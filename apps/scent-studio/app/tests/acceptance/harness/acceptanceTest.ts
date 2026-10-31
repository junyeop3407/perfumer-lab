import { test as base, expect, type BrowserContext, type Locator, type Page } from '@playwright/test';
import { readFileSync } from 'node:fs';
import path from 'node:path';

import { FAKE_AUTH_STORAGE_KEY } from '../../../src/auth/fakeAuthKey';
import { TestIds as T, type IntensityLevel, type Layer } from '../../../src/ui/testIds';
import { FakeUserServer, TEST_TOKEN, TEST_USER_ID } from './fakeUserServer';

/**
 * 승인 목록(docs/tests.md) 테스트의 공통 준비. (예전 Kotlin 테스트의 AcceptanceTest.kt를 옮김)
 * 가짜 향수 데이터(tests/fixtures/test_catalog.json.gz), 가짜 서버(FakeUserServer),
 * 가짜 로그인(FakeAuth)으로 앱을 켠다. 기본 화면은 큰 폰(412×915) 라이트 모드.
 */
const TEST_CATALOG = path.resolve(__dirname, '../../../../tests/fixtures/test_catalog.json.gz');

export const SIZES = {
  smallPhone: { width: 360, height: 640 },
  largePhone: { width: 412, height: 915 },
  pc: { width: 1280, height: 800 },
  /** 글자 200%: 작은 폰(360×640)을 브라우저 확대 200%로 본 것과 같은 크기 (NOTES.md) */
  smallPhoneFont200: { width: 180, height: 320 },
} as const;

export type Rec = {
  rank: number;
  name: string;
  brand: string;
  scoreText: string;
  grade: string;
  sharedAccords: string;
  matchedNotes: string;
  score: number;
};

/** 가짜 데이터에서 패출리가 어느 층에든 든 향수 (tests/fixtures/synthetic_parfumo.csv) */
export const PATCHOULI_PERFUMES = new Set([
  'Santal Profond', 'Rose Patchouli', 'Citron Terre', 'Jardin Sombre',
  'Cuir Fumé', "Épices d'Or", 'Mousse de Chêne',
]);

/** 가짜 데이터에서 어코드 1위가 플로럴인 향수 */
export const FLORAL_FIRST_PERFUMES = new Set([
  'Verte No.2', 'Verte No.5', 'Rose Patchouli', 'Pivoine Blanche',
  "Rose d'Été", 'Fleur de Thé', 'Osmanthe Doré', 'Jasmin Nuit',
]);

export const GRADES = new Set(['매우 비슷', '비슷', '참고']);

/** 구글 로그인 흉내. 앱(authMode 'fake')이 이 저장소 열쇠로 로그인 상태를 읽고 쓴다. */
export class FakeAuth {
  constructor(private readonly app: ScentApp) {}

  /** 테스트 준비용: 화면을 거치지 않고 로그인된 상태로 시작 (이미 켜져 있으면 다시 켠다) */
  async signInDirectly() {
    await this.app.seedStorage(FAKE_AUTH_STORAGE_KEY, JSON.stringify({ userId: TEST_USER_ID, accessToken: TEST_TOKEN }));
  }

  /** 지금 로그인된 사용자 (없으면 null) */
  async session(): Promise<{ userId: string; accessToken: string } | null> {
    const raw = await this.app.page.evaluate((k) => localStorage.getItem(k), FAKE_AUTH_STORAGE_KEY);
    return raw ? JSON.parse(raw) : null;
  }
}

export class ScentApp {
  page!: Page;
  private launched = false;
  private seeds: Record<string, string> = {};

  constructor(readonly context: BrowserContext) {}

  // ---------- 앱 켜기 ----------

  async launchApp() {
    if (!this.page) this.page = await this.context.newPage();
    await this.page.goto('/');
    if (Object.keys(this.seeds).length > 0) {
      await this.page.evaluate((s) => Object.entries(s).forEach(([k, v]) => localStorage.setItem(k, v)), this.seeds);
      this.seeds = {};
      await this.page.reload();
    }
    this.launched = true;
    await this.waitForTag(T.TAB_HOME);
  }

  /** 앱을 껐다 켠 것처럼 (웹: 탭을 닫고 다시 연다. 기기에 저장한 것만 남음) */
  async restartApp() {
    await this.page.close();
    this.page = await this.context.newPage();
    await this.launchApp();
  }

  /** 화면 크기·라이트/다크를 바꿔 다시 켠다 */
  async launchAppWith(size: { width: number; height: number }, colorScheme: 'light' | 'dark' = 'light') {
    if (!this.page) this.page = await this.context.newPage();
    await this.page.setViewportSize(size);
    await this.page.emulateMedia({ colorScheme });
    await this.launchApp();
  }

  /** 기기 저장소에 값을 넣는다. 앱이 켜져 있으면 넣고 다시 켠다 */
  async seedStorage(key: string, value: string) {
    if (!this.launched) {
      this.seeds[key] = value;
      return;
    }
    await this.page.evaluate(([k, v]) => localStorage.setItem(k, v), [key, value]);
    await this.page.reload();
    await this.waitForTag(T.TAB_HOME);
  }

  /** 인터넷을 완전히 끊는다 (이미 연 앱은 그대로, 새 요청은 모두 실패) */
  async goOffline() {
    await this.context.setOffline(true);
  }

  // ---------- 찾기 · 누르기 · 읽기 ----------

  /** 보이는 요소 중 이 이름표를 가진 첫 번째 (웹 화면 쌓임 때문에 숨은 이전 화면은 뺀다) */
  node(id: string): Locator {
    return this.page.getByTestId(id).filter({ visible: true }).first();
  }

  async exists(id: string): Promise<boolean> {
    return (await this.page.getByTestId(id).filter({ visible: true }).count()) > 0;
  }

  async waitForTag(id: string, timeout = 5_000) {
    await expect(this.node(id), `'${id}'가 화면에 없음`).toBeVisible({ timeout });
  }

  async waitUntil(condition: () => boolean | Promise<boolean>, timeout = 5_000, message?: string) {
    await expect.poll(condition, { timeout, message }).toBe(true);
  }

  async click(id: string) {
    await this.waitForTag(id);
    const n = this.node(id);
    await n.scrollIntoViewIfNeeded();
    await n.click();
  }

  async type(id: string, text: string) {
    await this.waitForTag(id);
    await this.node(id).fill(text);
  }

  /** 요소와 그 안의 글자를 모두 이어 붙인 것 (보이는 글자 + 화면 읽기 설명 + 입력값) */
  async textOf(id: string): Promise<string> {
    await this.waitForTag(id);
    return allText(this.node(id));
  }

  nodesWithTagPrefix(prefix: string): Locator {
    return this.page.locator(`[data-testid^="${prefix}"]`).filter({ visible: true });
  }

  async textsWithTagPrefix(prefix: string): Promise<string[]> {
    const nodes = this.nodesWithTagPrefix(prefix);
    const out: string[] = [];
    for (let i = 0; i < (await nodes.count()); i++) out.push(await allText(nodes.nth(i)));
    return out;
  }

  /** 화면 전체 글자 */
  async screenText(): Promise<string> {
    return this.page.locator('body').innerText();
  }

  /**
   * 목록(listId)을 위에서부터 끝까지 내려가며 itemId를 찾는다. 찾으면 보이게 하고 true.
   * (긴 목록은 화면에 보이는 부분만 그려질 수 있어서 직접 스크롤한다)
   */
  async scrollToFind(listId: string, itemId: string): Promise<boolean> {
    await this.waitForTag(listId);
    const list = this.node(listId);
    await list.evaluate((el) => scrollerOf(el).scrollTo(0, 0), undefined).catch(() => undefined);
    for (let i = 0; i < 100; i++) {
      if (await this.exists(itemId)) {
        await this.node(itemId).scrollIntoViewIfNeeded();
        return true;
      }
      const moved = await list.evaluate((el) => {
        const s = scrollerOf(el);
        const before = s.scrollTop;
        s.scrollTop = before + Math.max(50, s.clientHeight * 0.8);
        return s.scrollTop !== before;
      });
      if (!moved) return this.exists(itemId);
      await this.page.waitForTimeout(50);
    }
    return false;
  }

  // ---------- 자주 하는 동작 ----------

  async openTab(tabId: string) {
    await this.click(tabId);
  }

  /** 홈 → 새 조합 → 계열 고르기 → 다음 */
  async startNewBlend(...familyCodes: string[]) {
    await this.openTab(T.TAB_HOME);
    await this.click(T.NEW_BLEND);
    for (const code of familyCodes) await this.click(T.family(code));
    await this.click(T.NEXT);
    await this.waitForTag(T.NOTE_LIST);
  }

  /** 층 탭을 고르고 목록에서 노트를 골라 강도를 정한다 */
  async pickNote(layer: Layer, noteKey: string, level?: IntensityLevel) {
    await this.click(T.layerTab(layer));
    await this.scrollToFind(T.NOTE_LIST, T.noteItem(noteKey));
    await this.click(T.noteItem(noteKey));
    await this.waitForTag(T.selectedNote(layer, noteKey));
    if (level) await this.click(T.intensity(noteKey, level));
  }

  /** 승인 목록 A3의 조합: 시트러스·우디 / 탑 레몬(강) · 미들 장미(중) · 베이스 샌달우드(약) */
  async buildA3Blend() {
    await this.startNewBlend('citrus', 'woody');
    await this.pickNote('top', 'lemon', 'strong');
    await this.pickNote('middle', 'rose', 'medium');
    await this.pickNote('base', 'sandalwood', 'weak');
  }

  /** 기획서의 맹점 조합: 탑 레몬(강) · 미들 오스만투스(중) · 베이스 샌달우드(약) */
  async buildBlindSpotBlend() {
    await this.startNewBlend('citrus', 'floral', 'woody');
    await this.pickNote('top', 'lemon', 'strong');
    await this.pickNote('middle', 'osmanthus', 'medium');
    await this.pickNote('base', 'sandalwood', 'weak');
  }

  async goToSummary() {
    await this.click(T.TO_SUMMARY);
    await this.waitForTag(T.SUMMARY_SCREEN);
  }

  async showRecommendations() {
    await this.click(T.SHOW_RECS);
    await this.waitForTag(T.REC_LIST);
  }

  /** 추천 목록을 위에서부터 끝까지 내려가며 읽는다 */
  async recommendations(): Promise<Rec[]> {
    const recs: Rec[] = [];
    for (let rank = 1; rank <= 20; rank++) {
      if (!(await this.scrollToFind(T.REC_LIST, T.rec(rank)))) break;
      const scoreText = await this.textOf(T.recScore(rank));
      const m = scoreText.match(/\d+/);
      if (!m) throw new Error(`점수 숫자 없음: ${scoreText}`);
      recs.push({
        rank,
        name: await this.textOf(T.recName(rank)),
        brand: await this.textOf(T.recBrand(rank)),
        scoreText,
        grade: (await this.textOf(T.recGrade(rank))).trim(),
        sharedAccords: await this.textOf(T.recSharedAccords(rank)),
        matchedNotes: await this.textOf(T.recMatchedNotes(rank)),
        score: Number(m[0]),
      });
    }
    return recs;
  }

  /** 마이에서 불호 노트를 검색해 더한다 */
  async addDislikedNote(searchText: string, noteKey: string) {
    await this.openTab(T.TAB_MY);
    await this.type(T.DISLIKE_SEARCH, searchText);
    await this.click(T.dislikeOption(noteKey));
    await this.waitForTag(T.dislikedChip(noteKey));
  }

  /** 향수 찾기에서 검색해 상세를 연다 */
  async openPerfumeBySearch(query: string, perfumeKey: string) {
    await this.openTab(T.TAB_SEARCH);
    await this.type(T.PERFUME_SEARCH, query);
    await this.click(T.searchResult(perfumeKey));
    await this.waitForTag(T.DETAIL_SCREEN);
  }

  // ---------- 보기 편하게(H) 검사 ----------

  /** 글자가 말줄임(…)이나 넘침으로 잘리지 않았는지, 화면이 옆으로 넘치지 않는지 */
  async assertNoClippedText(where: string) {
    const clipped = await this.page.evaluate(() => {
      const out: string[] = [];
      const doc = document.scrollingElement ?? document.documentElement;
      if (doc.scrollWidth > window.innerWidth + 1) out.push(`(화면이 옆으로 ${doc.scrollWidth - window.innerWidth}px 넘침)`);
      for (const el of Array.from(document.querySelectorAll<HTMLElement>('body *'))) {
        const hasText = Array.from(el.childNodes).some((c) => c.nodeType === Node.TEXT_NODE && c.textContent!.trim() !== '');
        if (!hasText || el.getClientRects().length === 0) continue;
        const cs = getComputedStyle(el);
        if (cs.visibility === 'hidden') continue;
        const clipsX = cs.overflowX !== 'visible' || cs.textOverflow === 'ellipsis';
        const clipsY = cs.overflowY !== 'visible' || cs.webkitLineClamp !== 'none';
        if ((clipsX && el.scrollWidth > el.clientWidth + 1) || (clipsY && el.scrollHeight > el.clientHeight + 1)) {
          out.push(el.innerText.slice(0, 60));
        }
      }
      return out;
    });
    expect(clipped, `${where}: 잘린 글자`).toEqual([]);
  }

  /** 누를 수 있는 것끼리 겹치지 않는지 (안에 든 것끼리는 제외) */
  async assertNoOverlappingTargets(where: string) {
    const overlaps = await this.page.evaluate((sel) => {
      const visibleRect = (el: Element) => {
        let r = el.getBoundingClientRect();
        let box = { l: r.left, t: r.top, r: r.right, b: r.bottom };
        for (let p = el.parentElement; p; p = p.parentElement) {
          const cs = getComputedStyle(p);
          if (cs.overflowX === 'visible' && cs.overflowY === 'visible') continue;
          r = p.getBoundingClientRect();
          box = { l: Math.max(box.l, r.left), t: Math.max(box.t, r.top), r: Math.min(box.r, r.right), b: Math.min(box.b, r.bottom) };
        }
        box = { l: Math.max(box.l, 0), t: Math.max(box.t, 0), r: Math.min(box.r, innerWidth), b: Math.min(box.b, innerHeight) };
        return box.r - box.l > 0 && box.b - box.t > 0 ? box : null;
      };
      const targets = Array.from(document.querySelectorAll(sel))
        .filter((el) => getComputedStyle(el).visibility !== 'hidden')
        .map((el) => ({ el, box: visibleRect(el) }))
        .filter((t) => t.box !== null) as { el: HTMLElement; box: { l: number; t: number; r: number; b: number } }[];
      const out: string[] = [];
      for (let i = 0; i < targets.length; i++)
        for (let j = i + 1; j < targets.length; j++) {
          const a = targets[i], b = targets[j];
          if (a.el.contains(b.el) || b.el.contains(a.el)) continue;
          const w = Math.min(a.box.r, b.box.r) - Math.max(a.box.l, b.box.l);
          const h = Math.min(a.box.b, b.box.b) - Math.max(a.box.t, b.box.t);
          if (w > 1 && h > 1) out.push(`'${a.el.innerText.slice(0, 30)}' / '${b.el.innerText.slice(0, 30)}'`);
        }
      return out;
    }, CLICKABLE_SELECTOR);
    expect(overlaps, `${where}: 겹친 버튼`).toEqual([]);
  }

  /** 누를 수 있는 것(버튼, 칩, 탭)은 모두 48px 이상 (안드로이드 48dp와 같은 크기) */
  async assertTouchTargetsAtLeast48(where: string) {
    const small = await this.page.evaluate((sel) => {
      return Array.from(document.querySelectorAll<HTMLElement>(sel))
        .filter((el) => el.getClientRects().length > 0 && getComputedStyle(el).visibility !== 'hidden')
        .map((el) => ({ el, r: el.getBoundingClientRect() }))
        .filter(({ r }) => r.width > 0 && r.height > 0 && (r.width < 47.5 || r.height < 47.5))
        .map(({ el, r }) => `'${(el.innerText || el.getAttribute('aria-label') || el.dataset.testid || el.tagName).slice(0, 30)}' ${Math.round(r.width)}x${Math.round(r.height)}px`);
    }, CLICKABLE_SELECTOR);
    expect(small, `${where}: 48px보다 작은 누르는 영역`).toEqual([]);
  }

  async capture(name: string) {
    await this.page.screenshot({ path: `test-results/captures/${name}.png`, fullPage: true });
  }
}

/** 누를 수 있는 것: 버튼, 링크, 탭, 칩(체크·라디오), 입력창, 키보드로 누를 수 있는 요소 */
export const CLICKABLE_SELECTOR = [
  'button', 'a[href]', 'input:not([type=hidden])', 'textarea', 'select',
  '[role=button]', '[role=link]', '[role=tab]', '[role=checkbox]', '[role=radio]',
  '[role=switch]', '[role=menuitem]', '[role=option]', '[tabindex="0"]',
].join(',');

/** 요소와 그 안의 글자 + aria-label + 입력값을 이어 붙인다 */
export async function allText(node: Locator): Promise<string> {
  return node.evaluate((el) => {
    const parts: string[] = [];
    const visit = (e: Element) => {
      const label = e.getAttribute('aria-label');
      if (label) parts.push(label);
      if (e instanceof HTMLInputElement || e instanceof HTMLTextAreaElement) parts.push(e.value);
      for (const c of Array.from(e.childNodes)) {
        if (c.nodeType === Node.TEXT_NODE) parts.push(c.textContent ?? '');
        else if (c instanceof Element) visit(c);
      }
    };
    visit(el);
    return parts.map((p) => p.trim()).filter((p) => p !== '').join(' ');
  });
}

/** 누를 수 있는지 (버튼·링크 역할이거나 키보드로 누를 수 있음) */
export async function hasClickAction(node: Locator): Promise<boolean> {
  return node.evaluate((el) => {
    const role = el.getAttribute('role');
    return (
      el.tagName === 'A' || el.tagName === 'BUTTON' || role === 'link' || role === 'button' || el.getAttribute('tabindex') === '0'
    );
  });
}

type Fixtures = { app: ScentApp; server: FakeUserServer; auth: FakeAuth };

export const test = base.extend<Fixtures>({
  server: async ({}, use) => {
    await use(new FakeUserServer());
  },
  app: async ({ context, server }, use) => {
    await context.addInitScript(() => {
      // 브라우저 안에서 쓰는 도우미: 요소의 스크롤 상자 (자기 자신 → 안쪽 → 바깥쪽 → 화면 전체)
      const scrollable = (e: Element) => {
        const cs = getComputedStyle(e);
        return /(auto|scroll)/.test(cs.overflowY) && e.scrollHeight > e.clientHeight + 1;
      };
      (window as unknown as { scrollerOf: (el: Element) => Element }).scrollerOf = (el: Element) => {
        if (scrollable(el)) return el;
        const inner = Array.from(el.querySelectorAll('*')).find(scrollable);
        if (inner) return inner;
        for (let p = el.parentElement; p; p = p.parentElement) if (scrollable(p)) return p;
        return document.scrollingElement ?? document.documentElement;
      };
    });
    const catalog = readFileSync(TEST_CATALOG);
    await context.route('**/catalog.json.gz', (route) =>
      route.fulfill({ status: 200, contentType: 'application/gzip', body: catalog }),
    );
    await server.install(context);
    await use(new ScentApp(context));
  },
  auth: async ({ app }, use) => {
    await use(new FakeAuth(app));
  },
});

declare global {
  function scrollerOf(el: Element): Element;
}

export { expect, T };
