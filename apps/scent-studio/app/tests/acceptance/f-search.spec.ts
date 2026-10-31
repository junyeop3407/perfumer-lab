import { T, expect, test } from './harness/acceptanceTest';

/** 승인 목록 F. 향수 찾기 (docs/tests.md) */
test.describe('F. 향수 찾기', () => {
  test('F1 이름이나 브랜드로 검색해 상세를 연다', async ({ app }) => {
    await app.launchApp();
    await app.openTab(T.TAB_SEARCH);
    await app.type(T.PERFUME_SEARCH, 'Lumière');
    await app.waitForTag(T.searchResult('T001'));

    await app.type(T.PERFUME_SEARCH, 'Noirwood');
    for (const key of ['T002', 'T022', 'T023', 'T036']) await app.waitForTag(T.searchResult(key));

    await app.type(T.PERFUME_SEARCH, 'Lumière');
    await app.click(T.searchResult('T001'));
    await app.waitForTag(T.DETAIL_SCREEN);
  });

  test('F2 조합 없이 연 상세는 비교 막대 없이 어코드와 피라미드만', async ({ app }) => {
    await app.launchApp();
    await app.openPerfumeBySearch('Lumière', 'T001');
    expect(await app.nodesWithTagPrefix(T.COMPARE_BAR_PREFIX).count(), '비교 막대가 있으면 안 됨').toBe(0);
    expect(await app.nodesWithTagPrefix(T.PERFUME_ACCORD_PREFIX).count(), '향수 어코드가 없음').toBeGreaterThan(0);
    for (const layer of ['top', 'middle', 'base'] as const) {
      expect(await app.exists(T.pyramid(layer)), `피라미드 ${layer}`).toBe(true);
    }
  });
});
