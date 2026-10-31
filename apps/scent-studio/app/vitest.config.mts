import { defineConfig } from 'vitest/config';

// 부품 단위 테스트 (QA팀 추가 테스트, 승인 대상 아님). 승인 목록은 playwright.config.ts
export default defineConfig({
  test: {
    include: ['tests/unit/**/*.test.ts'],
    environment: 'node',
  },
});
