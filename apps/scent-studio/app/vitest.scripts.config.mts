// 확인용 스크립트(scripts/*.check.ts)만 돌리는 설정. run.sh(테스트)에는 들어가지 않는다 — 실제 카탈로그로 오래 걸리는 계산용.
// 예: npx vitest run -c vitest.scripts.config.mts
import { defineConfig } from 'vitest/config';

export default defineConfig({
  test: { include: [process.env.CHECK ?? 'scripts/**/*.check.ts'], testTimeout: 1_800_000 },
});
