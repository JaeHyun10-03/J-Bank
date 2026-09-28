import http from "k6/http";
import { check } from "k6";
import { SharedArray } from "k6/data";
import { Trend } from "k6/metrics";
import { uuidv4 } from "https://jslib.k6.io/k6-utils/1.4.0/index.js";

// EC2 부하 테스트(S1·S2·S3·S5) 공통 모듈. perf/README.md "EC2 부하 테스트" 절.
// - 계정: perf/prepare-accounts.py가 만든 ACCOUNTS_FILE(JSON). 비밀번호는 PERF_PASSWORD.
// - 대상은 내부 CA 인증서라 각 스크립트 options에 insecureSkipTLSVerify를 켠다.
// - 요청마다 name 태그를 달아 Grafana·요약에서 API별로 나눠 본다.
// - 로그인 부하가 측정을 오염시키지 않도록 setup()에서 perf 고객 전원을 한 번 로그인해 토큰을
//   나눠 쓴다. 각 VU는 고정된 고객 한 명을 쓰고(VU 번호 기준), 액세스 토큰 TTL(15분)보다 먼저
//   토큰 발급 후 9~12분 사이(VU마다 무작위)에 자기 고객으로 다시 로그인한다(name=relogin, 로그인
//   혼합 비율과 별도 집계). 1차 측정에서 모든 VU가 12분에 한꺼번에 재로그인해 포화가 생겨 분산했다.
// - setup이 끝나는 시각을 SCENARIO_START로 로그에 남긴다. 단계 구간은 이 시각부터 센다.

export const BASE_URL = __ENV.BASE_URL;
const PASSWORD = __ENV.PERF_PASSWORD;
const RELOGIN_MIN_MS = 9 * 60 * 1000;
const RELOGIN_SPREAD_MS = 3 * 60 * 1000;
// VU마다 정한 재로그인 시점(분). 요약의 min·max로 분산 범위를 확인한다.
const reloginAfterTrend = new Trend("relogin_after_minutes");
const SEED_ACCOUNTS = 100000; // seed-10m.sql: 계좌번호 '900' || lpad(1..100000, 10, '0')

const accounts = JSON.parse(open(__ENV.ACCOUNTS_FILE));
export const senders = new SharedArray("senders", () => accounts.senders);
export const loginUsers = new SharedArray("loginUsers", () => accounts.loginUsers);
export const hot = accounts.hot;

export const commonOptions = {
  insecureSkipTLSVerify: true,
  setupTimeout: "5m",
  summaryTrendStats: ["min", "med", "avg", "p(90)", "p(95)", "p(99)", "max"],
};

export const REQUEST_TIMEOUT = "10s";

function login(user, name) {
  const res = http.post(
    `${BASE_URL}/api/v1/auth/login`,
    JSON.stringify({ loginId: user.loginId, password: PASSWORD }),
    { headers: { "Content-Type": "application/json" }, tags: { name }, timeout: REQUEST_TIMEOUT },
  );
  check(res, { [`${name} 200`]: (r) => r.status === 200 });
  if (res.status !== 200) return null;
  const access = res.cookies.access_token && res.cookies.access_token[0].value;
  const xsrf = res.cookies["XSRF-TOKEN"] && res.cookies["XSRF-TOKEN"][0].value;
  if (!access || !xsrf) return null;
  return {
    "Content-Type": "application/json",
    Cookie: `access_token=${access}; XSRF-TOKEN=${xsrf}`,
    "X-CSRF-TOKEN": xsrf,
  };
}

// 각 시나리오의 setup()에서 호출한다. 측정 구간 밖에서 전원 로그인한다.
export function setupSessions() {
  // 사용자별 토큰 발급 시각을 함께 남긴다. 재로그인 기준 시각이 setup 소요 시간에 밀리지 않게 한다.
  const tokens = {};
  for (const user of senders.concat(loginUsers)) {
    const headers = login(user, "setup-login");
    if (headers) tokens[user.loginId] = { headers, at: Date.now() };
  }
  console.log(`SCENARIO_START ${new Date().toISOString()}`);
  return { tokens };
}

// VU 전역 상태: 이 VU가 다시 로그인한 토큰과 이 VU의 재로그인 시점.
let own = null;
let reloginAfterMs = null;

export function session(data, user) {
  if (reloginAfterMs === null) {
    reloginAfterMs = RELOGIN_MIN_MS + Math.random() * RELOGIN_SPREAD_MS;
    reloginAfterTrend.add(reloginAfterMs / 60000);
  }
  const current = own || data.tokens[user.loginId];
  if (current && Date.now() - current.at < reloginAfterMs) return current.headers;
  const headers = login(user, "relogin");
  own = headers ? { headers, at: Date.now() } : null;
  return headers;
}

// VU마다 고정 고객 한 명을 쓴다(토큰 재사용·재로그인 횟수를 VU 수로 묶는다).
export function vuSender() {
  return senders[(__VU - 1) % senders.length];
}

export function vuLoginUser() {
  return loginUsers[(__VU - 1) % loginUsers.length];
}

export function pick(arr) {
  return arr[Math.floor(Math.random() * arr.length)];
}

export function randomSeedAccountNumber() {
  const n = 1 + Math.floor(Math.random() * SEED_ACCOUNTS);
  return "900" + String(n).padStart(10, "0");
}

function get(path, headers, name) {
  const res = http.get(`${BASE_URL}${path}`, { headers, tags: { name }, timeout: REQUEST_TIMEOUT });
  check(res, { [`${name} 200`]: (r) => r.status === 200 });
}

export function transfer(data, user, toAccountNumber, name) {
  const headers = session(data, user);
  if (!headers) return;
  const key = uuidv4();
  const res = http.post(
    `${BASE_URL}/api/v1/transfers`,
    JSON.stringify({
      fromAccountNumber: user.accountNumber,
      toAccountNumber,
      amount: "1000.00",
      memo: "k6 ec2",
    }),
    {
      headers: Object.assign({ "Idempotency-Key": key }, headers),
      tags: { name },
      timeout: REQUEST_TIMEOUT,
    },
  );
  const ok = check(res, { [`${name} 201`]: (r) => r.status === 201 });
  // 실패 응답(timeout 상태 0·5xx 등)의 멱등키를 남긴다. 측정 후 DB와 대조해 실제로 반영됐는지 센다(REQ-10 (4)).
  if (!ok) console.log(`FAILKEY ${key} ${res.status} ${name}`);
}

export function balance(data, user, name) {
  const headers = session(data, user);
  if (headers) get(`/api/v1/accounts/${user.accountId}/balance`, headers, name);
}

// S1 혼합 비율(task.md 측정 조건): 거래내역 25·잔액 20·계좌 상세 15·고객 계좌 목록 10·이체 20·로그인 10.
export function mixedIteration(data) {
  const r = Math.random() * 100;
  if (r < 10) {
    login(pick(loginUsers), "login");
    return;
  }
  const user = vuSender();
  if (r < 30) {
    transfer(data, user, randomSeedAccountNumber(), "transfer");
    return;
  }
  const headers = session(data, user);
  if (!headers) return;
  if (r < 55) get(`/api/v1/accounts/${user.accountId}/transactions?page=0&size=20`, headers, "history");
  else if (r < 75) get(`/api/v1/accounts/${user.accountId}/balance`, headers, "balance");
  else if (r < 90) get(`/api/v1/accounts/${user.accountId}`, headers, "account");
  else get(`/api/v1/customers/${user.customerId}/accounts?page=0&size=20`, headers, "customer-accounts");
}
