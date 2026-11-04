import http from "k6/http";
import { Counter } from "k6/metrics";
import { uuidv4 } from "https://jslib.k6.io/k6-utils/1.4.0/index.js";

// 교착 재현 실험(docs/runbook/transfer-deadlock-experiment.md). perf/run-deadlock.sh가 실행한다.
// 같은 고객의 두 계좌 사이에서 1,000원을 이체하고, 방향을 (__VU + __ITER) % 2로 정해 시작부터
// 양방향이 섞이게 한다. 응답 코드는 상태 구간·오류 code별 카운터로 요약에 남긴다.
// 쿠키는 transfer.js처럼 로그인 응답에서 읽어 헤더로 직접 보낸다(로컬 http에서 Secure 쿠키 문제).
const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";
const ACCOUNT_A = __ENV.ACCOUNT_A;
const ACCOUNT_B = __ENV.ACCOUNT_B;

const status2xx = new Counter("status_2xx");
const status4xx = new Counter("status_4xx");
const status5xx = new Counter("status_5xx");
// 요청 타임아웃(30초)·연결 실패는 status 0이다.
const status0 = new Counter("status_0");
const codeInternal = new Counter("code_COMMON_006_INTERNAL_ERROR");
const codeOther = new Counter("code_other");
const seenOtherCodes = new Set();

export const options = {
  scenarios: {
    deadlock: {
      executor: "constant-vus",
      vus: Number(__ENV.VUS || 5),
      duration: __ENV.DURATION || "60s",
    },
  },
  summaryTrendStats: ["avg", "min", "med", "max", "p(95)", "p(99)"],
};

export function setup() {
  const res = http.post(
    `${BASE_URL}/api/v1/auth/login`,
    JSON.stringify({ loginId: __ENV.LOGIN_ID, password: __ENV.PASSWORD }),
    { headers: { "Content-Type": "application/json" } },
  );
  if (res.status !== 200) {
    throw new Error(`로그인 실패: status ${res.status}`);
  }
  const cookieHeader = Object.entries(res.cookies)
    .map(([name, jar]) => `${name}=${jar[0].value}`)
    .join("; ");
  return { cookieHeader, csrfToken: res.cookies["XSRF-TOKEN"][0].value };
}

export default function (data) {
  const forward = (__VU + __ITER) % 2 === 0;
  const res = http.post(
    `${BASE_URL}/api/v1/transfers`,
    JSON.stringify({
      fromAccountNumber: forward ? ACCOUNT_A : ACCOUNT_B,
      toAccountNumber: forward ? ACCOUNT_B : ACCOUNT_A,
      amount: "1000.00",
      memo: "deadlock experiment",
    }),
    {
      headers: {
        "Content-Type": "application/json",
        "Idempotency-Key": uuidv4(),
        Cookie: data.cookieHeader,
        "X-CSRF-TOKEN": data.csrfToken,
      },
      timeout: "30s",
    },
  );

  if (res.status === 0) {
    status0.add(1);
    return;
  }
  if (res.status < 300) {
    status2xx.add(1);
    return;
  }
  (res.status < 500 ? status4xx : status5xx).add(1);
  let code = "NO_CODE";
  try {
    code = res.json("error.code") || code;
  } catch (e) {
    // 본문이 JSON이 아님(프록시 502 등)
  }
  if (code === "COMMON_006_INTERNAL_ERROR") {
    codeInternal.add(1);
    return;
  }
  codeOther.add(1);
  if (!seenOtherCodes.has(code)) {
    seenOtherCodes.add(code);
    console.warn(`other_code status=${res.status} code=${code}`);
  }
}
