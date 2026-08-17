import http from "k6/http";
import { check } from "k6";
import { uuidv4 } from "https://jslib.k6.io/k6-utils/1.4.0/index.js";

// W2 이체 베이스라인(구현계획 7.5절). NFR-PERF-001 기준: 이체 200ms 이내, 초당 100건.
// FROM_ACCOUNT_NUMBER/TO_ACCOUNT_NUMBER는 미리 시드해둔 두 계좌 번호를 환경변수로 받는다.
// 두 계좌 모두 LOGIN_ID/PASSWORD 계정 소유여야 한다(방향을 번갈아 보내므로 양쪽 다
// 발신 계좌가 됨) — 매 요청마다 방향을 번갈아 같은 계좌 쌍의 잔액이 한쪽으로만
// 마르지 않게 한다.
//
// W3부터 이체 API가 인증을 요구한다(JWT 쿠키 + CSRF 이중제출). access_token 쿠키에
// Secure 속성이 붙어 k6 기본 쿠키 저장소는 http:// 로컬 실행에서 이를 되돌려보내지
// 않으므로, setup()에서 로그인 응답 쿠키를 직접 읽어 매 요청 헤더에 수동으로 실어
// 보낸다(자동 쿠키 저장소에 의존하지 않음).
const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";
const FROM_ACCOUNT_NUMBER = __ENV.FROM_ACCOUNT_NUMBER;
const TO_ACCOUNT_NUMBER = __ENV.TO_ACCOUNT_NUMBER;
const LOGIN_ID = __ENV.LOGIN_ID;
const PASSWORD = __ENV.PASSWORD;

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
  const csrfToken = loginRes.cookies["XSRF-TOKEN"][0].value;
  return { cookieHeader, csrfToken };
}

export const options = {
  scenarios: {
    transfer_baseline: {
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

export default function (data) {
  const forward = __ITER % 2 === 0;
  const payload = JSON.stringify({
    fromAccountNumber: forward ? FROM_ACCOUNT_NUMBER : TO_ACCOUNT_NUMBER,
    toAccountNumber: forward ? TO_ACCOUNT_NUMBER : FROM_ACCOUNT_NUMBER,
    amount: "100.00",
    memo: "k6 baseline",
  });

  const response = http.post(`${BASE_URL}/api/v1/transfers`, payload, {
    headers: {
      "Content-Type": "application/json",
      "Idempotency-Key": uuidv4(),
      Cookie: data.cookieHeader,
      "X-CSRF-TOKEN": data.csrfToken,
    },
  });

  check(response, {
    "status is 201": (r) => r.status === 201,
  });
}
