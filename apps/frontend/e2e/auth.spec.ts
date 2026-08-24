import { test, expect } from "@playwright/test";
import { SEED_CUSTOMERS, login } from "./helpers";

// 04번 화면플로우차트 3절 앱 진입 및 인증 플로우.
test.describe("로그인/로그아웃", () => {
  test("올바른 자격증명으로 로그인하면 홈으로 이동한다", async ({ page }) => {
    await login(page, SEED_CUSTOMERS.kim.loginId, SEED_CUSTOMERS.kim.password);
    await expect(page).toHaveURL("/");
  });

  test("잘못된 비밀번호면 에러 메시지가 뜨고 로그인 화면에 머무른다", async ({ page }) => {
    await page.goto("/login");
    await page.waitForURL("**/login/id");
    await page.getByLabel("로그인ID").fill(SEED_CUSTOMERS.kim.loginId);
    await page.getByLabel("비밀번호").fill("wrong-password");
    await page.getByRole("button", { name: "로그인" }).click();
    await expect(page.getByText("로그인ID 또는 비밀번호가 일치하지 않습니다.")).toBeVisible();
    await expect(page).toHaveURL(/\/login\/id/);
  });

  test("로그아웃하면 로그인 화면으로 돌아가고 인증 쿠키가 지워진다", async ({ page }) => {
    await login(page, SEED_CUSTOMERS.kim.loginId, SEED_CUSTOMERS.kim.password);
    await page.getByRole("button", { name: "로그아웃" }).click();
    await page.waitForURL(/\/login/);
    await page.goto("/");
    await page.waitForURL(/\/welcome/);
  });
});
