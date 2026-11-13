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

test.describe('카드 구분(사장 4번)', () => {
  test('③ 겹친 노트를 "N/전체개"로, ① 이 향수에 더 많은 어코드를 "+ 이름"으로', async ({ app }) => {
    await app.launchApp();
    await app.buildA3Blend(); // 노트 3개
    await app.goToSummary();
    await app.showRecommendations();
    for (let rank = 1; rank <= 10; rank++) {
      await app.scrollToFind(T.REC_LIST, T.rec(rank));
      if (await app.exists(T.recMatchedNotes(rank))) {
        expect(await app.textOf(T.recMatchedNotes(rank)), `${rank}위`).toMatch(/^겹친 노트 [1-3]\/3개 · /);
      }
    }
    // A3 조합(시트러스 57%)에는 우디·플로럴 쪽이 더 많은 향수가 섞여 있어 "+"가 적어도 하나 보인다
    const extras = await app.textsWithTagPrefix('rec:');
    expect(extras.filter((t) => /^\+ \S/.test(t)).length, '"+ 어코드" 표시가 하나도 없음').toBeGreaterThan(0);
  });
});

test.describe('한글 브랜드(사장 2번)', () => {
  test('브랜드 옆 괄호 한글 · 한글 검색(띄어쓰기 없이) · 결과 없음 문구', async ({ app, context }) => {
    // 가짜 카탈로그의 T001 브랜드만 표에 있는 "Acqua di Parma"로 바꿔 보낸다(하네스 응답보다 나중에 건 것이 먼저 쓰인다)
    const { readFileSync } = await import('node:fs');
    const { gunzipSync, gzipSync } = await import('node:zlib');
    const { TEST_CATALOG_PATH } = await import('../acceptance/harness/acceptanceTest');
    const cat = JSON.parse(gunzipSync(readFileSync(TEST_CATALOG_PATH)).toString('utf-8'));
    for (const p of cat.perfumes) if (p.key === 'T001') p.brand = 'Acqua di Parma';
    const body = gzipSync(JSON.stringify(cat));
    await context.route((url) => url.pathname === '/catalog.json.gz', (route) => route.fulfill({ status: 200, contentType: 'application/gzip', body }));

    await app.launchApp();
    await app.openTab(T.TAB_SEARCH);
    // 결과 없음 문구 먼저(상세를 연 뒤 검색 화면으로 되돌아가는 단계를 두지 않는다 — 여러 테스트가 함께 돌 때 시간 초과가 났음)
    await app.type(T.PERFUME_SEARCH, '없는향수이름');
    await expect(app.node(T.EMPTY_STATE)).toContainText('영어 이름으로도 찾아보세요');

    await app.type(T.PERFUME_SEARCH, '아쿠아디파르마');
    await app.waitForTag(T.searchResult('T001'));
    await expect(app.node(T.searchResult('T001'))).toContainText('Acqua di Parma (아쿠아 디 파르마)');
    await app.click(T.searchResult('T001'));
    await app.waitForTag(T.DETAIL_SCREEN);
    await expect(app.node(T.DETAIL_SCREEN)).toContainText('Acqua di Parma (아쿠아 디 파르마)');
  });
});
