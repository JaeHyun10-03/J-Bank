import { test, expect } from "@playwright/test";
import { SEED_CUSTOMERS, enterAmount, goToPrimaryAccount, login } from "./helpers";

// 04번 화면플로우차트 5절 입출금 플로우.
test.describe("입출금", () => {
  test("입금하면 잔액이 늘고, 그 금액을 출금하면 잔액이 원복된다", async ({ page }) => {
    await login(page, SEED_CUSTOMERS.kim.loginId, SEED_CUSTOMERS.kim.password);
    await goToPrimaryAccount(page);

    await page.getByRole("link", { name: "입금" }).click();
    await page.waitForURL(/\/deposit/);
    await enterAmount(page, "10000");
    await page.getByRole("button", { name: "다음" }).click();
    await page.getByRole("button", { name: "입금하기" }).click();
    await expect(page.getByText("10,000원이 입금됐어요")).toBeVisible();
    await page.getByRole("button", { name: "확인" }).click();

    await page.getByRole("link", { name: "출금" }).click();
    await page.waitForURL(/\/withdraw/);
    await enterAmount(page, "10000");
    await page.getByRole("button", { name: "다음" }).click();
    await page.getByRole("button", { name: "출금하기" }).click();
    await expect(page.getByText("10,000원이 출금됐어요")).toBeVisible();
  });

  test("출금 가능 금액을 초과하면 다음 버튼이 막힌다", async ({ page }) => {
    await login(page, SEED_CUSTOMERS.kim.loginId, SEED_CUSTOMERS.kim.password);
    await goToPrimaryAccount(page);

    await page.getByRole("link", { name: "출금" }).click();
    await page.waitForURL(/\/withdraw/);
    await enterAmount(page, "999999999999");
    await expect(page.getByText("출금 가능 금액을 초과했습니다")).toBeVisible();
    await expect(page.getByRole("button", { name: "다음" })).toBeDisabled();
  });
});
