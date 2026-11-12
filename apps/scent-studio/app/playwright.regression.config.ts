import base from './playwright.config';

/**
 * 리뷰·기기 확인에서 나온 문제의 화면 회귀 테스트 (승인 목록과 섞지 않음, .claude/rules/testing.md).
 * 실행: npx playwright test -c playwright.regression.config.ts
 */
export default { ...base, testDir: './tests/regression' };
