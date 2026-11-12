import { SIZES, T, expect, test } from '../acceptance/harness/acceptanceTest';

/** 5-2 폰 확인 3차(2026-11-12): 조합을 만들고 홈으로 와도 "만들던 조합 이어 하기"가 안 보임 */
test.describe('5-2 폰 확인 3차 회귀', () => {
  test('버그 1: 노트를 고르고 홈 탭으로 오면 "만들던 조합 이어 하기"가 보인다', async ({ app }) => {
    await app.launchAppWith(SIZES.smallPhone);
    await app.startNewBlend('sweet');
    await app.pickNote('base', 'vanilla');
    await app.openTab(T.TAB_HOME);
    await expect(app.node(T.RESUME_BLEND)).toBeVisible();
  });

  test('버그 1: 요약까지 가서 홈 탭으로 와도 보이고, 누르면 고른 노트 그대로', async ({ app }) => {
    await app.launchAppWith(SIZES.smallPhone);
    await app.buildA3Blend();
    await app.goToSummary();
    await app.openTab(T.TAB_HOME);
    await expect(app.node(T.RESUME_BLEND)).toBeVisible();
    await app.click(T.RESUME_BLEND);
    await app.waitForTag(T.NOTE_LIST);
    expect(await app.textOf(T.selectedNote('base', 'sandalwood'))).toContain('약');
  });

  test('버그 1: 다른 탭(마이)에 갔다가 홈 탭으로 와도 보인다', async ({ app }) => {
    await app.launchAppWith(SIZES.smallPhone);
    await app.buildA3Blend();
    await app.openTab(T.TAB_MY);
    await app.openTab(T.TAB_HOME);
    await expect(app.node(T.RESUME_BLEND)).toBeVisible();
  });

  test('버그 1: 노트 고르기에서 [뒤로]를 두 번 눌러 홈으로 와도 보인다', async ({ app }) => {
    await app.launchAppWith(SIZES.smallPhone);
    await app.startNewBlend('sweet');
    await app.pickNote('base', 'vanilla');
    await app.click('header:back');
    await app.waitForTag(T.family('sweet'));
    await app.page.waitForTimeout(700); // 연속 클릭 잠금(600ms, src/ui/tapLock.ts)이 풀린 뒤 두 번째 [뒤로]
    await app.click('header:back');
    await expect(app.node(T.RESUME_BLEND)).toBeVisible();
  });
});
