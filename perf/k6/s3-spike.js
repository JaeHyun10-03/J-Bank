import { commonOptions, vuLoginUser, balance, mixedIteration, setupSessions } from "./lib/ec2.js";

// S3 스파이크(task.md 측정 조건). 탐침 잔액 조회 초당 10건을 스파이크 60초 전부터 종료 후 180초까지
// 계속 보내고, 그 p95로 회복을 판정한다. 스파이크는 S1 혼합 비율로 10초 동안 초당 0→500, 60초 유지,
// 10초 동안 0으로 내린다.
const LEAD = 60;
const SPIKE = 10 + 60 + 10;
const TAIL = 180;

export const options = Object.assign({}, commonOptions, {
  scenarios: {
    probe: {
      executor: "constant-arrival-rate",
      rate: 10,
      timeUnit: "1s",
      duration: `${LEAD + SPIKE + TAIL}s`,
      preAllocatedVUs: 10,
      maxVUs: 300,
      exec: "probe",
    },
    spike: {
      executor: "ramping-arrival-rate",
      startTime: `${LEAD}s`,
      startRate: 0,
      timeUnit: "1s",
      preAllocatedVUs: 300,
      maxVUs: 2000,
      stages: [
        { duration: "10s", target: 500 },
        { duration: "60s", target: 500 },
        { duration: "10s", target: 0 },
      ],
      exec: "spike",
    },
  },
});

export function setup() {
  return setupSessions();
}

export function probe(data) {
  balance(data, vuLoginUser(), "probe-balance");
}

export function spike(data) {
  mixedIteration(data);
}
