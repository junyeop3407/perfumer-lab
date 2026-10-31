// 승인 테스트용 웹 빌드: 가짜 서버 주소·가짜 로그인·짧은 기록 전송 간격으로 dist-test/ 를 만든다.
// playwright.config.ts 의 webServer 가 부른다. (예전 Kotlin 테스트의 AppConfig override 자리)
import { spawnSync } from 'node:child_process';

const env = {
  ...process.env,
  EXPO_PUBLIC_API_BASE_URL: 'https://fake.scent.test/api/v1',
  EXPO_PUBLIC_AUTH_MODE: 'fake',
  EXPO_PUBLIC_EVENT_FLUSH_MS: '200',
  EXPO_PUBLIC_SLOW_SERVER_HINT_MS: '3000',
};
const r = spawnSync('npx expo export -p web --output-dir dist-test --clear', { stdio: 'inherit', shell: true, env });
process.exit(r.status ?? 1);
