import { expect, test } from './harness/acceptanceTest';

/** 승인 목록 I. 사용 기록 (docs/tests.md, docs/design.md 13절) */
const FLOW = ['blend_started', 'blend_summary_viewed', 'recs_viewed'];

test.describe('I. 사용 기록', () => {
  test('I1 조합 시작·요약·추천 기록이 서버로 간다', async ({ app, server }) => {
    await app.launchApp();
    await app.buildA3Blend();
    await app.goToSummary();
    await app.showRecommendations();
    await app.waitUntil(() => FLOW.every((e) => server.receivedEvents.includes(e)));
  });

  test('I2 인터넷이 끊겼다가 연결되면 밀린 기록이 간다', async ({ app, server }) => {
    server.offline = true;
    await app.launchApp();
    await app.buildA3Blend();
    await app.goToSummary();
    await app.showRecommendations();
    expect(server.receivedEvents, '끊긴 동안 받은 기록이 있으면 안 됨').toEqual([]);

    server.offline = false;
    await app.waitUntil(() => FLOW.every((e) => server.receivedEvents.includes(e)));
  });
});
