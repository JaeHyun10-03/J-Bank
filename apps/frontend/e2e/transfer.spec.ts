import { test, expect } from "@playwright/test";
import {
  SEED_CUSTOMERS,
  accountNumberByLoginId,
  enterAmount,
  expireOtp,
  getOtpCode,
  goToPrimaryAccount,
  login,
  otpRedisClient,
} from "./helpers";

// 04번 화면플로우차트 6절 계좌이체 플로우. 임계금액(1000만원) 기준 이하/초과
// 두 경로와 OTP 불일치/만료 두 실패 경로.
test.describe("계좌이체", () => {
  let leeAccountNumber: string;

  test.beforeAll(() => {
    leeAccountNumber = accountNumberByLoginId(SEED_CUSTOMERS.lee.loginId);
  });

  async function depositToPrimary(page: import("@playwright/test").Page, amountDigits: string) {
    await goToPrimaryAccount(page);
    await page.getByRole("link", { name: "입금" }).click();
    await page.waitForURL(/\/deposit/);
    await enterAmount(page, amountDigits);
    await page.getByRole("button", { name: "다음" }).click();
    await page.getByRole("button", { name: "입금하기" }).click();
    await expect(page.getByText("입금됐어요")).toBeVisible();
    await page.getByRole("button", { name: "확인" }).click();
  }

  test("임계금액 이하 이체는 OTP 없이 바로 완료된다", async ({ page }) => {
    await login(page, SEED_CUSTOMERS.kim.loginId, SEED_CUSTOMERS.kim.password);
    await depositToPrimary(page, "100000");

    await page.getByRole("link", { name: "이체" }).click();
    await page.waitForURL(/\/transfer$/);
    await page.getByPlaceholder("계좌번호 입력").fill(leeAccountNumber);
    await page.getByRole("button", { name: "다음" }).click();
    await enterAmount(page, "50000");
    await page.getByRole("button", { name: "다음" }).click();
    await page.waitForURL(/\/transfer\/confirm/);
    await page.getByRole("button", { name: "보내기" }).click();
    await expect(page.getByText("보냈어요")).toBeVisible();
  });

  test("임계금액 초과 이체는 OTP 인증 후 완료된다", async ({ page }) => {
    await login(page, SEED_CUSTOMERS.kim.loginId, SEED_CUSTOMERS.kim.password);
    await depositToPrimary(page, "15000000");

    await page.getByRole("link", { name: "이체" }).click();
    await page.waitForURL(/\/transfer$/);
    await page.getByPlaceholder("계좌번호 입력").fill(leeAccountNumber);
    await page.getByRole("button", { name: "다음" }).click();
    await enterAmount(page, "11000000");
    await page.getByRole("button", { name: "다음" }).click();
    await page.waitForURL(/\/transfer\/confirm/);

    const [response] = await Promise.all([
      page.waitForResponse((r) => /\/transfers$/.test(r.url()) && r.request().method() === "POST"),
      page.getByRole("button", { name: "보내기" }).click(),
    ]);
    const body = await response.json();
    expect(body.data.status).toBe("PENDING_OTP");
    const transactionId = body.data.transactionId as string;

    await page.waitForURL(/\/transfer\/otp/);
    const code = getOtpCode(transactionId);
    await page.getByPlaceholder("인증번호").fill(code);
    await page.getByRole("button", { name: "확인" }).click();
    await expect(page.getByText("보냈어요")).toBeVisible();
  });

  test("OTP를 틀리면 불일치 안내가 뜬다", async ({ page }) => {
    await login(page, SEED_CUSTOMERS.kim.loginId, SEED_CUSTOMERS.kim.password);
    await depositToPrimary(page, "11000000");

    await page.getByRole("link", { name: "이체" }).click();
    await page.waitForURL(/\/transfer$/);
    await page.getByPlaceholder("계좌번호 입력").fill(leeAccountNumber);
    await page.getByRole("button", { name: "다음" }).click();
    await enterAmount(page, "11000000");
    await page.getByRole("button", { name: "다음" }).click();
    await page.getByRole("button", { name: "보내기" }).click();
    await page.waitForURL(/\/transfer\/otp/);

    await page.getByPlaceholder("인증번호").fill("000000");
    await page.getByRole("button", { name: "확인" }).click();
    await expect(page.getByText("인증번호가 일치하지 않습니다")).toBeVisible();
  });

  test("OTP가 만료되면 지급정지가 풀리고 이체입력 화면으로 돌아간다", async ({ page }) => {
    await login(page, SEED_CUSTOMERS.kim.loginId, SEED_CUSTOMERS.kim.password);
    await depositToPrimary(page, "11000000");

    await page.getByRole("link", { name: "이체" }).click();
    await page.waitForURL(/\/transfer$/);
    await page.getByPlaceholder("계좌번호 입력").fill(leeAccountNumber);
    await page.getByRole("button", { name: "다음" }).click();
    await enterAmount(page, "11000000");
    await page.getByRole("button", { name: "다음" }).click();

    const [response] = await Promise.all([
      page.waitForResponse((r) => /\/transfers$/.test(r.url()) && r.request().method() === "POST"),
      page.getByRole("button", { name: "보내기" }).click(),
    ]);
    const body = await response.json();
    const transactionId = body.data.transactionId as string;
    await page.waitForURL(/\/transfer\/otp/);

    const redis = otpRedisClient();
    try {
      await expireOtp(redis, transactionId);
    } finally {
      redis.disconnect();
    }

    await page.getByPlaceholder("인증번호").fill("000000");
    await page.getByRole("button", { name: "확인" }).click();
    // AUTH_005_OTP_EXPIRED 처리는 안내 문구를 띄우자마자 이체입력 화면으로
    // router.replace하므로(otp/page.tsx backToTransferInput) 메시지 자체보다
    // 최종적으로 이체입력 화면으로 돌아왔는지를 검증한다.
    await page.waitForURL(/\/transfer$/);
    await expect(page.getByText("어디로 돈을 보낼까요?")).toBeVisible();
  });
});
