import { T, expect, test } from '../acceptance/harness/acceptanceTest';

/** 5-3 리뷰(QA)에서 나온 문제의 회귀 테스트 — 승인 목록과 따로 */
test.describe('5-3 리뷰 회귀', () => {
  test('망가뜨려 보기: /search/T001을 주소로 바로 열어도(새로 고침) 카탈로그를 사이트 맨 위에서 읽고 상세가 뜬다', async ({ app, context }) => {
    // 상대 주소 'catalog.json.gz'였을 때는 /search/catalog.json.gz를 찾아 "향수 데이터를 읽지 못했어요"가 떴다.
    // 테스트 준비(하네스)는 어느 경로의 catalog.json.gz든 응답하므로, 실제로 요청한 경로를 따로 확인한다.
    const asked: string[] = [];
    context.on('request', (r) => {
      if (r.url().endsWith('catalog.json.gz')) asked.push(new URL(r.url()).pathname);
    });
    app.page = await context.newPage();
    await app.page.goto('/search/T001');
    await app.waitForTag(T.DETAIL_SCREEN, 15_000);
    expect(asked.length, '카탈로그 요청 없음').toBeGreaterThan(0);
    for (const p of asked) expect(p, '카탈로그는 사이트 맨 위 경로에서').toBe('/catalog.json.gz');
  });

  test('망가뜨려 보기: /perfume 을 key 없이 열면 빈 화면이 아니라 "찾지 못했어요"', async ({ app }) => {
    await app.launchApp();
    await app.page.goto('/perfume');
    await expect(app.node(T.EMPTY_STATE)).toContainText('이 향수를 찾지 못했어요');
  });

  test('겹친 노트가 없는 추천: 카드에는 그 줄이 없고(공통 어코드는 그대로), 상세에 "겹친 노트는 없지만…" 한 줄 (2026-11-13 사장)', async ({ app }) => {
    // 가짜 데이터에서 탑 시더우드 하나만 넣으면 1위 향수에 시더우드가 없다(어코드만 비슷)
    await app.launchApp();
    await app.startNewBlend('woody');
    await app.pickNote('top', 'cedarwood');
    await app.goToSummary();
    await app.showRecommendations();
    await app.waitForTag(T.rec(1));
    expect(await app.exists(T.recMatchedNotes(1)), '겹친 노트 줄이 숨어야 함').toBe(false);
    expect((await app.textOf(T.recSharedAccords(1))).trim().length, '공통 어코드는 그대로').toBeGreaterThan(0);
    await expect(app.node(T.rec(1))).not.toContainText('겹친 노트');
    await app.click(T.rec(1));
    await app.waitForTag(T.DETAIL_SCREEN);
    await expect(app.node(T.MATCHED_NOTES)).toContainText('겹친 노트는 없지만 어코드 구성이 비슷해요');
  });

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
