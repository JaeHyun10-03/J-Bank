import { commonOptions, hot, vuSender, vuLoginUser, transfer, balance, setupSessions } from "./lib/ec2.js";

// S2 핫 계좌(task.md 측정 조건). 발신 고객 200명이 핫 수신 계좌 1개로 1,000원씩 이체한다.
// 초당 20건에서 1분마다 +20, 상한 400. 무너짐 판정 후 한 단계 더 가면 오케스트레이터가 중단한다.
// 핫 계좌와 무관한 계좌(로그인 전용 고객 본인 계좌) 잔액 조회를 초당 20건 고정으로 병행해
// 장애 전파를 본다. 병행 조회는 이체보다 60초 먼저 시작해 "S2 시작 전 60초 p95" 기준을 만든다.
// S2_START·S2_STEP·S2_MAX로 램프를 바꿀 수 있다(s2f 정밀 측정: 40·10·400). 기본값은 기존 S2와 같다.
const STEP = Number(__ENV.S2_STEP || 20);
const START_RATE = Number(__ENV.S2_START || STEP);
const MAX_RATE = Number(__ENV.S2_MAX || 400);
const STAGE = 60;
const LEAD = 60;

function stages() {
  const out = [];
  for (let rate = START_RATE; rate <= MAX_RATE; rate += STEP) {
    out.push({ duration: "1s", target: rate }, { duration: `${STAGE - 1}s`, target: rate });
  }
  return out;
}

const totalSeconds = LEAD + (Math.floor((MAX_RATE - START_RATE) / STEP) + 1) * STAGE;

export const options = Object.assign({}, commonOptions, {
  scenarios: {
    parallel_balance: {
      executor: "constant-arrival-rate",
      rate: 20,
      timeUnit: "1s",
      duration: `${totalSeconds}s`,
      preAllocatedVUs: 20,
      maxVUs: 500,
      exec: "parallelBalance",
    },
    hot_transfer: {
      executor: "ramping-arrival-rate",
      startTime: `${LEAD}s`,
      startRate: START_RATE,
      timeUnit: "1s",
      preAllocatedVUs: 100,
      maxVUs: 2000,
      stages: stages(),
      exec: "hotTransfer",
    },
  },
});

export function setup() {
  return setupSessions();
}

export function hotTransfer(data) {
  transfer(data, vuSender(), hot.accountNumber, "hot-transfer");
}

export function parallelBalance(data) {
  balance(data, vuLoginUser(), "parallel-balance");
}
