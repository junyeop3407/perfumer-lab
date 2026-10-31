import { T, expect, test } from './harness/acceptanceTest';

/** 승인 목록 D. 저장 · 찜 · 시향 피드백 (docs/tests.md) */
test.describe('D. 저장 · 찜 · 시향 피드백', () => {
  test('D1 로그인 안 하고 저장이나 찜을 누르면 로그인 화면', async ({ app }) => {
    await app.launchApp();
    await app.buildA3Blend();
    await app.goToSummary();
    await app.click(T.SAVE_BLEND);
    await app.waitForTag(T.LOGIN_SCREEN);

    await app.launchApp();
    await app.openPerfumeBySearch('Lumière', 'T001');
    await app.click(T.FAVORITE_TOGGLE);
    await app.waitForTag(T.LOGIN_SCREEN);
  });

  test('D2 조합에 이름을 붙여 저장하면 보관함에 보인다', async ({ app, auth, server }) => {
    await auth.signInDirectly();
    await app.launchApp();
    await app.buildA3Blend();
    await app.goToSummary();
    await app.click(T.SAVE_BLEND);
    await app.type(T.BLEND_NAME_FIELD, '봄 산책');
    await app.click(T.CONFIRM_SAVE);
    await app.waitUntil(() => server.blends.length > 0);

    await app.openTab(T.TAB_LIBRARY);
    await app.click(T.LIB_BLENDS);
    await app.waitForTag(T.libBlend('봄 산책'));
  });

  test('D3 찜하면 보관함에 생기고 풀면 사라진다', async ({ app, auth, server }) => {
    await auth.signInDirectly();
    await app.launchApp();
    await app.openPerfumeBySearch('Lumière', 'T001');
    await app.click(T.FAVORITE_TOGGLE);
    await app.waitUntil(() => server.favorites.includes('T001'));
    await app.openTab(T.TAB_LIBRARY);
    await app.click(T.LIB_FAVORITES);
    await app.waitForTag(T.libFavorite('T001'));

    await app.openPerfumeBySearch('Lumière', 'T001');
    await app.click(T.FAVORITE_TOGGLE);
    await app.waitUntil(() => !server.favorites.includes('T001'));
    await app.openTab(T.TAB_LIBRARY);
    await app.click(T.LIB_FAVORITES);
    await app.waitUntil(async () => !(await app.exists(T.libFavorite('T001'))));
  });

  test('D4 시향 피드백이 보관함 시향 기록에 보인다', async ({ app, auth, server }) => {
    await auth.signInDirectly();
    await app.launchApp();
    await app.openPerfumeBySearch('Lumière', 'T001');
    await app.click(T.WRITE_FEEDBACK);
    await app.click(T.satisfaction(4));
    await app.click(T.similarity(3));
    await app.type(T.FEEDBACK_MEMO, '첫 향이 상큼했어요');
    await app.click(T.FEEDBACK_SUBMIT);
    await app.waitUntil(() => server.feedback.length > 0);

    await app.openTab(T.TAB_LIBRARY);
    await app.click(T.LIB_FEEDBACK);
    const item = await app.textOf(T.libFeedback('T001'));
    expect(item, '메모 없음').toContain('첫 향이 상큼했어요');
    expect(item, '만족도 4 없음').toContain('4');
    expect(item, '비슷했나 3 없음').toContain('3');
  });

  test('D5 저장한 조합을 누르면 요약이 열리고 추천을 다시 본다', async ({ app, auth, server }) => {
    server.blends.push({
      id: 'b-1', name: '저장된 조합', families: ['citrus', 'woody'],
      notes: [
        { noteKey: 'lemon', layer: 'top', intensity: 1.5 },
        { noteKey: 'rose', layer: 'middle', intensity: 1.0 },
        { noteKey: 'sandalwood', layer: 'base', intensity: 0.5 },
      ],
    });
    await auth.signInDirectly();
    await app.launchApp();
    await app.openTab(T.TAB_LIBRARY);
    await app.click(T.LIB_BLENDS);
    await app.click(T.libBlend('저장된 조합'));
    await app.waitForTag(T.SUMMARY_SCREEN);
    expect(await app.exists(T.selectedNote('top', 'lemon'))).toBe(true);
    await app.showRecommendations();
    expect((await app.recommendations()).length).toBeGreaterThan(0);
  });

  test('D6 로그인하면 기기의 불호와 서버의 불호가 합쳐진다', async ({ app, server }) => {
    server.dislikedNoteKeys = ['oakmoss'];
    await app.launchApp();
    await app.addDislikedNote('패출', 'patchouli');
    await app.click(T.MY_LOGIN);
    await app.click(T.LOGIN_GOOGLE);
    await app.openTab(T.TAB_MY);
    await app.waitForTag(T.dislikedChip('patchouli'));
    await app.waitForTag(T.dislikedChip('oakmoss'));
  });
});
