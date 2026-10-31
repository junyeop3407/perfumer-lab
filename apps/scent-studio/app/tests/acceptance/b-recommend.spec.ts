import { GRADES, T, expect, test } from './harness/acceptanceTest';

/** 승인 목록 B. 추천 보기 (docs/tests.md) */
test.describe('B. 추천 보기', () => {
  test('B1 추천 10개가 점수 높은 순', async ({ app }) => {
    await app.launchApp();
    await app.buildA3Blend();
    await app.goToSummary();
    await app.showRecommendations();
    const recs = await app.recommendations();
    expect(recs).toHaveLength(10);
    for (let i = 1; i < recs.length; i++) {
      expect(recs[i - 1].score, `${recs[i - 1].rank}위(${recs[i - 1].score}) < ${recs[i].rank}위(${recs[i].score})`)
        .toBeGreaterThanOrEqual(recs[i].score);
    }
  });

  test('B2 향수마다 이름·점수·등급·공통 어코드·일치 노트', async ({ app }) => {
    await app.launchApp();
    await app.buildA3Blend();
    await app.goToSummary();
    await app.showRecommendations();
    const recs = await app.recommendations();
    expect(recs.length).toBeGreaterThan(0);
    for (const r of recs) {
      expect(r.name.trim().length, `${r.rank}위 이름`).toBeGreaterThan(0);
      expect(r.brand.trim().length, `${r.rank}위 브랜드`).toBeGreaterThan(0);
      expect(r.scoreText, `${r.rank}위 점수`).toContain('%');
      expect(GRADES.has(r.grade), `${r.rank}위 등급 '${r.grade}'`).toBe(true);
      expect(r.sharedAccords.trim().length, `${r.rank}위 공통 어코드`).toBeGreaterThan(0);
      expect(r.matchedNotes.trim().length, `${r.rank}위 일치 노트`).toBeGreaterThan(0);
    }
  });

  test('B3 한 브랜드는 최대 2개', async ({ app }) => {
    // 가짜 데이터의 Maison Verte는 이 조합과 비슷한 향수가 6개 있다
    await app.launchApp();
    await app.buildA3Blend();
    await app.goToSummary();
    await app.showRecommendations();
    const recs = await app.recommendations();
    expect(recs.length).toBeGreaterThan(0);
    const counts = new Map<string, number>();
    for (const r of recs) counts.set(r.brand, (counts.get(r.brand) ?? 0) + 1);
    for (const [brand, count] of counts) expect(count, `${brand} 가 ${count}개`).toBeLessThanOrEqual(2);
  });

  test('B4 맹점 조합에서 가벼운 향수가 무거운 향수보다 위', async ({ app }) => {
    await app.launchApp();
    await app.buildBlindSpotBlend();
    await app.goToSummary();
    await app.showRecommendations();
    const names = (await app.recommendations()).map((r) => r.name);
    expect(names[0], '가벼운 시트러스·플로럴 향수가 1위').toBe("Lumière d'Agrumes");
    expect(names[0], '무거운 우디 향수가 1위').not.toBe('Santal Profond');
  });

  test('B5 향수 상세에 피라미드·비교 막대·일치/차이 노트', async ({ app }) => {
    await app.launchApp();
    await app.buildBlindSpotBlend();
    await app.goToSummary();
    await app.showRecommendations();
    await app.click(T.rec(1));
    await app.waitForTag(T.DETAIL_SCREEN);
    for (const layer of ['top', 'middle', 'base'] as const) {
      expect(await app.exists(T.pyramid(layer)), `피라미드 ${layer}`).toBe(true);
    }
    const bars = await app.textsWithTagPrefix(T.COMPARE_BAR_PREFIX);
    expect(bars.length, '비교 막대 없음').toBeGreaterThan(0);
    for (const bar of bars) expect(bar, `비교 막대에 % 글자 없음: ${bar}`).toContain('%');
    expect(await app.exists(T.MATCHED_NOTES)).toBe(true);
    expect(await app.exists(T.ONLY_IN_PERFUME_NOTES)).toBe(true);
  });

  test('B6 데이터상 유사도와 출처가 보인다', async ({ app }) => {
    await app.launchApp();
    await app.buildA3Blend();
    await app.goToSummary();
    await app.showRecommendations();
    expect(await app.textOf(T.SIMILARITY_DISCLAIMER)).toContain('데이터상 유사도');
    expect(await app.textOf(T.DATA_SOURCE)).toContain('Parfumo');
    await app.click(T.rec(1));
    await app.waitForTag(T.DETAIL_SCREEN);
    const detail = app.node(T.DETAIL_SCREEN);
    await expect(detail.getByTestId(T.SIMILARITY_DISCLAIMER)).toContainText('데이터상 유사도');
    await expect(detail.getByTestId(T.DATA_SOURCE)).toContainText('Parfumo');
  });
});
