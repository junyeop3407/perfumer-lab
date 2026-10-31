import { T, expect, test } from './harness/acceptanceTest';

/** 승인 목록 E. 끊기지 않게 (docs/tests.md) */
test.describe('E. 끊기지 않게', () => {
  test('E1 앱이 꺼졌다 켜져도(웹: 탭을 닫았다 열어도) 만들던 조합을 이어 한다', async ({ app }) => {
    await app.launchApp();
    await app.startNewBlend('citrus', 'floral');
    await app.pickNote('top', 'lemon', 'strong');
    await app.pickNote('middle', 'rose', 'medium');
    await app.restartApp();

    await app.click(T.RESUME_BLEND);
    await app.waitForTag(T.NOTE_LIST);
    expect(await app.textOf(T.selectedNote('top', 'lemon'))).toContain('강');
    expect(await app.textOf(T.selectedNote('middle', 'rose'))).toContain('중');
  });

  test('E2 인터넷이 없어도 조합·추천·상세·검색이 된다', async ({ app, server }) => {
    server.offline = true;
    await app.launchApp();
    await app.goOffline();
    await app.buildA3Blend();
    await app.goToSummary();
    await app.showRecommendations();
    expect(await app.recommendations()).toHaveLength(10);
    await app.click(T.rec(1));
    await app.waitForTag(T.DETAIL_SCREEN);

    await app.openTab(T.TAB_SEARCH);
    await app.type(T.PERFUME_SEARCH, 'Verte');
    await app.waitForTag(T.searchResult('T003'));
  });

  test('E3 인터넷이 없을 때 저장하면 안내와 다시 시도', async ({ app, auth, server }) => {
    await auth.signInDirectly();
    server.offline = true;
    await app.launchApp();
    await app.buildA3Blend();
    await app.goToSummary();
    await app.click(T.SAVE_BLEND);
    await app.type(T.BLEND_NAME_FIELD, '오프라인 조합');
    await app.click(T.CONFIRM_SAVE);

    await app.waitForTag(T.ERROR_MESSAGE);
    expect(await app.textOf(T.ERROR_MESSAGE)).toContain('인터넷 연결을 확인해 주세요');
    expect(await app.exists(T.ERROR_RETRY)).toBe(true);
    expect(await app.exists(T.SUMMARY_SCREEN), '앱이 요약 화면에 그대로 있어야 함').toBe(true);

    server.offline = false;
    await app.click(T.ERROR_RETRY);
    await app.waitUntil(() => server.blends.length > 0);
  });

  test('E4 서버가 늦으면 깨우는 중 안내 후 저장된다', async ({ app, auth, server }) => {
    await auth.signInDirectly();
    await app.launchApp();
    await app.buildA3Blend();
    await app.goToSummary();
    server.slowMillis = 4_500;
    await app.click(T.SAVE_BLEND);
    await app.type(T.BLEND_NAME_FIELD, '느린 서버 조합');
    await app.click(T.CONFIRM_SAVE);

    await app.waitForTag(T.SLOW_SERVER_HINT, 4_000);
    expect(await app.textOf(T.SLOW_SERVER_HINT)).toContain('서버를 깨우는 중이에요');
    await app.waitUntil(() => server.blends.length > 0, 10_000);

    server.slowMillis = 0;
    await app.openTab(T.TAB_LIBRARY);
    await app.click(T.LIB_BLENDS);
    await app.waitForTag(T.libBlend('느린 서버 조합'));
  });
});
