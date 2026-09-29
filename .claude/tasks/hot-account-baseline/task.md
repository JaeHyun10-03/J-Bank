# 작업 명세

## 요구사항 구체화

명세 상태: 확정
원문 요청: 1번부터 진행하자
("1번" = 직전 답변의 "더 자세히 봤으면 하는 부분" 1번: S2 핫 계좌를 단계 간격 초당 10건·5회로 더 촘촘히 재고, 핫 계좌가 실제로 처리한 최대 TPS를 따로 뽑아, 입금 비동기 반영 작업의 개선 전 비교 기준을 정밀하게 만든다.)
요청 해석: 앱 코드는 바꾸지 않는다. 기존 EC2 측정 환경(perf/run-ec2.sh)으로 S2만 더 촘촘한 단계와 더 많은 회차로 측정해, 무너지는 지점과 핫 계좌 최대 처리량을 분산과 함께 기록한다. 기존 기준선(S2 초당 20건 간격 3회)은 그대로 두고 별도 결과로 남긴다.
조사한 사실:
- 기존 S2(`perf/k6/s2-hot-account.js`, `perf/ec2/k6_monitor.py` RAMPS["s2"]): 초당 20건에서 시작해 1분마다 +20, 상한 400, 무너짐 판정 후 한 단계 더 가고 중단. 병행 잔액 조회 초당 20건을 60초 먼저 시작. 스크립트의 병행 조회 길이는 `LEAD + (MAX/STEP) × STAGE`로 시작 요청률을 반영하지 않는다.
- 재측정 기준선(`perf/results/ec2-baseline/s2/`): 무너진 요청률 80/100/80, 최대 지속 60/80/60. 1차 측정(first-pass) S2는 80/60/100/100. 간격 20이라 무너짐 지점이 한 단계씩 흔들린다.
- 지금까지 7회 중 최대 지속 가능 요청률의 최솟값은 초당 40건(1차 r2). 초당 40건 미만에서 무너진 회차는 없다.
- k6_monitor.py는 단계 요청률을 `step × (i + 1)`로 계산한다(stage_windows, watch 모두). 즉 시작 요청률 = 단계 간격을 가정한다. 장애 전파 판정·병행 조회 통계는 `mode == "s2"` 분기에만 있다.
- 현재 분석은 단계별 요청 수·오류율·p95·드롭 추정치를 내지만 성공(201) 이체 처리율은 내지 않는다. k6 원격 쓰기 지표에 `name`·`status` 라벨이 있어(기존 쿼리가 name="relogin", status="401"을 사용) hot-transfer의 status="201" 건수를 셀 수 있다. `increase()`는 외삽 근사치다.
- run-ec2.py: 원격 체크아웃 브랜치가 `GIT_REF = "perf/ec2-load-test"`로 고정(setup이 `origin/<GIT_REF>`로 체크아웃), 결과 위치 `RESULTS = perf/results/ec2-baseline` 전역 상수(commands.log·infra.md·env/·destroy.md·회차 폴더 `RESULTS/<시나리오>/r<회차>`), 시나리오 choices는 s1·s2·s3·s5, 회차 environment.md는 `../../env/...` 상대 링크. run-ec2.sh는 case로 s1|s2|s3|s5만 run으로 넘긴다. 드라이런은 s1 경로만 있다.
- loadgen.sh k6 단계는 `KEY=VALUE` 인자를 k6 `-e`로만 넘기고, 이후 별도 SSM 명령으로 도는 analyze·export는 그 값을 모른다.
- `perf/k6/lib/ec2.js`는 초기화 때 `open(__ENV.ACCOUNTS_FILE)`을 해서 `k6 inspect`에도 계정 파일이 필요하다. 로컬에 k6가 설치돼 있다(이전 작업에서 inspect 사용).
- 측정 절차와 후처리 순서(api 정지 → 정합성)는 ec2-load-test·ec2-load-test-followup에서 정한 대로다. perf 환경은 현재 삭제 상태다.
질문하지 않은 이유: 단계 간격 10건과 5회는 직전 답변의 제안을 사용자가 그대로 진행하라고 했다. 아래는 사용자 제품 결정이 아닌 측정 설계 세부라 근거와 함께 기술 선택으로 기록한다.
기술 선택(근거):
- 시작 요청률 초당 40건: 7회 모두 초당 40건에서는 버텼다. 10건부터 시작하면 회차당 3~4분이 늘지만 정보가 없다. 기존 S2는 20에서 점진적으로 올렸고 s2f는 0에서 40으로 바로 오른다는 부하 이력 차이는 요약에 조건 차이로 적는다. 40건 단계에서 무너진 회차가 2회 이상이면 멈추고 사용자에게 시작 요청률을 묻는다.
- 단계 길이 60초, 상한 초당 400건, 병행 잔액 조회 초당 20건, 무너짐 기준(p95 > 200ms·오류율 > 1%·재시작/OOM), 무너진 뒤 한 단계 더 가고 중단 — 기존 S2와 같다.
- "핫 계좌 최대 처리 TPS": 각 단계 구간(시작 10초 제외)에서 hot-transfer가 status 201을 받은 건수 ÷ 구간 길이 = 단계 성공 처리율. 회차의 최대 처리 TPS = 단계 성공 처리율의 최댓값과 그 단계. 무너짐과 무관하게 전 단계에서 계산한다. 단계에 드롭이 있으면 실제 제공 부하가 목표보다 낮으므로 그 단계의 성공 처리율은 "목표 부하를 다 받지 못한 상태의 값"으로 표시한다. 교차 확인: 전 단계 성공 건수 합(추정)과 k6-summary의 "hot-transfer 201" passes(확정)를 함께 적는다.
- 램프 설정 전달: 오케스트레이터가 s2f 램프 값(시작 40·간격 10·상한 400)을 k6 환경변수로 주고, 부하 발생기가 그 값을 회차 폴더의 `ramp.json`에 남긴다. 감시(watch)·분석(analyze)은 `ramp.json`이 있으면 그 값을, 없으면 기존 RAMPS 기본값을 쓴다. 기존 s2 기본값(20·20·400)은 바뀌지 않는다.
- 원격 체크아웃 브랜치: run-ec2.py가 환경변수 `PERF_GIT_REF`(없으면 기존 `perf/ec2-load-test`)의 원격 커밋으로 두 인스턴스를 맞춘다. 이 작업은 `perf/hot-account-baseline`을 push한 뒤 `PERF_GIT_REF=perf/hot-account-baseline`으로 실행한다. 인스턴스가 체크아웃한 커밋은 environment-target.md에 이미 기록된다.
- 결과 위치: run-ec2.py가 환경변수 `PERF_RESULTS`(없으면 `perf/results/ec2-baseline`)를 결과 루트로 쓴다. 이 작업은 모든 명령(up·setup·prepare·드라이런·run·down)을 `PERF_RESULTS=perf/results/ec2-hot-account`로 실행해 env/·commands.log·회차 폴더가 전부 새 위치에 쌓이고 기존 ec2-baseline 기록은 건드리지 않는다. 회차 폴더는 기존 구조 그대로 `<루트>/s2f/r<회차>/`(environment.md 상대 링크 `../../env/` 유지).
- s2f 사전 검증: `perf/run-ec2.sh s2f --dry-run`을 추가한다. s2f 경로(감시·분석·전파 판정·성공 처리율)를 상한 초당 60건(40·50·60 세 단계)으로 짧게 돌려 결과 폴더가 채워지는지 본다. 드라이런은 기준선에서 제외한다. 부하 발생기 CPU 80% 초과 시 유형 상향은 Terraform 변수(`-var loadgen_instance_type=…`)로 하며 인프라 코드 변경이 아니다.
- 브랜치 `perf/hot-account-baseline`(PR #6과 범위 분리). 로컬의 추적하지 않는 `perf/results/ec2-baseline/dryrun/` 파일은 커밋하지 않는다(사용자 결정).

| 질문 ID | 결정 사항과 영향 | 사용자 답변·근거 | 상태 |
| --- | --- | --- | --- |
| Q-01 | 단계 간격·회차 수 | 초당 10건 간격, 5회(직전 제안을 사용자가 "1번부터 진행") | 해결 |

## 목표

입금 비동기 반영 작업이 비교할 핫 계좌 개선 전 기준을, 무너지는 요청률과 최대 성공 처리량(TPS)의 분산까지 포함해 정밀하게 남긴다.

## 범위

- k6 S2 스크립트: 시작 요청률·간격·상한을 환경변수로 받음(기본값 기존과 같음), 병행 조회 길이를 시작 요청률 반영해 계산.
- k6_monitor.py: `ramp.json` 우선의 램프 설정, 시작 요청률 반영 단계 계산(watch·analyze), s2f의 전파 판정, 단계 성공 처리율·최대 처리 TPS·드롭·실패 수.
- loadgen.sh: k6 단계에서 램프 값을 `ramp.json`으로 기록.
- run-ec2.py·run-ec2.sh: `PERF_GIT_REF`·`PERF_RESULTS`, `s2f` 시나리오와 `s2f --dry-run`, check_artifacts의 s2f 처리.
- EC2 측정: up → setup → prepare → s2f 드라이런 → s2f 5회(+규칙상 추가) → down.
- 결과 요약 문서, perf/README.md 절, 개발일지.

## 요구사항

| ID | 조건·입력 | 기대 동작·실패 조건 |
| --- | --- | --- |
| REQ-01 | `PERF_RESULTS=perf/results/ec2-hot-account PERF_GIT_REF=perf/hot-account-baseline perf/run-ec2.sh s2f <회차>` | 기존 S2와 같은 절차로, 핫 계좌 이체를 초당 40건에서 시작해 1분마다 +10(상한 400) 올리고 병행 잔액 조회 초당 20건을 60초 먼저 시작해 이체 램프 끝까지 유지한다. 무너짐 판정 후 한 단계 더 가고 중단한다. 결과는 `perf/results/ec2-hot-account/s2f/r<회차>/`에 저장되고, env/·commands.log도 같은 루트에 쌓인다. 환경변수 없이 실행한 기존 `s2`의 단계(20에서 시작, +20)와 결과 위치(ec2-baseline)는 바뀌지 않는다 |
| REQ-02 | 분석(analysis.json)·감시(monitor.log) | 단계 목표 요청률이 40·50·60…으로 실제 스크립트 단계와 일치하고(monitor.log 표기 포함), 단계별 성공 처리율(status 201 건수/초)·실패 수·드롭 추정치가 기록되며, 회차의 최대 처리 TPS와 그 단계, 전 단계 성공 합(추정)과 k6-summary "hot-transfer 201" passes(확정)가 기록된다. 무너진 요청률·최대 지속 가능·장애 전파 판정도 기존 S2와 같은 규칙으로 기록된다 |
| REQ-03 | 반복 | 유효 회차 5회를 목표로 한다. 무효 사유(부하 발생기 CPU 80% 초과, 지표 결측)는 대표값·변동폭에서 빼고 사유별 1회까지 추가한다. 유효 회차 기준 무너진 요청률 차이가 한 단계(10)를 넘으면 원인을 기록하고 1회 추가한다. 상한으로도 유효 5회가 안 되거나, 시작 단계(40)에서 무너진 회차가 2회 이상이면 멈추고 사용자에게 확인한다. 회차별 무너진 요청률·최대 지속·최대 처리 TPS·드롭(확정 전체 수와 단계 추정치)·실패 수가 기록되고 중앙값·최소·최대가 요약된다 |
| REQ-04 | 각 회차 정합성 | 새 불일치 0, 차변 = 대변, 핫 계좌 잔액 증가분 = 완료 이체 금액 합(DB·k6 기준), 실패 멱등키 대조, 대사 성공이 기록된다 |
| REQ-05 | 인프라·비밀값 | perf 환경을 측정할 때만 만들고 끝나면 destroy해 잔여 리소스 0을 확인한다. 운영 `envs/dev` plan은 "No changes"로 유지된다. 결과물에 비밀값·토큰 패턴이 0건이다 |
| REQ-06 | 문서 | `perf/results/ec2-hot-account/summary.md`에 조건·회차별 표·분산 요약·드롭 해석·기존 S2 기준선과의 관계(간격 20 대 10, 시작 20 점진 대 40 즉시, 후처리 순서 차이)가 적히고, perf/README.md에 `s2f`·`s2f --dry-run`·`PERF_GIT_REF`·`PERF_RESULTS` 사용법이 추가된다 |

## 커밋 계획

1. `feat(perf)`: S2 k6 스크립트가 시작 요청률·간격·상한을 환경변수로 받게 함(기본값 유지, 병행 조회 길이 계산 보정)
2. `feat(perf)`: 램프 설정을 회차 ramp.json으로 넘기고 단계 분석에 시작 요청률·성공 처리율·최대 처리 TPS 반영
3. `feat(perf)`: 오케스트레이터에 s2f 시나리오·드라이런과 원격 브랜치·결과 위치 환경변수 추가
4. `perf(results)`: 핫 계좌 정밀 기준선 원자료
5. `docs(perf)`: 핫 계좌 정밀 기준선 요약과 README 실행 방법
6. `docs(devlog)`: 개발일지
7. `chore(harness)`: 작업 기록

## 완료 기준

- REQ-01~06이 아래 증거로 확인된다.
- 기존 필수 검사(verify)가 통과한다.
- perf 환경 destroy, 잔여 리소스 0.

## 하지 않을 일

- 앱 코드 변경, 입금 비동기 반영(후속 작업).
- S1·S3·S5 재측정, 기존 기준선 결과 수정.
- 시작 요청률을 40 미만으로 낮추는 측정(REQ-03 조건 충족 시 사용자 확인 후).

## 적용 영역과 상세 기준

- 프론트·백엔드(앱)·AI·보안·전체 흐름: 해당 없음(앱 코드·인증·화면 변경 없음). 측정이 동시성·정합성을 관측하므로 BE-04 적용.
- DevOps: 인프라 코드 변경 없음. perf 환경 생성·삭제와 오케스트레이터 설정(원격 브랜치·결과 위치) 변경이 있어 OPS-01·06·07 적용. OPS-02·03·04·05·08은 절차 변경이 없어 해당 없음.
- 보안: SEC-05 적용. 나머지 해당 없음.
- 성능: 적용. PERF-01·02·03·06·07. PERF-04·05 해당 없음.

| 기준 ID | 완료 기준·시나리오 | 기대 결과·수치 목표 | 실행 명령 | 시점 | 증거 위치 |
| --- | --- | --- | --- | --- | --- |
| BE-04 | REQ-04 정합성 | 회차마다 새 불일치 0, 차변=대변, 핫 계좌 증가분 = 완료 금액 합, 실패 키 대조 기록 | 오케스트레이터 정합성 단계 | 매 회차 | `ec2-hot-account/s2f/r<n>/integrity.md` |
| OPS-01 | REQ-01·05 구성 식별 | 인스턴스 체크아웃 커밋이 push한 브랜치 커밋과 같음, 이미지 digest 기록 | `PERF_RESULTS=… PERF_GIT_REF=… perf/run-ec2.sh setup` | 측정 전 | `ec2-hot-account/env/target/environment-target.md` |
| OPS-06 | 자원 관측 | CPU·메모리·풀·락 시계열 확보 | 오케스트레이터 export | 매 회차 | `s2f/r<n>/loadgen/prometheus/` |
| OPS-07 | 관측 결측 | gaps.txt invalid=no | 오케스트레이터 gaps | 매 회차 | `s2f/r<n>/loadgen/prometheus/gaps.txt` |
| SEC-05 | REQ-05 비밀값 | 결과물 비밀·토큰 패턴 0건, setup_data 비어 있음 | 파이썬 스캔(`access_token=`, `eyJhbGci`, `DB_PASSWORD=`, `JWT_SECRET=`, `PII_ENCRYPTION_KEY=`, `METRICS_PASSWORD=`, `GRAFANA_ADMIN_PASSWORD=`, `"password":"…"` 패턴을 `perf/results/ec2-hot-account/` 전 파일·gz에 적용, k6-summary setup_data 확인) | 커밋 4 전 | review.md |
| PERF-01 | 조건 고정 | 회차 environment.md(발생기 유형·커밋·시작 조건), ramp.json(40·10·400) | 오케스트레이터 | 매 회차 | `s2f/r<n>/environment.md`, `s2f/r<n>/loadgen/ramp.json` |
| PERF-02 | REQ-02 무너짐 전 구간 | 단계별 p95·오류율·성공 처리율·실패·드롭 | `perf/run-ec2.sh s2f <n>` | 매 회차 | `s2f/r<n>/loadgen/analysis.json` |
| PERF-03 | REQ-02·03 한계 | 무너진 요청률·최대 지속·최대 처리 TPS | 같은 명령 | 매 회차 | 같은 위치, summary.md |
| PERF-06 | REQ-04 정확성 | BE-04와 같음, 성공 합(추정)과 201 passes(확정) 교차 기록 | 같은 명령 | 매 회차 | `integrity.md`, analysis.json |
| PERF-07 | REQ-03 신뢰성 | 유효 5회, 발생기 CPU ≤ 80%, 결측 없음, 회차·단계별 드롭(확정·추정)·실패 수 집계, 변동폭 한 단계 이내 또는 원인 기록 | 오케스트레이터 + 요약 | 측정 후 | `ec2-hot-account/summary.md` |
| PERF-04·05 | 해당 없음 | 장시간·웹 체감 아님 | - | - | - |

## 일반 테스트 방법

| 완료 기준 | 시나리오·예상 결과 | 테스트 명령·근거 위치 | 실행 시점 |
| --- | --- | --- | --- |
| 기존 검사 회귀 없음 | 앱 무변경이라 통과 | `python3 .claude/hooks/workflow.py verify` | 구현 후 |
| 기존 S2 단계 불변 | 환경변수 없이 inspect한 hot_transfer 단계 목표가 20·40·…·400, startRate 20, parallel duration 기존(21분)과 같음 | 임시 계정 JSON을 만들어 `k6 inspect -e ACCOUNTS_FILE=<임시> -e BASE_URL=http://x perf/k6/s2-hot-account.js`, 출력의 scenarios.hot_transfer.stages·startRate·parallel_balance.duration 확인 | 커밋 1 후 |
| s2f 단계 | `-e S2_START=40 -e S2_STEP=10 -e S2_MAX=400`으로 inspect한 단계 목표가 40·50·…·400(37단계), parallel duration = 60 + 37×60초 | 같은 방식 | 커밋 1 후 |
| 분석 단계·표기 | ramp.json(40·10·400)이 있을 때 stage_windows 목표 요청률이 40·50·…, 없을 때 s2 기본 20·40·…, watch 표기 계산이 같은 값, s2f에서 전파 판정 필드 존재 | 파이썬으로 k6_monitor 함수 호출 확인(가짜 시각 입력) | 커밋 2 후 |
| 오케스트레이터 | 문법 검사, `PERF_RESULTS`·`PERF_GIT_REF` 미설정 시 기존 값, run-ec2.sh s2f·`s2f --dry-run` 인자 전달 | `python3 -c "import ast…"`, `bash -n`, 인자 파싱 확인 | 커밋 3 후 |
| s2f 경로 사전 검증 | EC2에서 `s2f --dry-run`(상한 60)으로 monitor.log·analysis.json(성공 처리율·최대 TPS·전파)·gaps.txt·integrity.md가 채워짐(기준선 제외) | `PERF_RESULTS=… PERF_GIT_REF=… perf/run-ec2.sh s2f --dry-run` | 측정 전 |
| 정리 | destroy 후 state 비어 있음, 잔여 0, 운영 plan No changes | `perf/run-ec2.sh down`, `terraform -chdir=infra/terraform/envs/dev plan -detailed-exitcode` | 측정 후 |

## 성능 테스트

- 필요 여부와 이유: 필요(작업 본체).
- 측정 대상과 명령: 핫 계좌 이체 `/api/v1/transfers`(수신 계좌 1개), 병행 잔액 조회. `perf/run-ec2.sh s2f 1..5`(환경변수 `PERF_RESULTS`·`PERF_GIT_REF`).
- 환경: ec2-load-test 측정 조건과 같음(대상 t3.small, 부하 발생기 c7i.large, 1천만 건 시드, 관리 포트 9095, api 정지 후 정합성).
- 부하: 핫 계좌 이체 초당 40건에서 1분마다 +10, 상한 400, 무너짐 후 한 단계 더. 병행 잔액 조회 초당 20건.
- 반복: 유효 5회(REQ-03 규칙).
- 지표와 판정: 무너짐 기준은 기존과 같다. 기준선 측정이라 통과 목표 수치는 없다.
- 변경 전 결과: 이 작업이 입금 비동기 반영의 변경 전 결과다. 기존 S2 기준선(간격 20, 3회: 무너짐 80/100/80)은 참고로 함께 적고, 간격·시작 방식·후처리 순서 차이를 명시한다.
- 결과 위치: `perf/results/ec2-hot-account/`.

## 추가 맥락

- 근거 작업: `.claude/tasks/ec2-load-test/`(측정 절차·조건·반복 규칙), `.claude/tasks/ec2-load-test-followup/`(후처리 순서).
- 비용: 측정할 때만 생성. 준비 약 30분 + 드라이런 약 10분 + 회차당 약 12~15분 × 5.
