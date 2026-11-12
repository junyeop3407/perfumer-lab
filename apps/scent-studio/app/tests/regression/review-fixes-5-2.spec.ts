import { SIZES, T, expect, test } from '../acceptance/harness/acceptanceTest';

/** 5-2 사장 기기 확인(2026-11-12, 안드로이드 Expo Go)에서 나온 문제 */
test.describe('5-2 기기 확인 회귀', () => {
  test('버그 2: 향수 찾기·보관함·마이 탭이 빈 화면이 아니라 "곧 열려요" 안내', async ({ app }) => {
    await app.launchAppWith(SIZES.smallPhone);
    for (const tab of [T.TAB_SEARCH, T.TAB_LIBRARY, T.TAB_MY]) {
      await app.openTab(tab);
      await expect(app.node(T.EMPTY_STATE), tab).toContainText('곧 열려요');
    }
  });

  test('버그 4: 강도 판이 올라와도 노트 목록 끝까지 스크롤해서 볼 수 있다', async ({ app }) => {
    await app.launchAppWith(SIZES.smallPhone);
    await app.startNewBlend('citrus');
    await app.pickNote('top', 'lemon');
    // 목록 맨 끝 노트가 아래 고정 막대([조합 요약 보기])보다 위에 다 보이게 스크롤된다
    const items = app.page.getByTestId(T.NOTE_LIST).filter({ visible: true }).locator('[data-testid^="noteInfo:"]');
    const last = items.last();
    await last.scrollIntoViewIfNeeded();
    await app.page.mouse.wheel(0, 5000);
    await app.page.waitForTimeout(200);
    const lastBox = (await last.boundingBox())!;
    const footerTop = (await app.node(T.TO_SUMMARY).boundingBox())!.y;
    expect(lastBox.y + lastBox.height, '마지막 노트가 아래 막대에 가림').toBeLessThanOrEqual(footerTop - 8);
  });

  test('버그 5: 조합 요약 맨 아래와 탭 바 사이에 여백', async ({ app }) => {
    await app.launchAppWith(SIZES.smallPhone);
    await app.buildA3Blend();
    await app.goToSummary();
    await app.node(T.EDIT_NOTES).scrollIntoViewIfNeeded();
    await app.page.mouse.wheel(0, 5000);
    await app.page.waitForTimeout(200);
    const edit = (await app.node(T.EDIT_NOTES).boundingBox())!;
    const tabTop = (await app.node(T.TAB_HOME).boundingBox())!.y;
    expect(tabTop - (edit.y + edit.height), '마지막 버튼과 탭 바 사이').toBeGreaterThanOrEqual(24);
  });

  test('디자인 8: 고른 노트가 줄을 넘기면 다음 줄도 첫 칩과 같은 위치에서 시작', async ({ app }) => {
    await app.launchAppWith(SIZES.smallPhone);
    await app.startNewBlend('citrus');
    await app.pickNote('top', 'lemon', 'strong');
    await app.pickNote('top', 'bergamot', 'strong');
    await app.pickNote('top', 'grapefruit', 'strong');
    const xs: { x: number; y: number }[] = [];
    for (const k of ['lemon', 'bergamot', 'grapefruit']) xs.push((await app.node(T.selectedNote('top', k)).boundingBox())!);
    const wrapped = xs.filter((b) => b.y > xs[0].y + 4);
    expect(wrapped.length, '360 폭에서 세 칩이 한 줄에 다 들어가면 이 검사는 의미 없음').toBeGreaterThan(0);
    for (const b of wrapped) expect(Math.abs(b.x - xs[0].x), '줄바꿈한 칩이 라벨 아래로 감').toBeLessThanOrEqual(1);
  });
});
