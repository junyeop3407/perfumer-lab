import { FLORAL_FIRST_PERFUMES, PATCHOULI_PERFUMES, T, expect, test } from './harness/acceptanceTest';

/** 승인 목록 C. 불호 노트와 취향, 로그인 없이도 (docs/tests.md) */
test.describe('C. 불호 노트와 취향', () => {
  test('C1 불호 노트가 든 향수는 추천에서 빠진다', async ({ app }) => {
    await app.launchApp();
    await app.addDislikedNote('패출', 'patchouli');
    await app.buildA3Blend();
    await app.goToSummary();
    await app.showRecommendations();
    const names = (await app.recommendations()).map((r) => r.name);
    expect(names.length).toBeGreaterThan(0);
    const leaked = names.filter((n) => PATCHOULI_PERFUMES.has(n));
    expect(leaked, '패출리가 든 향수가 추천됨').toEqual([]);
  });

  test('C2 불호 노트는 앱을 껐다 켜도(웹: 탭을 닫고 다시 열어도) 남는다', async ({ app }) => {
    await app.launchApp();
    await app.addDislikedNote('패출', 'patchouli');
    await app.restartApp();
    await app.openTab(T.TAB_MY);
    await app.waitForTag(T.dislikedChip('patchouli'));
  });

  test('C3 선호 계열을 정하면 그 계열이 강한 향수가 위로', async ({ app }) => {
    await app.launchApp();
    await app.buildA3Blend();
    await app.goToSummary();
    await app.showRecommendations();
    const before = (await app.recommendations()).map((r) => r.name);

    await app.openTab(T.TAB_MY);
    await app.click(T.preferredFamily('floral'));
    await app.buildA3Blend();
    await app.goToSummary();
    await app.showRecommendations();
    const after = (await app.recommendations()).map((r) => r.name);

    expect(after, '선호 계열을 정해도 순위가 그대로').not.toEqual(before);
    const rankOf = (list: string[], name: string) => (list.indexOf(name) < 0 ? 99 : list.indexOf(name));
    const rose = [...FLORAL_FIRST_PERFUMES].some((n) => rankOf(after, n) < rankOf(before, n));
    expect(rose, `플로럴이 1위 어코드인 향수 중 위로 온 것이 없음\n전: ${before}\n후: ${after}`).toBe(true);
  });
});
