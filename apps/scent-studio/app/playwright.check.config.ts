import base from './playwright.config';

/**
 * tests/run.sh용: 승인(acceptance)과 회귀(regression)를 웹 빌드 한 번으로 같이 돌린다.
 * 판정은 tests/check-playwright.mjs(승인은 tests/acceptance/pending.txt에 있는 것만 실패 허용).
 */
export default {
  ...base,
  testDir: './tests',
  reporter: [['json', { outputFile: 'test-results/check.json' }]],
  projects: [
    { name: 'acceptance', testDir: './tests/acceptance' },
    { name: 'regression', testDir: './tests/regression' },
  ],
};
