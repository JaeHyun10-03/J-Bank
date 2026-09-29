# 계획 리뷰

## 1회차

회차 계획 지문: be0e435e788166c75791502cea74ebe18c01591890cc448ffe27d2060b523f00
회차 판정: 수정 필요

### verifier 응답 원문

hot-account-baseline 계획 1회차 검증 결과는 "수정 필요"입니다. 파일을 읽어서만 확인했고, 명령이나 테스트는 실행하지 않았습니다. 전체 보고서는 메인에게 넘겼습니다.

task.md의 조사 사실은 기존 결과와 맞습니다. 기존 S2 3회는 무너짐 80/100/80, 첫 측정 중 최대 지속 최솟값 40이 확인됩니다. 핫 계좌 성공 이체 수도 Prometheus에서 조회할 수 있습니다. 시작 요청률 40은 사용자 결정이 아니라 측정 설계로 봐도 된다고 판단했습니다.

**중요 지적 (6건)**

1. **EC2에 새 코드가 올라가지 않습니다.** `perf/run-ec2.py:34`의 `GIT_REF`가 `perf/ec2-load-test`로 고정돼 있어, setup이 두 인스턴스를 옛 브랜치로 체크아웃합니다. 이대로면 s2f 감시 프로그램이 오류로 멈추고 k6가 400까지 약 38분을 계속 돕니다. 브랜치를 바꾸고 push하는 절차가 계획에 필요합니다.
2. **`perf/run-ec2.sh s2f`를 실행할 수 없습니다.** `run-ec2.sh:10`은 s1·s2·s3·s5만 받습니다. `run-ec2.py:516`의 선택지에도 s2f가 없습니다.
3. **결과 위치 변경이 기존 경로 처리와 맞지 않습니다.**
   - 결과 폴더가 코드에 `ec2-baseline`으로 고정돼 있습니다. 그래서 실행 기록, 인프라 기록, 대상 환경 파일이 모두 옛 위치에 쌓입니다. OPS-01 증거 경로와 다릅니다.
   - 회차 폴더는 그대로면 `s2f/r1`이 되어 요구사항의 `r<회차>`와 다릅니다.
   - 폴더 구조를 바꾸면 environment.md의 `../../env` 링크가 틀어집니다.
4. **s2f 경로를 측정 전에 확인하는 테스트가 없습니다.** 드라이런은 s1 경로만 타므로, 새 코드는 본 측정 1회차에서 처음 실행됩니다. 짧은 s2f 드라이런을 넣거나, 1회차 분석이 실패할 때 어떻게 처리할지 규칙을 정해야 합니다.
5. **로컬 `k6 inspect` 테스트가 그대로는 실패할 가능성이 큽니다.** `perf/k6/lib/ec2.js:25`가 시작할 때 계정 파일을 엽니다. 임시 파일과 환경변수를 넘기지 않으면 오류가 납니다.
6. **요청 수 집계 기준이 빠졌습니다.** PERF-07은 회차별 발생·완료·실패·드롭 수 집계를 요구합니다. 드롭이 있으면 제공 부하가 목표보다 낮아 최대 처리 TPS 해석이 달라집니다.

**개선 제안 (6건)**
- 기존 "한 단계 넘게 차이 나면 1회 추가" 규칙을 간격 10에도 적용할지 명시하기
- 감시 프로그램(`watch()`)의 단계 요청률 표기, s2 전용 분기, 결과물 확인 조건에 s2f 포함하기
- k6 스크립트의 전체 시간 계산에 시작 요청률 반영하기
- 성공 건수는 상태 코드 201로 좁혀 세고, k6 요약의 201 건수와 교차 확인하기
- 요약 문서에 기존 S2와 시작 방식이 다르다는 점(20에서 점진 증가 vs 40에서 시작) 적기
- 비밀값 검사 명령을 구체적으로 적고, `perf/results/ec2-baseline/dryrun/` 아래 추적되지 않은 파일의 커밋 여부 정하기

**적용 제외 판단**
- PERF-04·05 제외와 백엔드 중 BE-04만 적용한 것은 타당합니다.
- PERF-02를 목표 수치 없이 무너짐 기준으로 판정하는 것은 기준선 측정이라 받아들일 수 있습니다.
- 지적 1·3으로 실행 스크립트 설정이 바뀌므로 OPS-01 증거 경로는 다시 맞춰야 합니다.

**확인하지 못한 범위:** ec2-load-test-followup 본문, 로컬 k6 설치 여부, origin 브랜치 상태.

관련 파일:
- /Users/imjaehyeon/Documents/[01] - Project/j-bank/.claude/tasks/hot-account-baseline/task.md
- /Users/imjaehyeon/Documents/[01] - Project/j-bank/perf/run-ec2.py
- /Users/imjaehyeon/Documents/[01] - Project/j-bank/perf/run-ec2.sh
- /Users/imjaehyeon/Documents/[01] - Project/j-bank/perf/ec2/k6_monitor.py
- /Users/imjaehyeon/Documents/[01] - Project/j-bank/perf/k6/s2-hot-account.js
- /Users/imjaehyeon/Documents/[01] - Project/j-bank/perf/k6/lib/ec2.js

(원문 마지막 줄) 최종 판정 문구: 수정 필요

### 처리 내역

- 중요 1(원격 코드): `PERF_GIT_REF` 환경변수, 브랜치 push 후 setup, 체크아웃 커밋은 environment-target.md로 확인(OPS-01).
- 중요 2(run-ec2.sh·argparse): 범위·커밋 3에 s2f와 `s2f --dry-run` 추가.
- 중요 3(결과 위치): `PERF_RESULTS` 환경변수로 루트 전체(env·commands.log·회차)를 옮기고 회차 구조 `<루트>/s2f/r<n>/` 유지(상대 링크 유지).
- 중요 4(사전 검증): 상한 60의 `s2f --dry-run`을 측정 전 단계로 추가.
- 중요 5(inspect): 임시 계정 JSON과 `-e ACCOUNTS_FILE` 명시.
- 중요 6(PERF-07): 회차·단계별 드롭(확정·추정)·실패 수 기록, 드롭 단계 성공 처리율 해석 규칙 추가.
- 개선 7~12: 변동폭 규칙은 한 단계(10) 초과 시 추가 1회, 발생기 유형 상향은 Terraform 변수라 코드 변경 아님; watch·전파·check_artifacts s2f 반영; 병행 조회 길이 (MAX-START)/STEP+1 단계; 성공=status 201과 k6-summary 교차; 시작 40 부하 이력 차이 요약 기재; SEC-05 패턴 구체화, dryrun 미추적 파일 비커밋.

## 2회차

대상 계획 지문: ade9584ee502d0d35380d1fbe7cc0b8e7e9d7796d567b1a436dce2666576fe6f
최종 판정: 통과 권고

### verifier 응답 원문

## 계획 리뷰 2회차(재리뷰 1회차): hot-account-baseline

모드는 계획 리뷰, 대상은 `.claude/tasks/hot-account-baseline/task.md`의 1회차 지적 반영판입니다. 1회차 계획 지문은 be0e435e…였고, 이번 판의 지문은 따로 계산하지 않았습니다.

**읽은 자료**
- 작업 문서: task.md, plan-review.md, `.claude/tasks/ec2-load-test/task.md`(반복·변동 규칙 대조)
- 코드: perf/run-ec2.py 전체, perf/run-ec2.sh, perf/ec2/k6_monitor.py 전체, perf/ec2/loadgen.sh(k6·analyze·export 단계), perf/k6/s2-hot-account.js, perf/k6/lib/ec2.js 앞부분
- 기준 정의: docs/testing/{performance,devops,security,backend}.md의 해당 ID 행

**적용 기준 ID:** BE-04, OPS-01·06·07, SEC-05, PERF-01·02·03·06·07

**확인하지 못한 범위:** 파일 읽기로만 확인했고 명령은 실행하지 않았습니다. 그래서 다음은 확인하지 못했습니다.
- origin 브랜치 상태
- 로컬 k6 버전과 네트워크 접근: ec2.js가 원격 jslib를 import하므로 inspect할 때 네트워크가 필요합니다.
- ec2-load-test-followup 본문

**판정: 통과 권고.** 남은 것은 개선 제안뿐입니다.

## 이전 지적별 처리

| 번호 | 지적 | 결과 | 확인 근거 |
| --- | --- | --- | --- |
| 중요 1 | 원격 브랜치 고정 | 해결 | 조사한 사실에 run-ec2.py:34의 `GIT_REF`와 run-ec2.py:206-210 setup 동작이 정확히 적혀 있습니다. 기술 선택에 `PERF_GIT_REF`와 "push 후 실행" 절차가 있습니다. OPS-01은 target.sh:55의 "저장소 커밋" 기록(environment-target.md)으로 확인하므로 근거가 실재합니다. |
| 중요 2 | s2f 실행 불가 | 해결 | 범위, 커밋 3, 오케스트레이터 테스트 행에 run-ec2.sh case, argparse choices, `s2f --dry-run` 인자 전달이 모두 들어 있습니다. |
| 중요 3 | 결과 위치 | 해결 | `PERF_RESULTS` 하나로 log()·record_infra·fetch_env·cmd_down·cmd_run의 `RESULTS`를 함께 옮깁니다. 회차 구조 `<루트>/s2f/r<n>/`를 유지하므로 write_environment의 `../../env/` 링크도 맞습니다. OPS-01 증거 경로도 새 루트로 바뀌었습니다. |
| 중요 4 | s2f 사전 검증 없음 | 해결 | 상한 60(40·50·60) 드라이런이 측정 전 단계와 일반 테스트 표에 들어갔고, 기준선에서 뺀다고 명시돼 있습니다. |
| 중요 5 | 로컬 inspect 실패 | 해결 | 임시 계정 JSON과 `-e ACCOUNTS_FILE`, `-e BASE_URL`이 명시돼 있습니다. 계획에 영향 없는 세부는 아래 개선 3에 적었습니다. |
| 중요 6 | 요청 수 집계 | 해결 | 다음이 REQ-02·REQ-03·PERF-07에 기록됩니다: 단계별 성공 처리율·실패 수·드롭 추정, 회차 확정 드롭 수, k6-summary의 "hot-transfer 201" passes, 드롭 단계의 해석 규칙. 근거로 쓰는 `k6_check(..., "hot-transfer 201")`는 run-ec2.py:408에 있고, check 이름은 ec2.js:125에서 만들어지므로 실재합니다. |
| 개선 7 | 간격 10의 변동폭 규칙 | 해결 | REQ-03에 "한 단계(10) 초과 시 1회 추가"가 있고, 기존 ec2-load-test 반복·변동 규칙과 같은 구조입니다. |
| 개선 8 | watch 표기·s2 분기·결과물 확인 | 해결 | 범위와 "분석 단계·표기" 테스트에 반영돼 있습니다. |
| 개선 9 | 병행 조회 길이 | 해결 | (MAX−START)/STEP+1 = 37단계, 60+37×60초입니다. 기본값으로 계산하면 20단계, 21분으로 기존과 같습니다. |
| 개선 10 | 성공을 201로 좁히고 교차 확인 | 해결 | status="201" 라벨로 세고 k6-summary passes와 교차 확인합니다. |
| 개선 11 | 시작 방식 차이 | 해결 | REQ-06과 성능 테스트 절에 조건 차이로 명시돼 있습니다. |
| 개선 12 | 비밀값 패턴·dryrun 파일 | 해결 | SEC-05 패턴이 구체화됐고, 기존 dryrun 파일은 커밋하지 않는다고 사용자 결정으로 기록돼 있습니다. |

## 새 지적(모두 개선 제안이며 완료를 막지 않음)

1. **개선, PERF-02 / REQ-02**
   - 위치: run-ec2.py:297-300, 319-321, 471
   - 근거: 지금 드라이런은 run 이름이 `dryrun`이고 `s1-mixed.js`를 `mode=warmup`으로 돌립니다. check_artifacts는 드라이런일 때 `monitor.log`를 필수 목록에서 뺍니다. 반면 계획의 s2f 드라이런 행은 monitor.log·analysis.json(성공 처리율·최대 TPS·전파)이 채워질 것을 기대합니다.
   - 영향: 구현이 기존 드라이런 분기를 그대로 쓰면 s2f 경로를 검증하지 못한 채 "파일 있음"으로 넘어갈 수 있습니다.
   - 필요한 수정: 커밋 3에서 다음을 명시하면 충분합니다.
     - s2f 드라이런은 s2-hot-account.js에 `S2_MAX=60`을 붙여 실행
     - 모니터 모드는 s2f
     - 드라이런에서도 monitor.log를 필수로 확인
     - 결과 위치 `<루트>/dryrun/<stamp>`의 커밋 여부: 기존 dryrun 파일과 같은 규칙인지

2. **개선, REQ-02**
   - 위치: k6_monitor.py:110-131, 134-156(`RAMPS[mode]` 직접 참조)
   - 근거: 계획은 "ramp.json이 없으면 RAMPS 기본값"이라고만 적었고 `RAMPS["s2f"]` 항목의 유무는 정하지 않았습니다. 또 loadgen.sh:50이 k6 단계 시작 때 `rm -rf $OUT/$run`을 하므로, ramp.json은 그 뒤이면서 watch 시작 전에 써야 합니다.
   - 영향: 모드 이름이 s2f인데 ramp.json이 없으면 KeyError가 납니다. 이때 watch가 죽어 k6가 상한 400까지 계속 돕니다.
   - 필요한 조치: 드라이런이 이 경로를 잡아 주므로 차단 사유는 아닙니다. "분석 단계·표기" 테스트에 s2f 모드 이름으로 ramp.json 없이 호출하는 경우를 하나 넣으면 좋습니다.

3. **개선, 일반 테스트 "s2f 단계" 행**
   - `startRate 40` 확인이 빠져 있습니다. 지금 코드는 s2-hot-account.js:36의 `startRate: STEP`입니다.
   - 임시 계정 JSON에는 `senders`·`loginUsers`·`hot` 키가 필요합니다(ec2.js:25-28).
   - inspect는 원격 jslib import 때문에 네트워크가 필요합니다.

4. **개선, REQ-03**
   - 기존 ec2-load-test의 Q-06(상한까지 무너지지 않으면 기록 후 사용자에게 제안)에 해당하는 규칙이 s2f에는 없습니다.
   - 기존 7회가 모두 초당 100건 이하에서 무너졌으므로 400에 닿을 가능성은 낮습니다. 한 줄로 명시해 두면 됩니다.

5. **개선, 오케스트레이터**
   - `PERF_RESULTS`의 상대 경로를 cwd 기준이 아니라 ROOT 기준으로 푼다고 적어 두면, 다른 위치에서 실행했을 때 결과가 흩어지지 않습니다.

## 적용 제외 항목의 타당성

- **PERF-04·05 제외:** 타당합니다. 장시간 측정도, 웹 체감 측정도 아닙니다.
- **백엔드 중 BE-04만 적용:** 타당합니다. 앱 코드를 바꾸지 않고 정합성만 관측합니다.
- **OPS-02·03·04·05·08 제외:** 타당합니다. 인프라 코드와 배포 절차가 바뀌지 않습니다. 발생기 유형 상향도 Terraform 변수로 합니다.
- **PERF-02·07의 목표 수치 없음:** 기준선 측정이라 받아들일 수 있습니다. PERF-07의 "회귀 허용폭 사전 확정"은 이 기준선을 쓰는 후속 비교 작업에서 정해야 하는 항목입니다.

## 요청 해석과 제품 결정

- 원문 "1번부터 진행하자"와 직전 제안(간격 10, 5회, 최대 TPS 따로 산출)이 요청 해석과 Q-01에 맞게 반영돼 있습니다.
- 시작 요청률 40, 무너짐 기준 유지, 결과 위치 분리는 측정 설계 세부로 보는 것이 타당합니다. 40 단계에서 2회 이상 무너지면 사용자 확인으로 넘기는 안전장치도 있습니다.
- 임의로 채운 제품 결정은 찾지 못했습니다.

관련 파일:
- /Users/imjaehyeon/Documents/[01] - Project/j-bank/.claude/tasks/hot-account-baseline/task.md
- /Users/imjaehyeon/Documents/[01] - Project/j-bank/perf/run-ec2.py
- /Users/imjaehyeon/Documents/[01] - Project/j-bank/perf/run-ec2.sh
- /Users/imjaehyeon/Documents/[01] - Project/j-bank/perf/ec2/k6_monitor.py
- /Users/imjaehyeon/Documents/[01] - Project/j-bank/perf/ec2/loadgen.sh
- /Users/imjaehyeon/Documents/[01] - Project/j-bank/perf/k6/s2-hot-account.js
- /Users/imjaehyeon/Documents/[01] - Project/j-bank/perf/k6/lib/ec2.js

(원문 마지막 줄) 최종 판정 문구: 통과 권고

### 처리 내역

- 새 개선 제안 1~5는 명세(REQ·기준)를 바꾸지 않는 구현 세부라 구현에서 반영하고 progress.md에 기록한다: s2f 드라이런은 s2-hot-account.js + S2_MAX=60 + 모니터 모드 s2f + monitor.log 필수, 결과는 `<루트>/dryrun/<stamp>`(기존처럼 커밋하지 않음); RAMPS에 s2f 기본값(40·10·400) 추가, ramp.json은 run 폴더 정리 뒤·watch 전에 기록; inspect에서 startRate 40 확인·계정 JSON 키(senders·loginUsers·hot); 상한 400까지 안 무너지면 기록 후 사용자 확인; PERF_RESULTS 상대 경로는 ROOT 기준.
