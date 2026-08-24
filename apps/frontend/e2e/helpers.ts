import { execSync } from "node:child_process";
import { readFileSync } from "node:fs";
import { Page, expect } from "@playwright/test";
import Redis from "ioredis";

/** SeedDataRunner가 만드는 고정 계정. */
export const SEED_CUSTOMERS = {
  kim: { loginId: "kim01", password: "seed-password-1234" },
  lee: { loginId: "lee01", password: "seed-password-1234" },
};

export async function login(page: Page, loginId: string, password: string) {
  await page.goto("/login");
  await page.waitForURL("**/login/id");
  await page.getByLabel("로그인ID").fill(loginId);
  await page.getByLabel("비밀번호").fill(password);
  await page.getByRole("button", { name: "로그인" }).click();
  await page.waitForURL("/");
  await expect(page.getByText("입출금통장").first()).toBeVisible();
}

/** 홈 화면의 primary 계좌 상세로 이동해 계좌번호를 얻는다. */
export async function primaryAccountNumber(page: Page): Promise<string> {
  const text = await page.getByText(/입출금통장 \d{3}-\d{6}-\d/).first().textContent();
  const match = text?.match(/\d{3}-\d{6}-\d/);
  if (!match) throw new Error("primary account number not found on home");
  return match[0];
}

export async function goToPrimaryAccount(page: Page) {
  await page.getByText(/입출금통장 \d{3}-\d{6}-\d/).first().click();
  await page.waitForURL(/\/accounts\/.+/);
}

/** OTP 키 삭제(만료 시뮬레이션)만 필요하므로 값 직렬화 포맷은 신경 쓰지 않는다. */
export function otpRedisClient(): Redis {
  return new Redis({ host: "localhost", port: 6379, lazyConnect: false });
}

const BACKEND_LOG = process.env.JBANK_BACKEND_LOG ?? "/tmp/backend.log";

/**
 * 실제 SMS 발송 대신 서버 로그로만 나가는 이체 OTP 코드를 로그 파일에서 읽는다
 * (OtpService.issue: "OTP 발급: transactionId={}, code={}"). Redisson이 RBucket 값을
 * 바이너리 코덱으로 저장해서 ioredis로 직접 GET하면 깨진 값이 나와 이 방식으로 대체했다.
 */
export function getOtpCode(transactionId: string): string {
  const log = readFileSync(BACKEND_LOG, "utf-8");
  const pattern = new RegExp(`OTP 발급: transactionId=${transactionId}, code=(\\d{6})`, "g");
  const matches = [...log.matchAll(pattern)];
  if (matches.length === 0) {
    throw new Error(`OTP code not found in ${BACKEND_LOG} for transactionId=${transactionId}`);
  }
  return matches[matches.length - 1][1];
}

/** Redis에서 OTP 키를 강제로 지워 "만료" 상태를 그대로 재현한다(OtpService.verify: stored===null → EXPIRED). */
export async function expireOtp(redis: Redis, transactionId: string): Promise<void> {
  await redis.del(`transfer:otp:${transactionId}`);
}

export function randomPhone(): string {
  const suffix = Date.now().toString().slice(-8);
  return `010${suffix}`;
}

export function randomSsn(): { front: string; back: string } {
  const front = "990101";
  const back = `1${Date.now().toString().slice(-6)}`;
  return { front, back };
}

/** PinKeypad는 매 렌더마다 숫자 배치가 섞일 수 있어 위치가 아니라 버튼 텍스트로 누른다. */
export async function enterPin(page: Page, pin: string) {
  for (const digit of pin) {
    await page.getByRole("button", { name: digit, exact: true }).click();
  }
}

/** AmountKeypad(입금/출금/이체/가입금액 공통)로 금액 자릿수를 하나씩 누른다. */
export async function enterAmount(page: Page, digits: string) {
  for (const digit of digits) {
    await page.getByRole("button", { name: digit, exact: true }).click();
  }
}

/** 로그인 화면으로는 다른 고객의 계좌번호를 알 방법이 없어 로컬 postgres 컨테이너를 직접 조회한다. */
export function accountNumberByLoginId(loginId: string): string {
  const sql = `SELECT a.account_number FROM accounts a JOIN customers c ON c.customer_id = a.customer_id WHERE c.login_id = '${loginId}' LIMIT 1;`;
  const out = execSync(
    `docker exec compose-postgres-1 psql -U jbank -d jbank -tAc "${sql}"`,
  ).toString().trim();
  if (!out) throw new Error(`account not found for loginId=${loginId}`);
  return out;
}

/** 계좌해지 화면은 accountId로만 진입 가능한데 계좌개설 완료 화면은 accountNumber만 보여준다. */
export function accountIdByAccountNumber(accountNumber: string): string {
  const sql = `SELECT account_id FROM accounts WHERE account_number = '${accountNumber}' LIMIT 1;`;
  const out = execSync(
    `docker exec compose-postgres-1 psql -U jbank -d jbank -tAc "${sql}"`,
  ).toString().trim();
  if (!out) throw new Error(`accountId not found for accountNumber=${accountNumber}`);
  return out;
}
