import { T, expect, hasClickAction, test } from './harness/acceptanceTest';

/** 승인 목록 G. 계정 (docs/tests.md) */
test.describe('G. 계정', () => {
  test('G1 계정 삭제를 누르면 되돌릴 수 없다는 확인 창', async ({ app, auth }) => {
    await auth.signInDirectly();
    await app.launchApp();
    await app.openTab(T.TAB_MY);
    await app.click(T.DELETE_ACCOUNT);
    const dialog = await app.textOf(T.DELETE_DIALOG);
    expect(dialog).toContain('저장한 조합·찜·시향 기록이 모두 지워지고');
    expect(dialog).toContain('되돌릴 수 없어요');
  });

  test('G2 삭제하면 로그아웃되고 보관함이 빈다', async ({ app, auth, server }) => {
    server.blends.push({
      id: 'b-1', name: '지워질 조합', families: ['citrus'],
      notes: [{ noteKey: 'lemon', layer: 'top', intensity: 1.0 }],
    });
    await auth.signInDirectly();
    await app.launchApp();
    await app.openTab(T.TAB_MY);
    await app.click(T.DELETE_ACCOUNT);
    await app.click(T.DELETE_CONFIRM);

    await app.waitUntil(() => server.accountDeleted);
    await app.waitUntil(async () => (await auth.session()) === null);
    await app.waitForTag(T.MY_LOGIN);

    await app.openTab(T.TAB_LIBRARY);
    await app.waitForTag(T.EMPTY_STATE);
    expect(await app.exists(T.libBlend('지워질 조합'))).toBe(false);
  });

  test('G3 개인정보처리방침과 웹 계정 삭제 안내 링크', async ({ app }) => {
    await app.launchApp();
    await app.openTab(T.TAB_MY);
    expect(await app.textOf(T.PRIVACY_LINK)).toContain('개인정보처리방침');
    expect(await hasClickAction(app.node(T.PRIVACY_LINK)), '개인정보처리방침을 누를 수 없음').toBe(true);
    expect(await app.textOf(T.DELETION_GUIDE_LINK)).toContain('계정 삭제');
    expect(await hasClickAction(app.node(T.DELETION_GUIDE_LINK)), '계정 삭제 안내를 누를 수 없음').toBe(true);
  });
});
