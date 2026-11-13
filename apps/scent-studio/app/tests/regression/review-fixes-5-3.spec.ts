import { T, expect, test } from '../acceptance/harness/acceptanceTest';

/** 5-3 리뷰(QA)에서 나온 문제의 회귀 테스트 — 승인 목록과 따로 */
test.describe('5-3 리뷰 회귀', () => {
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
