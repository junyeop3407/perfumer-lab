import { readFileSync } from 'node:fs';
import { gunzipSync } from 'node:zlib';

import { T, TEST_CATALOG_PATH, expect, test } from '../acceptance/harness/acceptanceTest';

/** 5-3 리뷰(QA)에서 나온 문제의 회귀 테스트 — 승인 목록과 따로 */
test.describe('5-3 리뷰 회귀', () => {
  test('망가뜨려 보기: /search/T001을 주소로 바로 열어도(새로 고침) 카탈로그를 사이트 맨 위에서 읽고 상세가 뜬다', async ({ app, context }) => {
    // 상대 주소 'catalog.json.gz'였을 때는 /search/catalog.json.gz를 찾아 "향수 데이터를 읽지 못했어요"가 떴다.
    // 하네스는 이제 /catalog.json.gz에만 답하지만(2026-11-13), 실제로 요청한 경로도 따로 확인한다.
    const asked: string[] = [];
    context.on('request', (r) => {
      if (r.url().endsWith('catalog.json.gz')) asked.push(new URL(r.url()).pathname);
    });
    app.page = await context.newPage();
    await app.page.goto('/search/T001');
    await app.waitForTag(T.DETAIL_SCREEN, 15_000);
    expect(asked.length, '카탈로그 요청 없음').toBeGreaterThan(0);
    for (const p of asked) expect(p, '카탈로그는 사이트 맨 위 경로에서').toBe('/catalog.json.gz');
  });

  test('망가뜨려 보기: /perfume 을 key 없이 열면 빈 화면이 아니라 "찾지 못했어요"', async ({ app }) => {
    await app.launchApp();
    await app.page.goto('/perfume');
    await expect(app.node(T.EMPTY_STATE)).toContainText('이 향수를 찾지 못했어요');
  });

  test('겹친 노트가 없는 추천: 카드에는 그 줄이 없고(공통 어코드는 그대로), 상세에 "겹친 노트는 없지만…" 한 줄 (2026-11-13 사장)', async ({ app }) => {
    // 실제 카탈로그가 아니라 가짜 카탈로그(tests/fixtures)만 쓴다. 순위에 기대지 않도록, 카드마다 그 향수가
    // 내 조합(탑 시더우드)과 실제로 같은 노트가 있는지를 가짜 카탈로그에서 직접 계산해 줄이 있어야/없어야 하는지 맞춰 본다.
    const fixture = JSON.parse(gunzipSync(readFileSync(TEST_CATALOG_PATH)).toString('utf-8')) as {
      perfumes: { key: string; brand: string; name: string; top: string[]; middle: string[]; base: string[] }[];
    };
    const blendNotes = ['cedarwood'];
    const sharesNote = (brand: string, name: string) => {
      const p = fixture.perfumes.find((x) => x.brand === brand && x.name === name);
      if (!p) throw new Error(`가짜 카탈로그에 없는 향수: ${brand} ${name}`);
      return [...p.top, ...p.middle, ...p.base].some((k) => blendNotes.includes(k));
    };

    await app.launchApp();
    await app.startNewBlend('woody');
    await app.pickNote('top', 'cedarwood');
    await app.goToSummary();
    await app.showRecommendations();
    const noOverlapRanks: number[] = [];
    for (let rank = 1; rank <= 10; rank++) {
      expect(await app.scrollToFind(T.REC_LIST, T.rec(rank)), `${rank}위 카드`).toBe(true);
      const shares = sharesNote(await app.textOf(T.recBrand(rank)), await app.textOf(T.recName(rank)));
      expect(await app.exists(T.recMatchedNotes(rank)), `${rank}위: 같은 노트 ${shares ? '있음 → 줄 보임' : '없음 → 줄 숨김'}`).toBe(shares);
      expect((await app.textOf(T.recSharedAccords(rank))).trim().length, `${rank}위 공통 어코드는 그대로`).toBeGreaterThan(0);
      if (!shares) {
        await expect(app.node(T.rec(rank))).not.toContainText('겹친 노트');
        noOverlapRanks.push(rank);
      }
    }
    // 이 테스트가 헛돌지 않게: 겹친 노트 없는 추천이 적어도 하나 있어야 한다(가짜 카탈로그가 바뀌면 여기서 알려 줌)
    expect(noOverlapRanks.length, '겹친 노트 없는 추천이 하나도 없음 — 가짜 카탈로그·조합을 바꿔야 함').toBeGreaterThan(0);

    await app.click(T.rec(noOverlapRanks[0]));
    await app.waitForTag(T.DETAIL_SCREEN);
    await expect(app.node(T.MATCHED_NOTES)).toContainText('겹친 노트는 없지만 어코드 구성이 비슷해요');
  });

  test('등급 설명 ⓘ: 목록 헤더에 하나, 누르면 사장 확정 문구, [닫기]로 닫힌다', async ({ app }) => {
    await app.launchApp();
    await app.buildA3Blend();
    await app.goToSummary();
    await app.showRecommendations();
    expect(await app.page.getByTestId(T.GRADE_INFO).filter({ visible: true }).count(), 'ⓘ는 하나만').toBe(1);
    const box = (await app.node(T.GRADE_INFO).boundingBox())!;
    expect(Math.min(box.width, box.height), 'ⓘ 누르는 영역').toBeGreaterThanOrEqual(44);

    await app.click(T.GRADE_INFO);
    const sheet = app.node(T.GRADE_INFO_SHEET);
    await expect(sheet).toContainText('등급은 이렇게 나눠요');
    await expect(sheet).toContainText('약 76% 이상');
    await expect(sheet).toContainText('약 65% 이상');
    await expect(sheet).toContainText('그보다 낮아요');
    await sheet.getByRole('button', { name: '닫기' }).click();
    await expect(app.page.getByTestId(T.GRADE_INFO_SHEET).filter({ visible: true })).toHaveCount(0);
  });
});
