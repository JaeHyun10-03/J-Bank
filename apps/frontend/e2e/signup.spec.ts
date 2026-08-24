import { test, expect } from "@playwright/test";
import { enterPin, randomPhone, randomSsn } from "./helpers";

// 04번 화면플로우차트 4절 회원가입 및 계좌개설 플로우. eddRequired=true 분기는
// KYC 등급 판정 로직에 의존하는 조건이라 이번 스코프에서는 다루지 않는다.
test("신규 고객이 회원가입부터 계좌개설까지 마친다", async ({ page }) => {
  const { front, back } = randomSsn();
  const phone = randomPhone();
  const name = `테스트${Date.now().toString().slice(-6)}`;

  await page.goto("/welcome");
  await page.getByRole("link", { name: "제이뱅크 시작하기" }).click();
  await page.waitForURL(/\/signup\/permissions/);

  await page.getByRole("button", { name: "확인" }).click();
  await page.waitForURL(/\/signup\/onboard/);

  await page.getByRole("link", { name: "제이뱅크 시작하기" }).click();
  await page.waitForURL(/\/signup\/name/);

  await page.getByPlaceholder("이름").fill(name);
  await page.getByRole("button", { name: "다음" }).click();
  await page.waitForURL(/\/signup\/ssn/);

  await page.getByPlaceholder("앞자리").fill(front);
  await page.getByPlaceholder("뒷자리").pressSequentially(back);
  await page.getByRole("button", { name: "본인인증 진행하기" }).click();
  await page.waitForURL(/\/signup\/phone/);

  await page.getByPlaceholder("010-1234-5678").fill(phone);
  await page.getByRole("button", { name: "본인인증 진행하기" }).click();
  await page.waitForURL(/\/signup\/otp/);

  await page.getByPlaceholder("인증번호").fill("000000");
  await page.getByRole("button", { name: "다음" }).click();
  await page.waitForURL(/\/signup\/device-done/);

  await page.getByRole("button", { name: "확인" }).click();
  await page.waitForURL(/\/signup\/pin/);

  await enterPin(page, "135790");
  await enterPin(page, "135790");
  await page.waitForURL(/\/accounts\/open/);

  await page.getByRole("button", { name: "계좌 개설하기" }).click();
  await expect(page.getByText("계좌개설 완료")).toBeVisible();
  await page.getByRole("button", { name: "홈으로" }).click();
  await expect(page).toHaveURL("/");
});
