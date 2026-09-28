# 검토 결과

## 검토 범위

작업 시작 커밋 af895cd 이후: ba2910d, 6c40d7d, 32f9f58, aff5bff, 07ef8ad, 9573ada(1회차 대상), a2dc521(1회차 지적 반영).

## 독립 검증 결과

### 1회차 (2026-09-28, verifier a3957d0491f61d303)

회차 판정: 수정 필요

## 결과 리뷰 1회차: hot-account-baseline

- **모드:** 결과 리뷰 1회차(최초)
- **작업 ID:** hot-account-baseline
- **검증 대상 snapshot:** checks.json `6d9c9d79…d012` (HEAD 9573ada, dirty)
- **판정: 수정 필요.** 중요 2건이 있고, 둘 다 summary.md 문서만 고치면 됩니다. 재측정은 필요 없습니다.
- **확인 방법:** 파일을 읽어서만 확인했습니다. 명령·테스트·verify는 실행하지 않았습니다.

### 읽은 자료
- **작업 문서:** task.md, plan-review.md, progress.md, evidence/checks.json, evidence/changes.txt
- **기준 문서:** docs/testing/{performance,devops,security,backend}.md의 해당 ID 행
- **코드:** perf/k6/s2-hot-account.js, perf/ec2/k6_monitor.py, perf/ec2/loadgen.sh, perf/run-ec2.py(cmd_run·check_artifacts·환경변수), perf/run-ec2.sh, perf/README.md(s2f 절·요약 표), perf/k6/lib/ec2.js(재로그인 주석)
- **결과:**
  - summary.md
  - r1~r7의 analysis.json·integrity.md·gaps.txt·ramp.json
  - environment.md(크레딧 값)
  - r3의 monitor.log·k6.log·k6-summary.json·failed-transfers.txt
  - 전 회차 k6-summary의 check 값
  - dryrun의 monitor.log·analysis.json
  - env/commands.log·destroy.md·environment-target.md

**적용 기준 ID:** BE-04, OPS-01·06·07, SEC-05, PERF-01·02·03·06·07

**확인하지 못한 범위**
- gz 원자료(query_range.json.gz) 내용
- 운영 `envs/dev` plan "No changes" 출력: 결과 폴더와 증거 어디에도 원문이 없습니다.
- 로컬 오프라인 확인(k6 inspect, 가짜 Prometheus 분석): progress.md 기록만 있고 로그가 없습니다. 코드를 읽어 기본값이 그대로인지 대신 확인했습니다.
- verify 실제 실행: checks.json 기록만 봤습니다.

---

### 원자료와 맞는 것
- **회차별 표 7행 전부 일치:** 무너진 요청률·최대 지속, 최대 처리 TPS(단계), 201 확정/추정, 드롭 확정/단계 추정, 체크 실패/실패 키, 전파
- **최대 처리 TPS 재계산:** 구간 50초 이상인 단계만 쓰는 규칙을 적용한 값(r2 97.8@100, r4 112.0@110, r5 87.7@90, r6 109.7@110, r7 90.0@90, r3 140.6@140)이 analysis.json 단계 값과 맞습니다.
- **중앙값·범위(유효 r2~r7):** 무너짐 95(80~140), 최대 지속 85(70~130), 최대 처리 TPS 103.8(87.7~140.6). 계산이 맞습니다.
- **201 차이 0.0~0.8%:** 최대가 r6의 302/37,933 = 0.80%입니다.
- **무너진 단계 지표 범위:**
  - 대상 CPU 44~66%, steal 3.4~7.6%(r3만 1.6%)
  - 락 대기 0.36~1.02초, PG 락 대기 세션 1~9, Hikari 대기 49~123
  - 잔액 조회 p95 시작 전 5.9~10.5ms → 무너짐 때 193~1,115ms
  - 부하 발생기 CPU 4.1~15.6%
- **정합성(BE-04·PERF-06):** 7회 모두 새 불일치 0, 차변 = 대변, 핫 계좌 증가분 = DB 완료 금액, 경계 이후 멱등키 중복 0, 대사 exit=0. 실패 키 반영 수와 중단 잔차 수도 summary 58행과 맞습니다.
- **관측(OPS-06·07, PERF-01):** 전 회차 gaps.txt invalid=no, ramp.json {40,10,400}, check-artifacts missing=none
- **OPS-01:** setup이 `origin/perf/hot-account-baseline`의 32f9f58을 체크아웃했고(commands.log 12~13행), environment-target.md에 커밋과 이미지 digest가 있습니다.
- **REQ-05 정리:** destroy 후 state가 비었고, 태그 조회에 남았던 7건은 직접 조회로 모두 없음이 확인됩니다.
- **REQ-01 기존 s2 불변(코드로 확인):**
  - s2-hot-account.js 기본값이 STEP 20·START 20·MAX 400, 전체 1,260초(21분)로 기존과 같습니다.
  - s2 호출은 추가 값이 비어 있어 ramp.json이 생기지 않으므로 RAMPS["s2"]를 씁니다. 단계 요청률 `start + step·i`는 기존 `step·(i+1)`과 같습니다.
  - `RESULTS`·`GIT_REF` 기본값도 그대로입니다.
- **SEC-05:** gz를 뺀 결과 파일에 8개 패턴을 검색해 적중 0이고, k6-summary 16개 모두 `setup_data: {}`입니다. gz는 확인하지 못했습니다.
- **드라이런:** 40·50 단계가 monitor.log에 있고, analysis.json의 max_success_tps 60@60, 추정 8,900 / 확정 8,990, missing=none입니다.

---

### 지적

**1. [중요] PERF-07·REQ-06 — summary.md에 원자료와 다른 사실 문장 3곳**

- **17행 "r2만 꼬리 구간 값(106.0 @110)이 들어 있다"는 틀렸습니다.**
  - r4 analysis.json도 `"max_success_tps": 119.7, "max_success_tps_stage": 120`입니다(구간 [1790603515, 1790603521], 6초, 718건).
  - 표의 r4 값 112.0은 맞지만, 원자료와 대조하는 사람이 모순을 발견하게 됩니다.
- **34행 "r3의 588건은 150 단계 끝 3초에 몰렸다", 52행 "오류(타임아웃)는 r3의 150 단계에서만"도 틀렸습니다.**
  - 150 단계 구간은 1790602525(13:35:25Z)에 끝납니다.
  - r3 k6.log의 FAILKEY 588건 중 13:35:22~24(단계 안)는 89건, 13:35:25~41(단계가 끝난 뒤 감시의 15초 판정 대기와 중단 순간)은 499건입니다.
  - 단계별 failed가 0인 이유도 "새 시계열 첫 값" 설명만으로는 부족합니다. 실패 대부분이 160 꼬리 구간 [535, 541]에 있는데, 이 구간은 전체 요청 수조차 0으로 잡혔습니다.
- **53행 "CPU 크레딧 76~106"은 실제 범위와 다릅니다.**
  - 실제는 68.6~106.2입니다(r6 73.2~76.4, r7 68.6~71.1).
  - 전 회차 CPUSurplusCreditsCharged가 0이라 "충분했다"는 결론은 그대로 유효합니다.
- 16행의 "k6를 멈춘 뒤 남은 꼬리"는 실제로는 "k6를 멈추기 전까지 돈 다음 단계 앞부분"입니다. 표현 수정을 권합니다.
- **영향:** 개선 전후 비교의 기준 문서라서, r3 오류가 어느 단계에서 났는지와 원자료 대조 결과가 잘못 전달됩니다.
- **필요한 수정:** 위 세 문장 수정. r3 실패는 k6.log 시각 기준으로 "150 단계 안 89건 / 이후 499건"처럼 나눠 적습니다.

**2. [중요] PERF-07·PERF-01·REQ-03·06 — r3의 이체 외 실패와 재로그인 조건 차이가 요약에 없음**

- **근거 1 — r3 k6-summary의 이체 외 실패:**
  - `relogin 200` 성공 315 / 실패 1,418
  - `parallel-balance 200` 실패 78
  - http_req_failed 1,953건 = 457 + 1,418 + 78
  - 요약은 이체 실패(457/588)만 적었습니다. PERF-07의 "누락과 실패를 집계"에 어긋나고, 무관한 잔액 조회가 실제로 실패한 78건은 전파의 직접 근거인데 빠졌습니다.
- **근거 2 — 재로그인 시점:**
  - ec2.js 11~13행에 따르면 토큰 발급 9~12분 뒤 재로그인합니다.
  - s2f는 9분 지점이 120 단계입니다. 그래서 r3(유일하게 약 13분 진행)만 120~150 단계에 재로그인 부하(bcrypt 로그인)가 섞였습니다.
  - `window_stats`의 `scenario="hot_transfer"` 조건에는 같은 시나리오 태그가 붙은 relogin 요청도 들어가므로, 단계 p95·오류율에 포함됩니다.
  - r1·r4·r6의 재로그인은 20~22건으로 꼬리 구간뿐입니다.
- **영향:**
  - 기준선 안에서 r3만 조건이 달라 튀는 값의 해석이 달라집니다. 요약은 steal 상관만 적었습니다.
  - 입금 비동기 반영 뒤 무너짐 지점이 120을 넘으면 개선 후 모든 회차에 재로그인이 섞입니다. 개선 전후 조건이 달라지는 것이며, 이 차이는 명시해야 합니다(PERF-01).
- **필요한 수정:** summary.md에 다음 두 가지를 적습니다. 재측정은 필요 없습니다.
  - r3의 relogin·parallel-balance 실패 수
  - "9분(120 단계) 이후 단계는 재로그인 요청이 단계 통계에 섞인다"는 비교 한계
  - 단계 통계에서 relogin을 빼는 코드 수정(`name!="relogin"`)은 후속 비교 작업에서 정할 선택지로 남기면 됩니다.

**3. [개선] REQ-03 — r1 제외가 중앙값에 주는 영향 병기**
- r1 제외의 근거(예열 후 37분 지연, commands.log 74~78행에서 확인)는 PERF-01 조건 고정 관점에서 타당합니다.
- 다만 REQ-03의 무효 사유 목록(발생기 CPU 초과·결측)에 없는 새 사유입니다.
- r1을 넣으면 무너짐 중앙값 95 → 100, 최대 처리 TPS 103.8 → 109.7로 바뀝니다. 요약에 함께 적고, review.md에 계획 변경으로 기록하길 권합니다.

**4. [개선] 드라이런 커밋이 계획 처리 내역과 다름**
- plan-review 2회차 처리 내역에는 "`<루트>/dryrun/<stamp>`(기존처럼 커밋하지 않음)"이라고 돼 있습니다.
- 커밋한 근거(ec2-baseline/dryrun/20260928-095721이 추적 중이라는 점, changes.txt의 미추적 목록에 없음)는 사실이고, SEC-05 스캔에도 포함돼 영향은 없습니다.
- review.md에 계획과 다른 처리로 적으면 됩니다.

**5. [개선] r4 드롭 위치 불명**
- r4는 k6 드롭 확정 12건인데 단계 추정이 전부 null이라 어느 단계인지 알 수 없습니다.
- ※ 표기 여부를 판단할 수 없다는 점을 한 줄 적어 두면 좋습니다.

---

### 계획과 다르게 처리한 부분의 타당성

| 항목 | 판단 |
| --- | --- |
| r1 참고 회차·r6 대체 | 타당. 절차 조건 차이 근거가 commands.log에 있음. 개선 3 참고 |
| r7 추가 | 타당. r2 90 대 r3 140으로 한 단계 초과, REQ-03 규칙대로 1회 추가, 이후 원인 기록 |
| 드라이런 커밋 | 영향 없음. 계획 기록과 달라 개선 4로 기록 필요 |
| 최대 TPS 재계산 | 규칙과 값 모두 맞음. 다만 "r2만" 문장은 틀림(지적 1) |
| 실패 수를 k6 확정값으로 대체 | 대체 자체는 타당. 실패 위치 설명 오류(지적 1)와 이체 외 실패 누락(지적 2) 수정 필요 |

### 적용 제외 항목
PERF-04·05, OPS-02·03·04·05·08 제외, 백엔드 중 BE-04만 적용한 것은 계획 리뷰 판단과 같게 타당합니다.

### 완료 전 메인이 보존해야 할 근거
- **REQ-05:** 운영 `envs/dev` plan의 "No changes" 원문 출력을 review.md에 남겨야 합니다. 지금은 결과 파일에 없습니다.
- **SEC-05:** gz 포함 스캔 결과(268개 파일, 적중 0)를 review.md에 남겨야 합니다.
- **남은 커밋:** 커밋 계획 6(개발일지)·7(작업 기록)이 아직 안 됐습니다.
- **checks.json:** 명령 6개는 실제 앱 검사(unittest, lint, tsc, jest, gradle test·spotless, build)이고 모두 exit 0입니다. 다만 perf 파이썬·쉘 코드는 이 검사에 포함되지 않으며, 대신 EC2 드라이런과 7회 측정 결과가 실제 동작 근거입니다.

### 이전 지적
결과 리뷰 최초 회차라 없습니다.

### 관련 파일
- /Users/imjaehyeon/Documents/[01] - Project/j-bank/perf/results/ec2-hot-account/summary.md
- /Users/imjaehyeon/Documents/[01] - Project/j-bank/perf/results/ec2-hot-account/s2f/r4/loadgen/analysis.json
- /Users/imjaehyeon/Documents/[01] - Project/j-bank/perf/results/ec2-hot-account/s2f/r3/loadgen/k6.log
- /Users/imjaehyeon/Documents/[01] - Project/j-bank/perf/results/ec2-hot-account/s2f/r3/loadgen/k6-summary.json
- /Users/imjaehyeon/Documents/[01] - Project/j-bank/perf/results/ec2-hot-account/s2f/r6/environment.md
- /Users/imjaehyeon/Documents/[01] - Project/j-bank/perf/results/ec2-hot-account/s2f/r7/environment.md
- /Users/imjaehyeon/Documents/[01] - Project/j-bank/perf/k6/lib/ec2.js
- /Users/imjaehyeon/Documents/[01] - Project/j-bank/perf/ec2/k6_monitor.py
- /Users/imjaehyeon/Documents/[01] - Project/j-bank/.claude/tasks/hot-account-baseline/plan-review.md

(원문 마지막 줄) 최종 판정 문구: 수정 필요

### 1회차 처리 내역

- 중요 1: summary.md 수정(a2dc521). 꼬리 구간 설명을 "감시가 멈추기 전까지 돈 다음 단계 앞부분"으로, r2·r4 두 회차에 짧은 구간 값이 있음을 명시. r3 실패 키 k6.log 시각 대조 결과 150 단계 안 89건 / 이후 499건으로 확인해 기재. 크레딧 범위 68.6~106.2로 정정(원자료 재계산), CPUSurplusCreditsCharged 전 회차 0 확인.
- 중요 2: r3 이체 외 실패(parallel-balance 78, relogin 성공 315·실패 1,418, http_req_failed 1,953 = 457+1,418+78)를 k6-summary로 확인해 기재. 재로그인 9분(120 단계) 이후 섞임과 개선 후 비교 한계, 선택지(`name!="relogin"` 제외·재로그인 시점 조정)를 기재.
- 개선 3: r1 포함 7회 중앙값(무너짐 100, 최대 처리 TPS 109.7) 병기, 계획 변경으로 아래 기록.
- 개선 4: 드라이런 커밋을 계획과 다른 처리로 아래 기록.
- 개선 5: r4 드롭 위치 불명 한 줄 추가.
- 근거 보존 요청: 운영 plan 원문과 SEC-05 결과를 아래 "완료 기준별 근거"에 기록.

## 계획과 다르게 처리한 부분

- r1 참고 회차: 로컬 맥 수면으로 예열 후 약 37분 뒤 측정 시작(commands.log). 명세 무효 사유(발생기 CPU·결측)에 없는 절차 조건 차이라 대표값에서 빼고 r6로 대체. r1 포함 값도 요약에 병기.
- r7: REQ-03 변동폭 규칙(r2 90 대 r3 140)으로 1회 추가. 이후에도 폭이 한 단계를 넘어 원인 기록.
- 드라이런 폴더 커밋: 계획 리뷰 2회차 처리 내역의 "비커밋"과 다름. 기준선 작업의 드라이런 스탬프 폴더도 추적 중이고 s2f 사전 검증 근거라 커밋. 사용자가 커밋하지 말라고 한 ec2-baseline/dryrun 루트 미추적 파일은 그대로 둠.
- 최대 처리 TPS: 측정 중 결함 발견(aff5bff로 코드 수정). 인스턴스가 32f9f58로 돌아 요약에서 단계 값에 같은 규칙을 적용해 재계산.
- 단계별 실패 수: Prometheus 한계로 k6 확정값으로 대체.

### 미등록 참고 응답 (1회차 verifier를 SendMessage로 재호출, 하네스 호출 기록 없음)

참고 응답 snapshot: 08cef578e3b499c6fca816c5491ca1a7111fc68a8968930adeb219100dd6a220
참고 응답 판정: 통과 권고

## 결과 리뷰 2회차(재리뷰 1회차): hot-account-baseline

- **모드:** 결과 리뷰, 재리뷰 1회차
- **작업 ID:** hot-account-baseline
- **검증 대상 snapshot:** checks.json `08cef578…a6220` (HEAD a2dc521, verify-002 통과)
- **판정: 통과 권고.** 1회차 지적 1~5가 원자료 기준으로 모두 해소됐습니다. 새 차단·중요 지적은 없습니다.
- **확인 방법:** 파일을 읽어서만 확인했습니다. 명령·테스트·verify는 실행하지 않았습니다.

### 읽은 자료
- **작업 문서:** summary.md(a2dc521판), review.md, evidence/checks.json(snapshot·head·passed)
- **원자료 재대조:**
  - r1~r7 k6-summary의 check 값(relogin·parallel-balance)
  - r3 k6.log의 FAILKEY 시각
  - r1~r7 environment.md의 크레딧·초과 과금 값
  - r2·r4 analysis.json
  - r1~r7 batch-*-recon.log(COMPLETED)
  - run-ec2.py의 write_integrity·cmd_down 위치

**적용 기준 ID:** BE-04, OPS-01·06·07, SEC-05, PERF-01·02·03·06·07

**확인하지 못한 범위**
- gz 원자료 내용. review.md의 SEC-05 결과(gz 포함 268개 파일 적중 0)는 메인 기록으로만 봤습니다.
- 운영 plan 실행 사실. review.md에 명령·exit=0·마지막 줄이 기록된 것만 확인했습니다.
- 로컬 k6 inspect 로그. 1회차처럼 progress 기록과 코드 읽기로 대신했습니다.

### 이전 지적별 처리

| 지적 | 결과 | 원자료 대조 근거 |
| --- | --- | --- |
| 1 [중요] 사실 오류 | 해결 | 아래 참고 |
| 2 [중요] r3 이체 외 실패·재로그인 섞임 | 해결 | 아래 참고 |
| 3 [개선] r1 포함 중앙값 병기 | 해결 | 48행 "7회 기준 무너짐 중앙값 100, 최대 처리 TPS 109.7"은 7개 값으로 다시 계산해도 맞습니다. review.md "계획과 다르게 처리한 부분"에도 기록돼 있습니다. |
| 4 [개선] 드라이런 커밋 | 해결 | review.md에 계획과 다른 처리로 이유와 함께 기록돼 있습니다. |
| 5 [개선] r4 드롭 위치 | 해결 | 29행에 "단계 추정이 모두 null이라 ※ 미표기"가 있습니다. |

**지적 1 확인 내용**
- 17행: r2(106.0@110)·r4(119.7@120) 두 회차를 명시했고, analysis.json 값과 같습니다.
- 16행: 꼬리 구간 표현이 "감시가 k6를 멈추기 전까지 돈 다음 단계 앞부분"으로 바뀌었습니다.
- 33~35행: FAILKEY 시각 기준 89건(13:35:22~24, 150 단계 안)과 499건(13:35:25~41)이 k6.log와 맞습니다.
- 57행: "150 단계 끝과 그 뒤 중단 전 구간"으로 고쳐졌습니다.
- 58행: 크레딧 68.6~106.2(r7 최저 68.64, r1 최고 106.19)와 CPUSurplusCreditsCharged 전 회차 0이 environment.md와 맞습니다.

**지적 2 확인 내용**
- 36~37행 r3 수치가 k6-summary와 맞습니다: parallel-balance 실패 78, relogin 성공 315·실패 1,418, http_req_failed 1,953 = 457 + 1,418 + 78.
- "다른 회차 재로그인 0~22건"도 맞습니다(r1 21, r4 20, r6 22, r2·r5·r7은 재로그인 체크 없음). 이체 외 실패도 r3 외에는 0입니다.
- 59행 재로그인 섞임(9~12분, 120 단계, 시나리오 태그 `hot_transfer`)과 78행 비교 한계·선택지가 들어갔습니다.

### 회귀 확인
- 회차별 표, 분산 요약(95/85/103.8), 정합성, 기존 S2 관계 표의 수치는 1회차에 확인한 값에서 바뀌지 않았습니다.
- 새로 넣은 문장에서 원자료와 모순되는 곳은 찾지 못했습니다.

### review.md 요구사항별 표 대조

| REQ | 판단 |
| --- | --- |
| REQ-01 | 증거 일치. ramp.json {40,10,400}, monitor.log 단계 40·50…, 결과는 ec2-hot-account 아래에만 생성. inspect는 progress 기록이며, 코드로 기본값 불변을 1회차에 확인함 |
| REQ-02 | 일치. 요약에서 재계산했다는 점도 명시됨 |
| REQ-03 | 일치 |
| REQ-04 | 일치. write_integrity(run-ec2.py:412), integrity.md 7개, recon 로그 7개 모두 COMPLETED |
| REQ-05 | 일치. destroy.md, cmd_down(run-ec2.py:499), 운영 plan 기록 |
| REQ-06 | 일치. summary.md, README의 s2f 절(426~441행)과 결과 표 |

과장된 판정은 없습니다.

### 새 지적
**[개선] summary.md 34~35행의 원인 설명은 추정으로 표기 권장.** "150 단계의 89건은 새 시계열 첫 값을 `increase()`가 세지 못해 빠졌다"는 gz 원자료로 확인하지 않은 설명입니다. "(추정)" 정도로 표기하면 충분하며, 완료를 막지 않습니다.

### 완료 전 남은 일
커밋 계획 6(개발일지)·7(작업 기록)은 아직입니다. 이번 판정 범위 밖이며, 메인이 complete 전에 처리할 일입니다.

### 관련 파일
- /Users/imjaehyeon/Documents/[01] - Project/j-bank/perf/results/ec2-hot-account/summary.md
- /Users/imjaehyeon/Documents/[01] - Project/j-bank/.claude/tasks/hot-account-baseline/review.md
- /Users/imjaehyeon/Documents/[01] - Project/j-bank/.claude/tasks/hot-account-baseline/evidence/checks.json

(원문 마지막 줄) 최종 판정 문구: 통과 권고

### 참고 응답 처리 내역

- 새 개선(summary.md 34~35행 원인 설명을 추정으로 표기): 완료를 막지 않는 개선이라 이번 작업에서 문서를 다시 바꾸지 않고(재검사·재리뷰 필요) 입금 비동기 비교 작업에서 요약을 갱신할 때 반영할 후속 사항으로 남긴다.

### 2회차 (2026-09-28, verifier a0270cceefc6b5856, 재리뷰 1회차, 공식)

대상 snapshot: 08cef578e3b499c6fca816c5491ca1a7111fc68a8968930adeb219100dd6a220
최종 판정: 통과 권고

## 결과 리뷰 2회차(재리뷰 1회차): hot-account-baseline

- **모드:** 결과 리뷰, 재리뷰 1회차(하네스 기준 2회차)
- **작업 ID:** hot-account-baseline
- **검증 대상:** checks.json snapshot `08cef578…a220`, HEAD a2dc521, passed=true
- **확인 방법:** 파일을 읽어서만 확인했습니다. 명령·테스트·verify·terraform은 실행하지 않았습니다.
- **판정: 통과 권고.** 1회차 중요 1·2와 개선 3~5는 원자료 기준으로 해소됐습니다. 새로 찾은 것은 개선 3건이며, 차단·중요 지적은 없습니다.

### 읽은 자료
- **작업 문서:** task.md, progress.md, review.md(전체), evidence/checks.json, evidence/changes.txt
- **기준:** docs/testing/{performance,devops,security,backend}.md의 BE-04, OPS-01·06·07, SEC-05, PERF-01~07 행
- **요약:** perf/results/ec2-hot-account/summary.md(a2dc521판. 이후 작업 트리 변경은 .claude/tasks와 ec2-baseline/dryrun 미추적 파일뿐)
- **원자료:**
  - r1~r7 analysis.json 요약 필드 전체
  - r3 analysis.json 전 단계, monitor.log, k6.log의 FAILKEY 시각별 건수, k6-summary.json, integrity.md
  - r1~r7 loadgen k6-summary의 relogin·parallel-balance 체크 값
  - r1~r7 environment.md의 CPUCreditBalance·CPUSurplusCreditsCharged 값
  - env/destroy.md, env/loadgen/accounts.json(비밀값 패턴 검색)

### 확인하지 못한 범위
- **gz 원자료(query_range.json.gz):** 34행의 "새 시계열 첫 값" 원인이 맞는지 확인하지 못했습니다. SEC-05의 gz 스캔도 메인 기록으로만 봤습니다.
- **운영 `envs/dev` plan "No changes":** review.md 273행의 메인 기록만 있고, 결과 폴더에는 원문 출력이 없습니다.
- **로컬 k6 inspect·가짜 Prometheus 확인:** progress.md 7행 기록만 있고 로그가 없습니다. 1회차에 코드를 읽어 기본값 불변을 대신 확인했습니다.
- **verify 실제 실행:** checks.json 기록만 봤습니다. 명령 6개는 실제 앱 검사이고 모두 exit 0입니다. perf 파이썬·쉘 코드는 이 검사 범위 밖입니다.

---

### 이전 지적별 처리

| 지적 | 결과 | 원자료 대조 근거 |
| --- | --- | --- |
| 1 [중요] 요약 사실 오류 3곳 | 해결 | 아래 참고 |
| 2 [중요] r3 이체 외 실패·재로그인 섞임 누락 | 해결 | 아래 참고 |
| 3 [개선] r1 포함 중앙값 병기 | 해결 | 48행. 7개 값으로 다시 계산해도 무너짐 중앙값 100(80,80,90,100,100,100,140), 최대 처리 TPS 중앙값 109.7로 맞습니다. review.md 167행에 계획 변경으로 기록돼 있습니다. |
| 4 [개선] 드라이런 커밋 | 해결 | review.md 169행에 계획과 다른 처리로 기록돼 있습니다. |
| 5 [개선] r4 드롭 위치 | 해결 | 29행. r4 dropped_stage_est가 전부 null이고 k6_dropped_total이 12인 것과 맞습니다. |

**지적 1 확인 내용**
- **17행:** analysis.json 값 r2 `106.0 @110`, r4 `119.7 @120`과 같습니다.
- **16행:** "감시가 k6를 멈추기 전까지 돈 다음 단계 앞부분"으로 바뀌었습니다.
- **33~34행 r3 실패 위치:** k6.log의 FAILKEY는 모두 588건이고 상태는 전부 0입니다.
  - 13:35:22~24에 89건이 있습니다. 150 단계 구간 [1790602475, 1790602525), 즉 13:35:25 전이라 단계 안이 맞습니다.
  - 13:35:25~41에 499건이 있습니다.
  - monitor.log 13행 "무너짐 단계 10 이후 한 단계 추가 완료 → k6 중단"과 맞습니다.
- **57행:** "150 단계 끝과 그 뒤 중단 전 구간"으로 고쳐졌습니다.
- **58행 크레딧:** 최저는 r7 68.64, 최고는 r1 106.19로 68.6~106.2가 맞습니다. 7개 회차 모두 CPUSurplusCreditsCharged가 0입니다.

**지적 2 확인 내용**
- **36~37행 r3 수치:** k6-summary와 모두 맞습니다.
  - parallel-balance: 성공 15,361, 실패 78
  - relogin: 성공 315, 실패 1,418
  - hot-transfer: 실패 457
  - http_req_failed passes 1,953 = 457 + 1,418 + 78
- **다른 회차:** relogin 체크는 r1 21, r4 20, r6 22건이고 r2·r5·r7에는 없습니다. 이체 외 실패는 0건입니다. "재로그인 0~22건"과 맞습니다.
- **59행 재로그인 시점:** 이체 램프가 부하 시작 60초 뒤에 시작하므로 120 단계는 540초(9분)에 시작합니다. relogin_after_minutes 범위 9.0~12.0과 맞습니다.
- **r3 섞임 정도:** 120·130·140 단계 요청 수가 목표보다 33·28·44건 많습니다. 섞인 재로그인이 1% 미만이라 "무너짐 지점을 설명하지 않는다"는 해석과 모순되지 않습니다.
- **78행:** 비교 한계와 선택지가 들어갔습니다.

### 회귀 확인
- **회차별 표 7행:** 무너짐·최대 지속·201 확정/추정·드롭 확정/단계 추정·전파 값이 analysis.json·k6-summary와 모두 일치합니다.
- **분산 요약:** 유효 6회 기준 중앙값이 무너짐 95, 최대 지속 85, 최대 처리 TPS 103.8로 재계산 결과와 같습니다.
- **201 차이:** 최대가 r6의 302/37,933 = 0.80%라 0.0~0.8%가 맞습니다.
- **r3 정합성(64행):** integrity.md의 실패 키 반영 373, 하한 242, 잔차 0과 맞습니다.
- **destroy:** env/destroy.md에 state가 비어 있고, 남았던 7건은 직접 조회로 모두 "없음"입니다.
- **비밀값:** env/ 아래에 pass·secret·token 검색 적중이 0건입니다. accounts.json에는 loginId·계좌 번호만 있습니다.
- **기존 기준선:** changes.txt에 ec2-baseline 아래 추적 파일 변경이 없습니다. 기존 기준선 결과가 바뀌지 않았습니다.

---

### 새 지적 (모두 개선, 완료를 막지 않음)

**A. [개선] PERF-07·REQ-06 — summary.md 35행 "나머지는 … 짧은 뒷구간(160)에 있다"는 일부가 틀렸습니다.**
- **근거:** r3 k6.log의 FAILKEY 499건을 시각별로 나누면 다음과 같습니다.
  - 13:35:25~34에 240건: 160 단계의 제외 구간(시작 10초)이라 어느 분석 구간에도 들어가지 않습니다.
  - 13:35:35~41에 259건: 160 단계 분석 구간 [1790602535, 1790602541]에 들어갑니다.
- 1회차 지적의 "실패 대부분이 160 꼬리 구간에" 문장도 같은 오류를 담고 있었습니다(259/588 = 44%).
- **영향:** 표·중앙값·결론(실패 수는 k6 확정값 사용)은 바뀌지 않습니다. 다만 analysis.json의 `failed=0`이 왜 생겼는지에 대한 설명이 부정확합니다.
- **권장:** 다음에 요약을 고칠 때 "240건은 160 단계 제외 구간, 259건은 160 분석 구간"으로 바로잡습니다. 이미 미룬 "(추정)" 표기와 함께 처리하면 됩니다.

**B. [개선] 57행 "무너진 뒤에도 성공 처리율은 목표 요청률을 거의 따라갔다"에 r3 예외가 빠져 있습니다.**
- **근거:** r3는 150 단계 success_tps가 113.5로 목표 150의 76%이고, 드롭 추정이 873건입니다.
- 같은 문단에 "이체 오류는 r3에서만"이 있어 부분적으로는 드러나지만, 문장 자체는 r3에 맞지 않습니다.
- **권장:** "(r3 150 단계 제외: 113.5, 드롭 추정 873)" 정도로 예외를 병기합니다.

**C. [개선] review.md 기록 정리**
- **"2회차" 절 이름:** 173~253행의 "2회차"는 하네스에 등록되지 않은 응답입니다. 이번 응답이 공식 2회차이므로, 이전 절은 "미등록 참고 응답"으로 이름을 바꾸고 이번 응답을 2회차로 보존하길 권합니다.
- **판정 문장 근거:** 287행 "판정과 이유"도 이번 응답을 근거로 다시 적어야 합니다.
- **REQ-01 증거 표기:** 263행의 "로컬 k6 inspect"는 저장된 로그가 없는 실행 기록입니다. "확인하지 못한 부분"(291행)에 "inspect·가짜 Prometheus 확인은 progress 기록만 있고 로그는 보존하지 않음"을 추가하면 증거 성격이 정확해집니다.

**기존에 미룬 개선(34행 원인을 "추정"으로 표기)** 은 review.md 257·291행에 후속 사항과 미확인으로 남아 있습니다. 완료를 막지 않는다는 판단에 동의합니다.

---

### review.md 요구사항별 표 대조

| REQ | 판단 |
| --- | --- |
| REQ-01 | 일치. ramp.json {40,10,400}과 monitor.log 단계 40·50…150이 확인되고, 결과는 ec2-hot-account 아래에만 있습니다. inspect는 로그 없는 기록입니다(개선 C). |
| REQ-02 | 일치. analysis.json에 success_tps, max_success_tps, success_total_est, k6_hot_201_passes, k6_dropped_total, propagation_at_collapse가 있습니다. 최대 TPS를 요약에서 다시 계산했다는 점도 적혀 있습니다. |
| REQ-03 | 일치. 유효 6회와 참고 r1이 있고, r7 추가 근거와 변동폭 원인이 적혀 있습니다. 원인은 단정하지 못했고 관측된 상관만 있습니다. 40 단계에서 무너진 회차는 없습니다. |
| REQ-04 | 일치. r3 integrity.md를 대조했고, 나머지 회차는 1회차 확인 값이 그대로입니다. |
| REQ-05 | 일치. destroy.md는 확인했습니다. 운영 plan은 메인 기록만 있습니다. |
| REQ-06 | 일치. 요구 항목이 모두 있습니다. 문장 정확도 개선 A·B가 남아 있습니다. |

과장된 판정은 없습니다.

### 적용 제외 항목
PERF-04·05, OPS-02·03·04·05·08을 제외하고 백엔드에서 BE-04만 적용한 것은 계획과 1회차 판단과 같게 타당합니다.

### 완료 전 메인이 할 일
- 커밋 계획 6(개발일지)과 7(작업 기록)이 아직 남아 있습니다.
- 이번 응답을 review.md에 공식 2회차로 보존합니다(개선 C).

### 관련 파일
- /Users/imjaehyeon/Documents/[01] - Project/j-bank/perf/results/ec2-hot-account/summary.md (35행, 57행)
- /Users/imjaehyeon/Documents/[01] - Project/j-bank/perf/results/ec2-hot-account/s2f/r3/loadgen/k6.log
- /Users/imjaehyeon/Documents/[01] - Project/j-bank/perf/results/ec2-hot-account/s2f/r3/loadgen/analysis.json
- /Users/imjaehyeon/Documents/[01] - Project/j-bank/.claude/tasks/hot-account-baseline/review.md (173~253행, 263행, 287행, 291행)

(원문 마지막 줄) 최종 판정 문구: 통과 권고

### 2회차 처리 내역

- 개선 A(r3 실패 499건 중 240건은 160 단계 제외 구간, 259건은 160 분석 구간)·B(57행 r3 150 단계 예외 113.5·드롭 873 병기): 표·중앙값·결론은 그대로이고 문장 정확도 개선이라, 지금 고치면 재검사·재리뷰가 필요하다. 참고 응답의 "(추정)" 표기와 함께 입금 비동기 비교 작업에서 요약을 갱신할 때 반영할 후속 사항으로 남긴다.
- 개선 C: 이 문서에 반영(이전 응답을 미등록 참고 응답으로 이름 변경, 이 응답을 공식 2회차로 보존, 판정 근거·미확인 범위 갱신).

## 요구사항별 검증

| 요구사항 ID | 구현 위치 | 테스트·실행 증거 | 판정 |
| --- | --- | --- | --- |
| REQ-01 | perf/k6/s2-hot-account.js(S2_START·STEP·MAX), perf/run-ec2.py(s2f·PERF_RESULTS·PERF_GIT_REF), perf/run-ec2.sh | 로컬 k6 inspect(기본 20·20단계·21m, s2f 40·37단계·38m), s2f/r1~r7 ramp.json {40,10,400}, monitor.log 단계 40·50…, 결과가 perf/results/ec2-hot-account/ 아래에만 생성 | 통과 |
| REQ-02 | perf/ec2/k6_monitor.py(ramp·stage_windows·evaluate_stage·analyze), perf/ec2/loadgen.sh(ramp.json) | s2f/r*/loadgen/analysis.json(success_tps·max_success_tps·k6_hot_201_passes·k6_dropped_total·propagation), 드라이런 analysis.json. 최대 TPS는 aff5bff 규칙으로 요약 재계산 | 통과 |
| REQ-03 | 측정 절차·summary.md | 유효 6회(r2~r7)+참고 r1, 회차별 무너짐·최대 지속·최대 TPS·드롭(확정·추정)·실패(확정), 중앙값·범위 | 통과 |
| REQ-04 | perf/run-ec2.py(write_integrity), perf/ec2/target.sh | s2f/r1~r7/integrity.md: 새 불일치 0, 차변=대변, 핫 계좌 증가분 = DB 완료 금액, 실패 키 대조, 대사 COMPLETED | 통과 |
| REQ-05 | perf/run-ec2.py(cmd_down) | env/destroy.md(state 비어 있음, 태그 잔여 7건 직접 조회 없음), 운영 plan No changes(아래), SEC-05 적중 0 | 통과 |
| REQ-06 | perf/results/ec2-hot-account/summary.md, perf/README.md(핫 계좌 정밀 측정 절·결과 표) | 조건·회차별 표·분산·드롭 해석·기존 S2 관계·재로그인 비교 한계 | 통과 |

## 완료 기준별 근거

- verify: 통과(evidence/checks.json).
- 운영 plan: `terraform -chdir=infra/terraform/envs/dev plan -detailed-exitcode` exit=0, 출력 마지막 줄 "found no differences, so no changes are needed." (2026-09-28, a2dc521 시점).
- SEC-05: `perf/results/ec2-hot-account/` 전 파일 268개(gz 포함 압축 해제 후)에 `access_token=`, `eyJhbGci`, `DB_PASSWORD=`, `JWT_SECRET=`, `PII_ENCRYPTION_KEY=`, `METRICS_PASSWORD=`, `GRAFANA_ADMIN_PASSWORD=`, `"password":"…"` 패턴 적중 0, k6-summary setup_data 모두 비어 있음.
- perf 환경 destroy, 잔여 0: env/destroy.md.

## 성능 테스트 확인

필요(작업 본체). 조건·결과는 perf/results/ec2-hot-account/summary.md. 기준선 측정이라 통과 목표 수치 없음. 기존 S2 기준선과 간격·시작 방식·후처리 순서 차이를 명시.

## 발견한 문제

1회차 지적 1·2(요약 사실 오류, r3 이체 외 실패·재로그인 섞임 누락) — a2dc521로 수정.

## 판정과 이유

1회차 수정 필요(요약 사실 오류·r3 이체 외 실패 누락) → a2dc521 수정·verify 재통과 → 2회차(공식, a0270cceefc6b5856) 통과 권고. 필수 기준 충족, 미해결 차단·중요 지적 없음. 남은 개선(요약 문장 A·B·추정 표기)은 후속 사항.

## 확인하지 못한 부분

gz 원자료는 메인의 SEC-05 스캔만 수행(verifier 미확인). summary.md의 r3 150 단계 89건 누락 원인(새 시계열 첫 값)은 gz로 확인하지 않은 추정이다. 로컬 k6 inspect·가짜 Prometheus 분석 확인은 progress.md 기록만 있고 출력 로그는 보존하지 않았다.
