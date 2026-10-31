import { SIZES, T, allText, expect, test } from './harness/acceptanceTest';

/** 승인 목록 A. 향 조합 만들기 (docs/tests.md) */
test.describe('A. 향 조합 만들기', () => {
  test('A1 앱을 켜면 탭 4개(폰: 아래, PC: 왼쪽 메뉴)와 새 조합 만들기', async ({ app }) => {
    const tabs: [string, string][] = [
      [T.TAB_HOME, '홈'], [T.TAB_SEARCH, '향수 찾기'], [T.TAB_LIBRARY, '보관함'], [T.TAB_MY, '마이'],
    ];
    for (const [size, where] of [[SIZES.largePhone, 'bottom'], [SIZES.pc, 'left']] as const) {
      await app.launchAppWith(size);
      const boxes = [];
      for (const [id, label] of tabs) {
        expect(await app.textOf(id), `탭 '${label}'`).toContain(label);
        boxes.push((await app.node(id).boundingBox())!);
      }
      if (where === 'bottom') {
        for (const b of boxes) expect(b.y, '폰 크기에서 탭이 화면 아래에 있어야 함').toBeGreaterThan(size.height * 0.75);
      } else {
        for (const b of boxes) expect(b.x + b.width, 'PC 크기에서 메뉴가 왼쪽에 있어야 함').toBeLessThan(size.width * 0.35);
        for (let i = 1; i < boxes.length; i++) expect(boxes[i].y, 'PC 메뉴는 위아래로 놓임').toBeGreaterThan(boxes[i - 1].y);
      }
      expect(await app.textOf(T.NEW_BLEND)).toContain('새 조합 만들기');
    }
  });

  test('A2 계열을 고르면 층 탭이 있고 고른 계열 노트가 먼저', async ({ app }) => {
    await app.launchApp();
    await app.startNewBlend('citrus', 'woody');
    for (const layer of ['top', 'middle', 'base'] as const) expect(await app.exists(T.layerTab(layer)), `층 탭 ${layer}`).toBe(true);
    const firstNote = app.page.getByTestId(T.NOTE_LIST).filter({ visible: true }).first()
      .locator('[data-testid^="note:"]').first();
    const text = await allText(firstNote);
    expect(text.includes('시트러스') || text.includes('우디'), `첫 노트가 시트러스·우디 계열이 아님: ${text}`).toBe(true);
  });

  test('A3 노트마다 고른 강도가 보인다', async ({ app }) => {
    await app.launchApp();
    await app.buildA3Blend();
    expect(await app.textOf(T.selectedNote('top', 'lemon'))).toContain('강');
    expect(await app.textOf(T.selectedNote('middle', 'rose'))).toContain('중');
    expect(await app.textOf(T.selectedNote('base', 'sandalwood'))).toContain('약');
  });

  test('A4 층마다 3개까지만', async ({ app }) => {
    await app.launchApp();
    await app.startNewBlend('citrus');
    await app.pickNote('top', 'lemon');
    await app.pickNote('top', 'bergamot');
    await app.pickNote('top', 'grapefruit');
    await app.scrollToFind(T.NOTE_LIST, T.noteItem('mandarin orange'));
    await app.click(T.noteItem('mandarin orange'));
    expect(await app.exists(T.selectedNote('top', 'mandarin orange')), '4번째 노트가 들어감').toBe(false);
    expect(await app.textOf(T.LAYER_LIMIT_MESSAGE)).toContain('층마다 3개까지 고를 수 있어요');
  });

  test('A5 한글과 영문으로 노트를 검색한다', async ({ app }) => {
    await app.launchApp();
    await app.startNewBlend('citrus');
    await app.type(T.NOTE_SEARCH, '베르가');
    expect(await app.textOf(T.noteItem('bergamot'))).toContain('베르가못');
    await app.type(T.NOTE_SEARCH, 'berg');
    expect(await app.textOf(T.noteItem('bergamot'))).toContain('베르가못');
  });

  test('A6 목록에 없는 드문 노트도 검색으로 고른다', async ({ app }) => {
    await app.launchApp();
    await app.startNewBlend('woody');
    await app.click(T.layerTab('middle'));
    expect(await app.scrollToFind(T.NOTE_LIST, T.noteItem('hinoki')), '히노키는 목록에 없어야 함(검색 전용)').toBe(false);
    await app.type(T.NOTE_SEARCH, '히노키');
    await app.click(T.noteItem('hinoki'));
    await app.waitForTag(T.selectedNote('middle', 'hinoki'));
  });

  test('A7 노트 카드에 설명·계열·궁합 노트 3개 (폰: 아래에서 올라옴, PC: 옆 패널)', async ({ app }) => {
    for (const size of [SIZES.largePhone, SIZES.pc]) {
      await app.launchAppWith(size);
      await app.startNewBlend('citrus');
      await app.click(T.noteInfo('lemon'));
      await app.waitForTag(T.NOTE_CARD);
      expect(await app.textOf(T.NOTE_CARD_DESCRIPTION)).toContain('밝고 산뜻하게 톡 쏘는 첫인상');
      expect(await app.textOf(T.NOTE_CARD_FAMILY)).toContain('시트러스');
      await expect(app.page.getByTestId(T.NOTE_CARD_PAIRING).filter({ visible: true })).toHaveCount(3);

      const card = (await app.node(T.NOTE_CARD).boundingBox())!;
      if (size === SIZES.pc) {
        const list = (await app.node(T.NOTE_LIST).boundingBox())!;
        expect(card.x, 'PC 크기에서 노트 카드는 목록 옆 패널').toBeGreaterThanOrEqual(list.x + list.width - 1);
      } else {
        expect(card.y + card.height, '폰 크기에서 노트 카드는 화면 아래에 붙음').toBeGreaterThan(size.height - 2);
      }
    }
  });

  test('A8 요약에 층별 노트와 합이 100인 어코드 막대', async ({ app }) => {
    await app.launchApp();
    await app.buildA3Blend();
    await app.goToSummary();
    expect(await app.exists(T.selectedNote('top', 'lemon'))).toBe(true);
    expect(await app.exists(T.selectedNote('middle', 'rose'))).toBe(true);
    expect(await app.exists(T.selectedNote('base', 'sandalwood'))).toBe(true);

    const bars = await app.textsWithTagPrefix(T.ACCORD_BAR_PREFIX);
    expect(bars.length, '어코드 막대가 없음').toBeGreaterThan(0);
    const percents = bars.map((bar) => {
      const m = bar.match(/(\d+)\s*%/);
      expect(m, `막대에 %가 글자로 없음: ${bar}`).not.toBeNull();
      return Number(m![1]);
    });
    expect(percents.reduce((a, b) => a + b, 0), '막대 % 합').toBe(100);
  });

  test('A9 베이스가 비면 안내', async ({ app }) => {
    await app.launchApp();
    await app.startNewBlend('citrus', 'floral');
    await app.pickNote('top', 'lemon');
    await app.pickNote('middle', 'rose');
    await app.goToSummary();
    expect(await app.textOf(T.BALANCE_HINT)).toContain('베이스가 비어 있어');
  });

  test('A10 노트 수정으로 돌아가도 고른 노트가 그대로', async ({ app }) => {
    await app.launchApp();
    await app.buildA3Blend();
    await app.goToSummary();
    await app.click(T.EDIT_NOTES);
    await app.waitForTag(T.NOTE_LIST);
    expect(await app.textOf(T.selectedNote('top', 'lemon'))).toContain('강');
    expect(await app.textOf(T.selectedNote('middle', 'rose'))).toContain('중');
    expect(await app.textOf(T.selectedNote('base', 'sandalwood'))).toContain('약');
  });
});
