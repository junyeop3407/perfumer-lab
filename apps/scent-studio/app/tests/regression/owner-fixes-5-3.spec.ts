import { T, expect, test } from '../acceptance/harness/acceptanceTest';

/** 2026-11-13 사장 지시(5-3 폰 확인 뒤) 회귀 테스트 — 승인 목록과 따로 */
test.describe('사장 지시 회귀(5-3 폰 확인 뒤)', () => {
  test('강도 유지: 약을 누르고 다음 노트를 고르면 약으로 시작, 앞 노트도 약 그대로. 새 조합에서는 다시 중', async ({ app }) => {
    await app.launchApp();
    await app.startNewBlend('citrus');
    await app.pickNote('top', 'lemon', 'weak');
    await app.pickNote('top', 'bergamot'); // 강도를 누르지 않음
    expect(await app.textOf(T.selectedNote('top', 'bergamot')), '새 노트는 마지막에 고른 강도(약)').toContain('약');
    expect(await app.textOf(T.selectedNote('top', 'lemon')), '앞 노트는 그대로 약').toContain('약');
    // 강도 고르기에서도 "약"이 골라져 있다(고른 칸은 primary 바탕). 웹에 aria-checked가 안 나가는 것은 5-8 접근성에서(NOTES.md)
    await expect(app.node(T.intensity('bergamot', 'weak'))).toHaveClass(/(^|\s)bg-primary(\s|$)/);
    await expect(app.node(T.intensity('bergamot', 'medium'))).not.toHaveClass(/(^|\s)bg-primary(\s|$)/);

    // 층을 바꿔도 유지
    await app.pickNote('middle', 'rose');
    expect(await app.textOf(T.selectedNote('middle', 'rose'))).toContain('약');

    // 요약에 갔다 와도 강도는 그대로
    await app.goToSummary();
    await app.click(T.EDIT_NOTES);
    await app.waitForTag(T.NOTE_LIST);
    expect(await app.textOf(T.selectedNote('top', 'lemon'))).toContain('약');

    // 새 조합을 시작하면 다시 중
    await app.startNewBlend('citrus');
    await app.pickNote('top', 'grapefruit');
    expect(await app.textOf(T.selectedNote('top', 'grapefruit')), '새 조합은 중부터').toContain('중');
  });
});
