import { T, expect, test } from '../acceptance/harness/acceptanceTest';

/** 5-2 망가뜨려 보기(2026-11-13)에서 화면 테스트가 못 잡은 것 — 사장 지시로 추가. 승인 목록과 따로 */
test.describe('5-2 고장 내기 빈틈', () => {
  test('W2 노트가 없으면 [조합 요약 보기]를 누를 수 없고, 하나 고르면 누를 수 있다', async ({ app }) => {
    await app.launchApp();
    await app.startNewBlend('citrus');
    await expect(app.node(T.TO_SUMMARY)).toHaveAttribute('aria-disabled', 'true');
    await app.pickNote('top', 'lemon');
    await expect(app.node(T.TO_SUMMARY)).not.toHaveAttribute('aria-disabled', 'true');
  });

  test('W4 고른 노트 칩에 강도 글자(약·중·강)가 화면에 보인다(화면 읽기 설명이 아니라 보이는 글자)', async ({ app }) => {
    await app.launchApp();
    await app.startNewBlend('citrus');
    await app.pickNote('top', 'lemon', 'strong');
    expect(await app.node(T.selectedNote('top', 'lemon')).innerText()).toContain('강');
  });

  test('W8 계열을 다시 누르면 선택이 빠진다', async ({ app }) => {
    await app.launchApp();
    await app.openTab(T.TAB_HOME);
    await app.click(T.NEW_BLEND);
    await app.click(T.family('citrus'));
    await app.click(T.family('citrus'));
    await app.click(T.NEXT);
    await app.waitForTag(T.NOTE_LIST);
    await expect(app.page.getByText('계열: 고르지 않음').filter({ visible: true })).toBeVisible();
  });

  test('W13 [새 조합 만들기]를 빠르게 두 번 눌러도 화면은 하나만 쌓인다(뒤로 한 번이면 홈)', async ({ app }) => {
    await app.launchApp();
    await app.waitForTag(T.NEW_BLEND);
    await app.node(T.NEW_BLEND).dblclick();
    await app.waitForTag(T.NEXT);
    await app.click('header:back');
    await app.waitForTag(T.NEW_BLEND);
    expect(await app.exists(T.NEXT), '계열 화면이 하나 더 쌓여 있음').toBe(false);
  });

  test('W14 다른 층에 이미 고른 노트를 누르면 "이미 다른 층에 고른 노트예요" 안내', async ({ app }) => {
    await app.launchApp();
    await app.startNewBlend('citrus');
    await app.pickNote('top', 'lemon');
    await app.click(T.layerTab('middle'));
    await app.scrollToFind(T.NOTE_LIST, T.noteItem('lemon'));
    await app.click(T.noteItem('lemon'));
    await expect(app.node('notes:notice')).toContainText('이미 다른 층에 고른 노트예요: 탑');
  });
});
