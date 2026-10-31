import { SIZES, T, expect, test, type FakeAuth, type ScentApp } from './harness/acceptanceTest';
import type { FakeUserServer } from './harness/fakeUserServer';

/** 승인 목록 H. 보기 편하게: 화면 크기 · 글자 크기 · 화면 읽기 (docs/tests.md) */

/** 저장된 조합·찜을 넣고 로그인된 상태로 만든다 (앱을 켜기 전에 부른다) */
async function seedLibrary(server: FakeUserServer, auth: FakeAuth) {
  if (server.blends.length === 0) {
    server.blends.push({
      id: 'b-1', name: '저장된 조합', families: ['citrus', 'woody'],
      notes: [
        { noteKey: 'lemon', layer: 'top', intensity: 1.5 },
        { noteKey: 'sandalwood', layer: 'base', intensity: 0.5 },
      ],
    });
    server.favorites.push('T001');
  }
  await auth.signInDirectly();
}

/** 홈 · 노트 선택 · 요약 · 추천 · 상세 · 보관함 · 마이를 차례로 열며 check를 부른다 */
async function visitMainScreens(app: ScentApp, check: (screen: string) => Promise<void>) {
  await app.openTab(T.TAB_HOME);
  await check('home');
  await app.buildA3Blend();
  await check('notes');
  await app.goToSummary();
  await check('summary');
  await app.showRecommendations();
  await check('recs');
  await app.click(T.rec(1));
  await app.waitForTag(T.DETAIL_SCREEN);
  await check('detail');
  await app.openTab(T.TAB_LIBRARY);
  await app.click(T.LIB_BLENDS);
  await app.waitForTag(T.libBlend('저장된 조합'));
  await check('library');
  await app.openTab(T.TAB_MY);
  await check('my');
}

test.describe('H. 보기 편하게', () => {
  test('H1 작은 폰·큰 폰·PC, 라이트·다크에서 잘리거나 겹치지 않는다', async ({ app, auth, server }) => {
    test.setTimeout(240_000);
    await seedLibrary(server, auth);
    const sizes = { small: SIZES.smallPhone, large: SIZES.largePhone, pc: SIZES.pc };
    for (const [sizeName, size] of Object.entries(sizes)) {
      for (const mode of ['light', 'dark'] as const) {
        const variant = `${sizeName}-${mode}`;
        await app.launchAppWith(size, mode);
        await visitMainScreens(app, async (screen) => {
          await app.assertNoClippedText(`${variant}/${screen}`);
          await app.assertNoOverlappingTargets(`${variant}/${screen}`);
          await app.capture(`H1_${variant}_${screen}`);
        });
      }
    }
  });

  test('H2 글자 200%에서도 잘리지 않고 주요 버튼을 누를 수 있다', async ({ app, auth, server }) => {
    // 웹에서 글자 200% = 브라우저 확대 200%: 작은 폰(360×640)이 180×320 크기로 보인다 (NOTES.md)
    // visitMainScreens 는 각 화면의 주요 버튼을 스크롤해서 눌러 다음 화면으로 간다
    await seedLibrary(server, auth);
    await app.launchAppWith(SIZES.smallPhoneFont200);
    await visitMainScreens(app, async (screen) => {
      await app.assertNoClippedText(`글자200%/${screen}`);
      await app.capture(`H2_font200_${screen}`);
    });
  });

  test('H3 화면 읽기가 어코드 막대를 이름과 퍼센트로 읽는다', async ({ app }) => {
    await app.launchApp();
    await app.buildA3Blend();
    await app.goToSummary();
    const bars = app.nodesWithTagPrefix(T.ACCORD_BAR_PREFIX);
    expect(await bars.count(), '어코드 막대 없음').toBeGreaterThan(0);
    for (let i = 0; i < (await bars.count()); i++) {
      const spoken = (await bars.nth(i).getAttribute('aria-label')) ?? '';
      expect(spoken, `화면 읽기 문구가 '이름 NN퍼센트'가 아님: '${spoken}'`).toMatch(/^\S.* \d+퍼센트$/);
    }
  });

  test('H4 누를 수 있는 것은 모두 48px(48dp) 이상', async ({ app, auth, server }) => {
    await seedLibrary(server, auth);
    await app.launchApp();
    await visitMainScreens(app, (screen) => app.assertTouchTargetsAtLeast48(screen));
  });

  test('H5 PC 크기에서 추천 목록의 향수를 누르면 목록 옆에 상세가 나란히', async ({ app }) => {
    await app.launchAppWith(SIZES.pc);
    await app.buildA3Blend();
    await app.goToSummary();
    await app.showRecommendations();
    await app.click(T.rec(1));
    await app.waitForTag(T.DETAIL_SCREEN);
    expect(await app.exists(T.REC_LIST), '상세를 열어도 추천 목록이 보여야 함').toBe(true);
    const list = (await app.node(T.REC_LIST).boundingBox())!;
    const detail = (await app.node(T.DETAIL_SCREEN).boundingBox())!;
    expect(detail.x, '상세가 목록 옆(오른쪽)에 있어야 함').toBeGreaterThanOrEqual(list.x + list.width - 1);
  });
});
