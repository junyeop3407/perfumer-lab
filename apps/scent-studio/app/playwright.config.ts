import { defineConfig } from '@playwright/test';

/**
 * 승인 목록(docs/tests.md) 테스트. 앱을 웹으로 내보내(dist-test) 실제 크롬에서 사람처럼 누른다.
 * 기본 화면은 큰 폰(412×915). 작은 폰(360)·PC(1280)·다크·글자 200%는 테스트 안에서 바꾼다(H1·H2·A1·A7·H5).
 */
const PORT = 4317;

export default defineConfig({
  testDir: './tests/acceptance',
  timeout: 60_000,
  expect: { timeout: 5_000 },
  fullyParallel: true,
  reporter: [['list'], ['html', { open: 'never', outputFolder: 'test-results/report' }]],
  outputDir: 'test-results/artifacts',
  use: {
    baseURL: `http://localhost:${PORT}`,
    browserName: 'chromium',
    viewport: { width: 412, height: 915 },
    locale: 'ko-KR',
    colorScheme: 'light',
    trace: 'retain-on-failure',
  },
  webServer: {
    command: `node tests/acceptance/build-web.mjs && npx serve -s dist-test -l ${PORT} --no-clipboard`,
    url: `http://localhost:${PORT}`,
    reuseExistingServer: !process.env.CI,
    timeout: 300_000,
  },
});
