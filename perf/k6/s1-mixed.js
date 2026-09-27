import { commonOptions, mixedIteration, setupSessions } from "./lib/ec2.js";

// S1 한계 탐색과 그 변형(task.md 측정 조건).
//   MODE=ramp     : 초당 50건에서 2분마다 +50, 상한 1,000. 무너짐 판정 후 한 단계 더 가면
//                   오케스트레이터(perf/run-ec2.py)가 k6를 중단시킨다.
//   MODE=constant : RATE·DURATION 고정. 예열(초당 20건 2분)과 S5 배치 중첩(최대 지속 가능 요청률의 70%)에 쓴다.
const MODE = __ENV.MODE || "ramp";
const STEP = 50;
const MAX_RATE = 1000;
const STAGE = 120; // 초

function rampStages() {
  const stages = [];
  for (let rate = STEP; rate <= MAX_RATE; rate += STEP) {
    stages.push({ duration: "1s", target: rate }, { duration: `${STAGE - 1}s`, target: rate });
  }
  return stages;
}

const scenario =
  MODE === "ramp"
    ? {
        executor: "ramping-arrival-rate",
        startRate: STEP,
        timeUnit: "1s",
        preAllocatedVUs: 200,
        maxVUs: 2000,
        stages: rampStages(),
      }
    : {
        executor: "constant-arrival-rate",
        rate: Number(__ENV.RATE),
        timeUnit: "1s",
        duration: __ENV.DURATION,
        preAllocatedVUs: 200,
        maxVUs: 2000,
      };

export const options = Object.assign({}, commonOptions, {
  scenarios: { mixed: scenario },
});

export function setup() {
  return setupSessions();
}

export default function (data) {
  mixedIteration(data);
}
