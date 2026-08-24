import { test, expect } from "@playwright/test";
import { SEED_CUSTOMERS, accountIdByAccountNumber, enterAmount, login } from "./helpers";

// 04번 화면플로우차트 8절 계좌해지 플로우. 매번 새 계좌를 만들어 해지하므로 기존
// seed 계좌 잔액에 영향을 주지 않는다.
test.describe("계좌해지", () => {
  async function openNewAccount(page: import("@playwright/test").Page): Promise<string> {
    await page.goto("/accounts/open");
    await page.getByRole("button", { name: "계좌 개설하기" }).click();
    const text = await page.getByText(/계좌가 개설됐습니다/).textContent();
    const match = text?.match(/\d{3}-\d{6}-\d/);
    if (!match) throw new Error("opened account number not found");
    await page.getByRole("button", { name: "홈으로" }).click();
    return match[0];
  }

  test("잔액이 0원인 계좌는 바로 해지된다", async ({ page }) => {
    await login(page, SEED_CUSTOMERS.kim.loginId, SEED_CUSTOMERS.kim.password);
    const accountNumber = await openNewAccount(page);
    const accountId = accountIdByAccountNumber(accountNumber);

    await page.goto(`/accounts/${accountId}/close`);
    await page.getByRole("button", { name: "계좌해지" }).click();
    await page.getByRole("button", { name: "해지할게요" }).click();
    await expect(page.getByText("계좌해지 완료")).toBeVisible();
  });

  test("잔액이 남아있으면 해지가 거절된다", async ({ page }) => {
    await login(page, SEED_CUSTOMERS.kim.loginId, SEED_CUSTOMERS.kim.password);
    const accountNumber = await openNewAccount(page);
    const accountId = accountIdByAccountNumber(accountNumber);

    await page.goto(`/accounts/${accountId}/deposit`);
    await enterAmount(page, "5000");
    await page.getByRole("button", { name: "다음" }).click();
    await page.getByRole("button", { name: "입금하기" }).click();
    await expect(page.getByText("입금됐어요")).toBeVisible();

    await page.goto(`/accounts/${accountId}/close`);
    await page.getByRole("button", { name: "계좌해지" }).click();
    await page.getByRole("button", { name: "해지할게요" }).click();
    await expect(page.getByText("잔액을 0원으로 만든 후 다시 시도해주세요.")).toBeVisible();
  });
});
