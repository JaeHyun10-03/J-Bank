import { defineConfig, devices } from "@playwright/test";

/**
 * 04번 화면플로우차트 문서의 흐름을 실제 브라우저로 검증한다. postgres/redis
 * 도커컴포즈(infra/compose --profile core)와 백엔드(local,seed 프로필)가 떠 있어야
 * 한다 — CI 연동은 이번 스코프 밖(README "다음" 참고).
 */
export default defineConfig({
  testDir: "./e2e",
  fullyParallel: false,
  workers: 1, // kim01/lee01 seed 계좌 잔액을 여러 테스트가 공유해서 건드린다
  retries: 0,
  reporter: [["list"]],
  use: {
    baseURL: "http://localhost:3000",
    trace: "retain-on-failure",
  },
  projects: [
    {
      name: "chromium",
      use: { ...devices["Desktop Chrome"] },
    },
  ],
});
