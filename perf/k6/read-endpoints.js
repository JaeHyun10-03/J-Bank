import http from "k6/http";
import { check } from "k6";

// 조회계 엔드포인트 부하 측정(perf/README.md 대규모 시드 절). 거래 1천만 건 상태에서
// 거래내역 조회(API-010) · 잔액 조회 · 계좌 상세 · 고객별 계좌 목록의 지연을 잰다.
// 한 번에 한 엔드포인트만 잰다 — ENDPOINT 환경변수로 고르고, run-10m.sh가 4번 호출한다.
// GET은 CSRF 이중제출 대상이 아니라 access_token 쿠키만 있으면 된다(SecurityConfig,
// CsrfDoubleSubmitFilter의 SAFE_METHODS). Secure 쿠키가 http://로 안 돌아오는 문제는
// transfer.js와 같은 방식으로 setup()에서 직접 헤더에 실어 우회한다.
const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";
const LOGIN_ID = __ENV.LOGIN_ID;
const PASSWORD = __ENV.PASSWORD;
const ACCOUNT_ID = __ENV.ACCOUNT_ID; // 로그인 계정이 소유한, 매칭률이 낮은 계좌
const ENDPOINT = __ENV.ENDPOINT || "history"; // history | balance | account | customer-accounts

export function setup() {
  const loginRes = http.post(
    `${BASE_URL}/api/v1/auth/login`,
    JSON.stringify({ loginId: LOGIN_ID, password: PASSWORD }),
    { headers: { "Content-Type": "application/json" } },
  );
  check(loginRes, { "login succeeded": (r) => r.status === 200 });

  const cookieHeader = Object.entries(loginRes.cookies)
    .map(([name, jar]) => `${name}=${jar[0].value}`)
    .join("; ");
  const customerId = loginRes.json("data.customerId");
  return { cookieHeader, customerId };
}

export const options = {
  scenarios: {
    read_baseline: {
      executor: "constant-arrival-rate",
      rate: 100,
      timeUnit: "1s",
      duration: "30s",
      preAllocatedVUs: 50,
      maxVUs: 200,
    },
  },
  thresholds: {
    http_req_duration: ["p(95)<200"],
    http_req_failed: ["rate<0.01"],
  },
};

function urlFor(data) {
  switch (ENDPOINT) {
    case "balance":
      return `${BASE_URL}/api/v1/accounts/${ACCOUNT_ID}/balance`;
    case "account":
      return `${BASE_URL}/api/v1/accounts/${ACCOUNT_ID}`;
    case "customer-accounts":
      return `${BASE_URL}/api/v1/customers/${data.customerId}/accounts?page=0&size=20`;
    case "history":
    default:
      return `${BASE_URL}/api/v1/accounts/${ACCOUNT_ID}/transactions?page=0&size=20`;
  }
}

export default function (data) {
  const res = http.get(urlFor(data), { headers: { Cookie: data.cookieHeader } });
  check(res, { "status is 200": (r) => r.status === 200 });
}
