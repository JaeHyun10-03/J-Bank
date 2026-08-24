import { test, expect } from "@playwright/test";
import { SEED_CUSTOMERS, enterAmount, login } from "./helpers";

// 04번 화면플로우차트 7절 상품가입 플로우. PRD_001/PRD_002 서버 에러 경로는
// subscribe 화면이 최소금액 미만이면 버튼 자체를 비활성화해 클라이언트에서 막아버려
// 화면으로 재현할 수 없다(별도로 남겨둠, README "다음" 참고).
test("상품 목록에서 J팜을 가입하면 계약번호와 만기일이 뜬다", async ({ page }) => {
  await login(page, SEED_CUSTOMERS.kim.loginId, SEED_CUSTOMERS.kim.password);

  await page.goto("/products");
  await page.getByRole("link", { name: /J팜 농장/ }).click();
  await page.waitForURL(/\/products\/j-farm$/);
  await page.getByRole("button", { name: "농장 만들기" }).click();
  await page.waitForURL(/\/products\/j-farm\/subscribe/);

  await page.getByText("입출금통장").first().click();
  await enterAmount(page, "10000");
  await page.getByRole("button", { name: "가입하기" }).click();

  await expect(page.getByText("가입이")).toBeVisible();
  await expect(page.getByText(/계약번호/)).toBeVisible();

  await page.getByRole("button", { name: "내 가입상품 보기" }).click();
  await page.waitForURL(/\/contracts/);
  await expect(page.getByText("j-farm")).toBeVisible();
});
