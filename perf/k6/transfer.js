import http from "k6/http";
import { check } from "k6";
import { uuidv4 } from "https://jslib.k6.io/k6-utils/1.4.0/index.js";

// W2 이체 베이스라인(구현계획 7.5절). NFR-PERF-001 기준: 이체 200ms 이내, 초당 100건.
// FROM_ACCOUNT_NUMBER/TO_ACCOUNT_NUMBER는 미리 시드해둔 두 계좌 번호를 환경변수로 받는다.
// 매 요청마다 방향을 번갈아 같은 계좌 쌍의 잔액이 한쪽으로만 마르지 않게 한다.
const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";
const FROM_ACCOUNT_NUMBER = __ENV.FROM_ACCOUNT_NUMBER;
const TO_ACCOUNT_NUMBER = __ENV.TO_ACCOUNT_NUMBER;

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

export default function () {
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
    },
  });

  check(response, {
    "status is 201": (r) => r.status === 201,
  });
}
