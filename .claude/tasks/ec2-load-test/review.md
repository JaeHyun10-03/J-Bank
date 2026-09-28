# 검토 결과

## 검토 범위

- 작업: ec2-load-test. 명세 task.md(REQ-01~13), 계획 리뷰 plan-review.md(3회차 통과 권고).
- 대상: 작업 시작 HEAD 6c40ee2 → HEAD 30dafea. verify-002 snapshot `bdaecc4024a3f1259e6e8737daa6a7d92bf625961271fdd135f1da2b03051f58`(6개 검사 종료 코드 0, 백엔드 Gradle은 apps/ 무변경으로 UP-TO-DATE).
- 하네스 결과 리뷰 회차: 1/3은 review-begin 직후 verify를 다시 실행하면서 verifier 호출 없이 소비됐다(메인 실수). 2/3이 실제 첫 verifier 결과 리뷰다.

## 독립 검증 결과

### 2/3회차 (실제 첫 결과 리뷰)

- 호출: verifier 서브에이전트(agentId ae8903d62542f528d), 2026-09-28 KST.
- 대상 snapshot: bdaecc4024a3f1259e6e8737daa6a7d92bf625961271fdd135f1da2b03051f58
- 판정: 수정 필요(중요 4, 차단 0)

#### verifier 응답 원문

# 결과 리뷰: ec2-load-test

- 모드: 결과 리뷰, 하네스 회차 2/3. 실제 verifier 결과 리뷰로는 첫 회차라 이전 지적은 없습니다.
- 검증 대상: snapshot bdaecc40…(HEAD 30dafea, 시작 6c40ee2), checks.json(verify-002)
- 판정: **수정 필요** (중요 4건, 차단 없음)

## 읽은 자료
- 명세·기록: docs/testing-policy.md, task.md, progress.md, plan-review.md(판정 부분), evidence/changes.txt, checks.json, verify-002/check-4·5.log
- 코드: perf/run-ec2.py, run-ec2.sh, perf/ec2/k6_monitor.py·target.sh·loadgen.sh·metrics_proxy.py, perf/k6/lib/ec2.js·s3-spike.js, perf/prepare-accounts.py, infra/terraform/modules/perf/main.tf·user_data.sh.tpl, infra/compose/perf/docker-compose.target.yml·target.env.example, 대시보드 JSON의 쿼리, perf/README.md EC2 절
- 결과 원자료: summary.md, bottleneck-analysis.md, env/(infra·destroy·environment-target·baseline·disk·seed·prepare·restore-verify·commands.log 일부)
  - s1 r1~r3, s2 r1·r2, s3 r1·r3, s5 r2: analysis.json·integrity.md·k6-summary.json·monitor.log·batch.txt·batch 로그·batch-explain.txt
  - 전 회차: `transfer 201`·`relogin 200` 체크 수 대조
- 비밀값 패턴 검사: perf/ 아래 JWT·access_token·AKIA·PRIVATE KEY·PASSWORD= 값, results/의 XSRF·Cookie·tokens를 grep했고 0건입니다.

## 확인하지 못한 범위
- `query_range.json.gz` 내용. 압축 파일이라 읽지 못했습니다. 그래서 다음 수치는 원자료와 맞춰 보지 못했습니다.
  - 가설 C의 서버 측 로그인 p95(297ms·793ms)
  - 가설 E 사고 수치(807→127MB, 메이저 폴트 169/s)
  - 패널별 시계열이 중간에 끊겼는지 여부
- git 이력. 이전 커밋에 토큰이 들어간 적이 있는지는 명령을 실행하지 못해 확인하지 못했습니다. 커밋 순서(45893e5 → 3f88d43)로 보면 제거 후 커밋된 것으로 보입니다.
- AWS 실제 상태. 삭제 완료 여부는 destroy.md 기록으로만 확인했습니다. 테스트와 terraform은 실행하지 않았습니다.

## 적용 기준 ID
BE-03, BE-04, OPS-01, OPS-03, OPS-04, OPS-06, OPS-07, SEC-05, SEC-07, PERF-01, PERF-02, PERF-03, PERF-06, PERF-07. 적용 영역 누락은 없습니다.

## 지적

### 중요-1. "실패 응답인데 반영" 수치에 k6 중단 순간 처리 중이던 요청이 섞였고, REQ-10 (4)는 구현되지 않았습니다 (BE-04, REQ-10, PERF-06)
- 근거
  - `perf/run-ec2.py:359-363`은 "새 COMPLETED 수 − k6 201 수"를 계산하고 "실패 응답이 실제 반영된 건수"라고 적습니다.
  - 그런데 S1·S2는 모니터가 SIGINT로 k6를 중단합니다(`k6_monitor.py:153-154`). 이때 처리 중이던 요청은 응답도 실패 체크도 받지 않습니다.
- 원자료 대조
  - s2/r1: `hot-transfer 201` passes 18506, **fails 1**. 새 COMPLETED는 19229이고 차이는 723입니다(s2/r1/integrity.md:5). 모든 단계의 이체 오류율도 0입니다(analysis.json).
  - s2/r2: fails 0인데 차이는 2입니다.
  - s1/r2: fails 1277인데 차이는 1640입니다. 5980+1277=7257은 7620보다 작으므로, 최소 363건은 응답 자체가 없었습니다. r1(706 vs 1050)과 r3(1519 vs 1850)도 같은 모양입니다.
  - S3는 끝까지 실행되어 fails(2950·2832·2821)가 차이보다 커서 성립 가능한 값입니다.
- 명세와의 차이: REQ-10 (4)는 "timeout·5xx 이체 요청의 멱등키가 실제로 반영됐는지 여부"를 비교하라고 합니다. 하지만 k6가 실패 요청의 멱등키를 기록하지 않습니다(`lib/ec2.js:111`). 그래서 멱등키 단위 비교가 없고 전체 건수 차이만 있습니다.
- 영향
  - bottleneck-analysis.md:11-12, 24-31, summary.md, README.md:428-429의 "금융 관점 가장 중요한 발견" 수치가 과장됐습니다. S2의 393건은 거의 전부 중단 순간 처리 중이던 요청입니다. S1의 1,640건 중 최소 약 360건도 같습니다.
  - 이 수치는 후속 작업 3번의 비교 기준으로 넘어갑니다.
- 필요한 수정(선택지)
  - (a) 다음 측정부터 이체 요청의 멱등키와 응답 상태(0/5xx/201)를 기록하고, 대상 DB의 idempotency_key와 조인해 판정합니다.
  - (b) 재측정 없이 가려면 표를 둘로 나눕니다: "실패 응답(fails) 중 반영 상한 = min(fails, 차이)"와 "중단 시 처리 중 반영 = 차이 − fails의 하한". 그리고 S2 행과 README·분석 문서의 문장을 고칩니다. REQ-10 (4)의 멱등키 단위 비교를 하지 못한 사실은 미충족 또는 범위 조정으로 사용자 결정을 받아야 합니다.

### 중요-2. 측정 구간 중 지표가 빈 경우를 잡지 못하고, 빈 시계열 검사 결과도 결과물에 없습니다 (OPS-07, REQ-05, REQ-08)
- 근거
  - `k6_monitor.py:226-229` export는 전체 구간에서 결과가 아예 없는 시계열만 셉니다. 출력만 하고 실패시키지 않으며, 파일로 남기지 않습니다. `check_artifacts`(run-ec2.py:400-408)도 이 검사를 하지 않습니다.
  - 저장소 전체에서 `empty_series`는 progress.md와 코드에만 있습니다. 회차별 결과에는 없습니다. "모든 회차 empty_series=0"은 커밋되지 않은 scratchpad 로그에만 근거가 있습니다.
- 실제 빈 구간의 정황
  - s3/r3 analysis.json의 spike_system을 보면 오류율 86%, 타임아웃 12,164건인데 `hikari_pending_max` 29, `tomcat_busy_max` 47, **`gc_pause_ratio` null**입니다. r1·r2는 189/200, 186/200이었습니다.
  - metrics-proxy는 포화된 같은 Tomcat을 통해 /actuator/prometheus를 10초 timeout으로 가져옵니다(metrics_proxy.py:32, 74). 그래서 스파이크 구간에 api 지표 수집이 빠진 것으로 보입니다.
  - summary.md:47은 이 값을 실제 값처럼 표에 올렸습니다.
- 영향
  - REQ-05의 "측정 구간에 한 항목이라도 데이터가 비면 실패"를 판정할 근거가 없습니다.
  - REQ-08 s3/r3의 Hikari pending 최댓값이 신뢰할 수 없습니다.
  - 정확히는 metrics-proxy(설명받은 차이 1)의 부작용입니다. 무너지는 순간 원인 지표가 빠질 수 있다는 한계를 문서에 적어야 합니다.
- 필요한 수정
  - query_range.json.gz에서 측정 구간 안 api job 지표의 빈 구간(연속 결측)을 계산해 회차별 파일로 남깁니다.
  - s3/r3 표의 Hikari·Tomcat 값에 "수집 공백"을 표시하고, bottleneck-analysis.md 측정 한계에 metrics-proxy 경유 수집 공백을 추가합니다.
  - 가능하면 `up{job="api"}`·`scrape_duration_seconds`도 export 대상에 넣습니다.

### 중요-3. 배치가 처리한 거래 건수가 결과 문서에 없습니다 (REQ-09, REQ-12, PERF-06)
- 근거
  - REQ-09는 "배치가 처리한 거래 건수(runDate 하루치)", REQ-12는 "가설 D의 실제 배치 처리량이 명시된다"를 요구합니다. 계획 리뷰 1회차 차단-2의 처리로 넣은 항목입니다.
  - 그런데 summary.md:61은 건수 없이 "runDate 하루치"라고만 적었고, bottleneck-analysis.md:21 가설 D에도 건수가 없습니다.
  - 원자료에는 값이 있습니다.
    - s5/r2/target/batch-s5-r2-load-ctrDetectionJob.log:55 "고액현금거래 판별 완료: 기준일=2026-09-28, **대상 없음**"
    - fdsDetectionJob.log:55 "대상 거래 3008건, 신규 적재 2608건"
- 영향
  - CTR은 0건을 처리하려고 9.1초 동안 전체를 읽었습니다. 기준일이 UTC 경계라 측정 부하가 만든 거래는 대상에 들어가지 않았습니다.
  - 이 사실이 D 판정의 해석(인덱스 없는 조회 비용)에 결정적인데 문서에 드러나지 않습니다.
- 필요한 수정: 회차별 CTR·FDS·대사 처리 건수를 로그에서 옮겨 summary.md와 가설 D에 적고, "CTR 대상 0건"의 의미를 명시합니다. 문서만 고치면 됩니다.

### 중요-4. S5에서 원인 설명이 없는 Tomcat·Hikari 포화가 3회 모두 있었는데 보고되지 않았습니다 (PERF-06, PERF-07, REQ-09·11)
- 근거: S5 전체 구간 overall_system 값은 다음과 같습니다. 배치 구간 창 안에서는 pending 0~14였습니다. 즉 배치 구간 밖에서 포화가 일어났습니다.

| 회차 | Hikari pending | Tomcat busy | p99 |
| --- | --- | --- | --- |
| r1 | 160 | 172 | 1.9s (timeouts 2) |
| r2 | 189 | 200 | 1.6s |
| r3 | 146 | 157 | 2.2s |

- 추정 원인: `relogin 200` 체크가 206·212·200건입니다(실패 3·2·0). setup 토큰이 모두 같은 `data.at`을 쓰므로(lib/ec2.js:58, 68), 12분 시점에 약 200개 VU가 거의 동시에 BCrypt 재로그인을 합니다. 20분짜리 S5에서만 12분을 넘습니다. 설명받은 차이 2가 만든 측정 부작용으로 보입니다. 시점은 query_range로 확인해야 합니다.
- 영향
  - summary.md:62 "무너지지 않아"와 가설 D의 "온라인 p95 105ms" 비교 기준에 설명되지 않은 이상 구간이 섞였습니다.
  - 후속 작업이 같은 S5를 재측정하면 같은 부작용이 반복됩니다.
- 필요한 수정
  - query_range로 포화 시점을 확인해 원인을 summary·분석 문서에 적습니다.
  - 재로그인 동기화가 원인이면 측정 한계로 명시하고, 후속 측정 전에 VU별 재로그인 시점 분산을 제안합니다. 이는 명세 변경이므로 계획 검증 대상입니다.

### 제안 (완료를 막지 않음)
1. summary.md:21-22의 "오류율 10~42%"는 r1~r3 원자료(9.9%·23.6%·36.3%)와 맞지 않습니다. 42%가 사고 회차 값이면 그렇다고 밝혀 주세요.
2. bottleneck-analysis.md:8의 "NFR-PERF-001이 요구하는 초당 100건과 같은 지점"은 비교가 맞지 않습니다. NFR은 이체 기준이고, 혼합 100건 중 이체는 20건입니다. 문장 정정을 권합니다.
3. PERF-07의 증거 위치 `<시나리오>/summary.md`가 없습니다. 통합 summary.md에는 발생·완료·실패·드롭 수가 없습니다. 원자료(analysis.json의 requests·dropped_iterations)는 있으므로 요약 표에 드롭 수를 추가하면 됩니다.
4. REQ-10 (3)의 비교 대상이 DB COMPLETED 합이라 사실상 같은 DB 값끼리 비교합니다(target.sh:209-211). k6 201 × 1,000원과의 비교도 함께 적으면 좋습니다.
5. postgres-exporter가 앱 소유자 계정(jbank)으로 접속합니다(compose:130). cAdvisor의 `/var/run:ro`는 docker.sock API 호출을 막지 못합니다. SEC-07의 문구("읽기 전용 마운트")는 형식상 충족하지만, 일회성 환경의 한계로 review.md에 적기를 권합니다.
6. Grafana UI 대신 대시보드 전 패널 쿼리를 export한 것은 REQ-05의 대체 증거로 타당합니다. 다만 명세 문구("Grafana에서 본다")와 다른 점과 SSM 포트 포워딩 접속을 확인하지 못한 점을 review.md에 적어 주세요.
7. 커밋하지 않는 첫 드라이런 산출물(`perf/results/ec2-baseline/dryrun/` 바로 아래)은 작업 트리를 계속 dirty로 만듭니다. 삭제나 무시 설정을 권합니다.
8. README "운영과 다른 점" 표에 "부하 발생기 분리(같은 AZ c7i.large)" 행을 명시하면 REQ-03 목록과 1:1로 맞습니다. 지금은 구성 절과 관측 도구 행에 암시만 되어 있습니다.

## 설명받은 차이 1~9의 타당성
| 번호 | 판단 |
| --- | --- |
| 1. metrics-proxy | 앱 코드를 바꾸지 않으려는 목적으로 타당하고 문서화도 됐습니다. 단, 포화 시 수집 공백이라는 부작용이 있어 중요-2의 원인이 됩니다. |
| 2. setup 토큰 공유·12분 재로그인 | S1~S3에는 타당합니다. S5에는 동시 재로그인 부작용이 의심됩니다(중요-4). |
| 3. default_sni | 타당합니다. |
| 4. 측정 후 api 정지·배치 timeout·사고 자료 보존 | 타당합니다. 최초 실패와 원인이 보존됐습니다. 재부팅은 수동이었지만 commands.log에 조사 명령이 남아 있습니다. |
| 5. S2 r4 추가 | 계획 규칙대로이고 중앙값 계산도 맞습니다. |
| 6. setup_data 토큰 제거 | 타당합니다. 현재 results에 토큰 패턴 0건입니다. |
| 7. 로그 강제 추가 | 비밀 패턴 0건을 확인했습니다. accounts.json·사설 IP·계정 ID는 비밀값이 아니고, 환경은 삭제됐습니다. |
| 8. testing-policy 되돌림 | 타당합니다. |
| 9. Grafana 화면 대체 | 제안-6 참고. |

## 표본 대조 (일치)
- S1 r1~r3의 직전·무너진 p95, CPU, Hikari, Tomcat, 락 대기 값과 중앙값
- S2 r1·r2의 모든 열과 4회 중앙값(90/70/916/120/744)
- S3 처리량·오류율·타임아웃·회복 시간과 중앙값
- S5 r2의 배치 시간(43/33, 64/45, 57/39), 배치 구간 p95·CPU, CTR EXPLAIN(9,146ms, read=152,711)
- infra.md(같은 AZ 2d, unlimited, SSM Online, 0.0.0.0/0 0건, SSM 정책만), destroy.md, environment-target.md의 digest

## REQ별 판정
| REQ | 판정 | 근거·비고 |
| --- | --- | --- |
| REQ-01 | 충족 | env/infra.md, modules/perf/main.tf |
| REQ-02 | 충족 | env/destroy.md(state 비어 있음, 태그 조회 지연분을 직접 조회해 NotFound, 운영 plan 종료 코드 0). 기록만 확인 |
| REQ-03 | 충족 | environment-target.md digest, user_data에 cron 없음, openssl rand(target.sh:31-41), README 차이 표(제안-8) |
| REQ-04 | 충족 | seed.log(10001/100000/1천만), prepare.log, baseline.log(불일치 10만, 대변 200억, 2346MB), disk.log, restore-verify.log |
| REQ-05 | 부분 | 패널·PG 로그·pg_stat_activity 10초·CloudWatch는 있음. 측정 구간 공백 검사와 결과 보존이 없고 s3/r3 공백 정황(중요-2) |
| REQ-06 | 충족 | s1 analysis.json 단계별 p95·p99·오류율·처리량, 무너짐 100/최대 지속 50 |
| REQ-07 | 충족 | s2 analysis.json(lock·Hikari·PG 락·병행 조회·전파). 반영 수치는 중요-1 |
| REQ-08 | 부분 | 회복·미회복은 충족. s3/r3 Hikari 최댓값 신뢰 불가(중요-2) |
| REQ-09 | 부분 | 시간·EXPLAIN은 충족. 처리 건수 문서 누락(중요-3), 전후 비교에 설명 없는 포화 섞임(중요-4) |
| REQ-10 | 부분 | (1)~(3)·대사 성공·새 불일치 0은 충족. (4) 멱등키 단위 비교 없음, 반영 수치 잘못 표기(중요-1) |
| REQ-11 | 충족(주의) | 3회+S2 r4, 발생기 CPU 최대 21%, 회차 시작 조건 기록. S5 부작용은 중요-4 |
| REQ-12 | 부분 | 회차 파일은 모두 있음(artifacts missing=none). 가설 D 실제 처리량 누락(중요-3) |
| REQ-13 | 충족 | README 명령과 commands.log 대응(run-ec2.sh → run-ec2.py). 비밀값은 생성 방법만 기재 |

## 기준 ID별
| 기준 ID | 판정 | 근거·비고 |
| --- | --- | --- |
| BE-03 | 충족 | |
| BE-04 | 부분 | 중요-1 |
| OPS-01 | 충족 | |
| OPS-03 | 충족 | review.md 기록은 메인이 남길 것 |
| OPS-04 | 충족 | 매 회차 restore→wait_ready→예열 |
| OPS-06 | 충족 | |
| OPS-07 | 부분 | 중요-2 |
| SEC-05 | 충족 | grep 0건 |
| SEC-07 | 충족 | 제안-5 |
| PERF-01 | 충족 | |
| PERF-02 | 충족 | |
| PERF-03 | 충족 | |
| PERF-06 | 부분 | 중요-1·3·4 |
| PERF-07 | 충족(제안-3) | |

기존 검사: checks.json 6개 종료 코드 0. 백엔드는 apps/를 바꾸지 않아 Gradle UP-TO-DATE(check-5.log)이며 타당합니다.

## 적용 제외 항목
- OPS-02·05·08: CI·배포·백업 절차 변경 없음
- SEC-01~04·06·08: 앱 보안 코드 변경 없음
- BE-01·02·05~08: 앱 코드 변경 없음
- PERF-04: Q-04로 S6 제외
- PERF-05: 웹 체감 측정 아님

모두 이유가 타당합니다.

## 이전 지적
실제 verifier 결과 리뷰가 처음이라 없습니다.

## 수정 방향 요약
- 중요-2·3·4는 재측정 없이 원자료 재분석과 문서 수정으로 해결할 수 있습니다.
- 중요-1은 문서 정정에 더해, REQ-10 (4) 멱등키 단위 비교를 못 한 사실을 두고 사용자 결정이 필요합니다: 다음 측정에서 보완할지, 범위를 조정할지.

(원문 마지막 줄) 최종 판정: 수정 필요

### 3/3회차 (마지막, Q-07 보완·재측정 후)

- 호출: verifier 서브에이전트(agentId a7a4aa7f0d754f098), 2026-09-28 KST.
- 대상 snapshot: 08c9bb650a3e17d5f1d61f25f4f2a606bd1e0c27c30d2c73ed5bde463b501569 (HEAD bd5e671, verify-003)
- 판정: 통과 권고(차단 0, 중요 0, 개선 6)

#### verifier 응답 원문

# 결과 리뷰 3/3 (마지막 회차): ec2-load-test

- 모드: 결과 리뷰, 하네스 회차 3/3. 읽기만 했습니다.
- 대상: snapshot 08c9bb65… (HEAD bd5e671, 작업 시작 6c40ee2), evidence/checks.json(verify-003)
- 판정: **통과 권고**. 차단·중요 지적은 없고, 개선 6건입니다.

## 읽은 자료
- 명세·기록: task.md(Q-07 반영본), review.md(2/3 원문), progress.md, evidence/checks.json, evidence/changes.txt(앞 618줄과 grep)
- 코드
  - perf/k6/lib/ec2.js, perf/ec2/k6_monitor.py(전체), perf/ec2/metrics_proxy.py(전체)
  - perf/ec2/target.sh(120~289줄), perf/ec2/loadgen.sh(FAILKEY 추출·setup_data 제거·gaps 호출 부분), perf/run-ec2.py(290~529줄), perf/run-ec2.sh
  - docker-compose.target.yml의 9095·헬스체크·마운트 부분, 대시보드 JSON의 Tomcat·up 쿼리
- 문서: summary.md, bottleneck-analysis.md, perf/README.md EC2 절(364~438줄), first-pass/summary.md·bottleneck-analysis.md 머리말
- 재측정 원자료
  - env/: infra.md, destroy.md, environment-target.md, commands.log(grep과 1~118줄)
  - 12개 회차 전부: gaps.txt, integrity.md의 실패 키 행
  - 일부 회차 상세
    - s1: r1 전체, r2·r3 monitor.log·integrity.md
    - s2: r1·r3 analysis.json·integrity.md, r2 integrity.md와 120 단계 행
    - s3: r2 analysis.json
    - s5: r1 analysis.json·batch.txt·batch-counts.txt·noload batch.txt·batch-explain.txt·environment.md, r3 analysis.json
- 비밀값 검사: perf/ 전체(추적하지 않는 dryrun/ 포함)에서 JWT(eyJ…)·access_token=값·XSRF-TOKEN=값·AKIA·PRIVATE KEY·PASSWORD=값·"password":"값" 패턴을 grep했습니다. 결과물에서는 0건입니다. 스크립트에서 걸린 것은 모두 `openssl rand` 생성이나 환경변수 참조입니다(run-10m.sh 고정값은 작업 전부터 있던 로컬 스크립트).

## 확인하지 못한 범위
- `query_range.json.gz`와 `k6.log.gz`는 압축 파일이라 읽지 못했습니다.
  - 그래서 서버 측 로그인 p95(315ms, 715ms), 가설 E 사고 수치(807→127MB)는 원자료와 대조하지 못했습니다.
  - gaps.txt의 결측 0은 코드 로직과 결과 파일로만 확인했습니다.
- AWS 실제 상태(destroy 완료, 운영 plan 종료 코드 0)와 git 이력(479eefc 내용)은 기록과 설명으로만 확인했습니다. 명령은 실행하지 않았습니다.
- verify 결과는 checks.json 기록만 봤습니다. 6개 모두 exit 0, skipped 없음, 명령은 실제 앱 검사입니다. 변경 목록에 apps/·docs/·운영 compose·envs/dev 변경은 0건이라 Gradle UP-TO-DATE는 타당합니다.

## 적용 기준 ID
BE-03, BE-04, OPS-01, OPS-03, OPS-04, OPS-06, OPS-07, SEC-05, SEC-07, PERF-01, PERF-02, PERF-03, PERF-06, PERF-07. 누락된 적용 영역은 없습니다.

## 이전 지적(2/3) 처리 확인

| 지적 | 판정 | 근거 |
| --- | --- | --- |
| 중요-1 실패 응답 반영 혼입, REQ-10 (4) 미구현 | **해결** | 아래 참고 |
| 중요-2 지표 공백, 결측 검사 미보존 | **해결** | 아래 참고 |
| 중요-3 배치 처리 건수 누락 | **해결** | 아래 참고 |
| 중요-4 S5 12분 동시 재로그인 포화 | **해결** | 아래 참고 |
| 제안 1·2·4·8 | 반영 | 제안-4는 integrity.md의 "k6 hot-transfer 201 × 1,000원" 행, 제안-8은 README.md:389 "부하 발생" 행 |
| 제안-3 드롭 수를 요약에 추가 | **미반영** | 개선-1 참고 |
| 제안 5·6·7 | 한계로 기록 예정 | review.md에 기록이 남는지 메인이 확인할 것 |

**중요-1 근거**
- 키 기록: ec2.js:125-127에서 201이 아닌 이체의 키를 FAILKEY로 남기고, loadgen.sh:77에서 추출합니다.
- DB 대조: target.sh:250-271에서 멱등키 단위로 조인하고 상태별로 셉니다.
- 표 생성: run-ec2.py:400-444에서 자기 검증, 반영 건수, 잔차를 나눠 적습니다.
- 원자료 대조
  - S3는 k6를 중단하지 않습니다. 그래서 키 수와 체크 실패 수가 정확히 같습니다(2794/2856/2807). 502 키는 0건 반영, 잔차는 0입니다.
  - S1·S2에서 "키가 더 많음"을 SIGINT 탓으로 본 설명과 하한 계산식은 S3 결과로 뒷받침됩니다.
  - s1/r1: 1887 − (1887 − 1723) = 1723, 잔차 37로 summary와 일치합니다.
  - s2/r2: 7932/7257, 잔차 635로 일치합니다.

**중요-2 근거**
- 관리 포트 9095 분리: compose:46·55, metrics_proxy.py:24.
- 프록시: 백그라운드 재로그인(metrics_proxy.py:71-81), 경계에서 재시작하고 200 확인(target.sh:143-149)합니다.
- 결측 검사(k6_monitor.py:239-275)
  - 샘플 나이와 `up`을 5초 단위로 봅니다.
  - 조회 결과가 없으면 결측으로 판정하는 fail-closed 방식이라 헛통과하지 않습니다.
- 12개 회차 모두 gaps.txt가 있고 invalid=no입니다. commands.log의 check-artifacts도 전 회차 missing=none, metrics_gap_invalid=no입니다.
- s3/r2 spike_system의 Hikari 193·Tomcat 200·gc 0.0199가 실제 값으로 채워져 있습니다.

**중요-3 근거**
- target.sh:196-197이 batch-counts.txt를 자동으로 추출합니다.
- s5/r1 값이 summary.md:64-65와 가설 D(bottleneck-analysis.md:24)에 반영됐습니다: CTR 200, FDS 6639/2585, 대사 100000.

**중요-4 근거**
- ec2.js:73-78이 재로그인 시점을 VU별로 9~12분 사이에 분산합니다.
- s5 r1·r3의 outside_batches: Hikari 대기 0/6, Tomcat 8/15.
- relogin_per_minute: [9,64,71,60,5,23,33]과 [3,71,78,58,29,33]. 1차의 12분 동시 몰림은 사라졌습니다.

## 지적 (모두 개선, 완료를 막지 않음)

**개선-1. PERF-07·제안-3: 요약에 드롭 수가 없습니다. "반영했다"는 설명과 다릅니다.**
- 근거: summary.md·bottleneck-analysis.md에 "드롭·dropped" 문자열이 0건입니다.
- 원자료는 있습니다. analysis.json에 단계별 `dropped_iterations`가 있습니다(예: s1/r1 150 단계 1267, s2/r3 80 단계 70, s3/r2 20764).
- 영향: 무너짐 증거 중 하나가 요약에 드러나지 않습니다. 원자료가 있어 기준 위반은 아닙니다.
- 수정: 요약 S1·S2 무너진 단계와 S3 행에 드롭 수 한 열을 추가합니다.

**개선-2. 정합성 스냅샷을 api 정지 전에 찍습니다.**
- 근거: run-ec2.py:348-352의 순서가 integrity → failed-keys(약 2분 뒤) → stop-api입니다. s1-r1 기록 기준으로 integrity는 k6 종료 약 23초 뒤인 10:15:04, failed-keys는 10:16:51입니다.
- 영향: 그 사이 대기열의 이체가 커밋되면 "새 완료"는 적게, "실패 키 반영"은 많게 잡혀 잔차가 줄어듭니다.
- 현재 원자료에서는 잔차가 모두 0 이상이고, S3는 정확히 0이라 문제 정황은 없습니다.
- 수정: 후속 측정 전에 순서를 stop-api → integrity → failed-keys로 바꾸기를 권합니다.

**개선-3. S2 r1·r3 문구가 자체 정의와 어긋납니다.**
- 근거: summary.md:42 "실패 응답 반영은 0·1건", bottleneck-analysis.md:33 "0 / 1".
- s2/r3는 체크 실패 0에 키 2건(둘 다 SIGINT로 끊긴 요청), 그중 1건 반영입니다(integrity.md:6-7). 문서의 하한식으로는 실패 응답 중 반영이 0이고, 1건은 중단 순간 요청입니다.
- 수정: "실패 응답 0건, 중단 순간 끊긴 키 2건 중 1건 반영"으로 바꿉니다.

**개선-4. S5 "가용 메모리 최저" 열의 기준이 불분명합니다.**
- 근거: summary.md:59-62의 217/204/241MB는 대사 배치 구간 값입니다. 회차 전체 최저는 s5/r1 185MB, s5/r3 181MB입니다(analysis.json overall_system).
- 영향: 가설 E(메모리 여유) 해석에 쓰는 값이 약간 낙관적입니다.
- 수정: 열 이름을 "배치 구간 최저"로 바꾸거나 전체 최저를 병기합니다.

**개선-5. first-pass 문서에 결함 표시가 없습니다.**
- 근거: first-pass/summary.md:1-8과 first-pass/bottleneck-analysis.md:1-8에 결함·대체 표시가 없습니다. 이 문서에는 과장된 "실패 응답인데 반영" 수치(예: S2 393건)가 그대로 있습니다.
- 수정: 두 문서 맨 위에 "결함 있는 1차 기록, 기준선은 ../summary.md" 한 줄을 추가합니다. 보존 원칙(task.md:52)과 충돌하지 않습니다.

**개선-6. 커밋 479eefc의 메시지와 내용이 다릅니다.**
- 근거: 메시지는 a2f21f7과 같은데, 설명상 내용은 first-pass로 옮기는 git mv뿐입니다. 커밋 계획 17번(perf(results)에 포함)과도 다릅니다.
- 결과·코드에는 영향이 없고, 이력만 사실과 다르게 읽힙니다.
- 수정: reword 후 강제 push할지는 사용자가 결정합니다. 메인이 보고하기로 한 대로 처리하면 됩니다.

## REQ별 판정

| REQ | 판정 | 근거 |
| --- | --- | --- |
| REQ-01 | 충족 | env/infra.md: 같은 AZ 2d, t3.small unlimited·c7i.large, SSM Online 2대, SG 5개 포트 모두 부하 발생기 SG에서만, 0.0.0.0/0 0건, SSM 관리 정책만 |
| REQ-02 | 충족(기록 기준) | env/destroy.md: state 비어 있음, 태그 조회 지연분 7건을 직접 조회해 NotFound, 운영 plan 종료 코드 0 |
| REQ-03 | 충족 | environment-target.md(api·postgres·redis·caddy 실행 digest, 커밋 a2f21f7, compose 해시), README.md:381-396 차이 표(관리 포트·부하 발생 행 포함), 비밀값은 target.sh:37-41의 openssl rand |
| REQ-04 | 충족 | env/target/seed.log·baseline.log·disk.log, env/loadgen/prepare.log·restore-verify.log, 회차별 disk.log. 재측정 준비 순서가 commands.log:22-28과 일치 |
| REQ-05 | 충족 | 대시보드 패널과 up 패널, 12개 회차 gaps.txt invalid=no, pg_stat_activity.csv, postgres-excerpt, CloudWatch 크레딧(environment.md). Grafana UI 접속은 제안-6대로 한계 기록 필요 |
| REQ-06 | 충족 | s1 r1~r3 monitor·analysis: 무너짐 100(p95), 최대 지속 50, 단계별 p95·p99·오류율·처리량 |
| REQ-07 | 충족 | s2 analysis.json: lock_wait·Hikari·PG 락·병행 조회·전파 판정. r1~r3가 summary와 일치 |
| REQ-08 | 충족 | s3 spike·spike_system·recovery_seconds(75/100/145, 3/3 미회복), 결측 없음 |
| REQ-09 | 충족 | 부하 중·부하 없음 batch.txt(s5/r1 44/34, 72/48, 53/40), 배치 창과 배치 밖 지표, relogin_per_minute, batch-counts.txt, batch-explain.txt(Parallel Seq Scan 9,105ms) |
| REQ-10 | 충족 | 12개 integrity.md: (1)~(4)·자기 검증·잔차·차대 일치·새 불일치 0·대사 exit 0. 개선-2·3 참고 |
| REQ-11 | 충족 | 시나리오별 유효 3회, 무효 없음(gaps no, 발생기 CPU 27% 이하, S5는 KST 12~14시), S2 변동폭 한 단계 이내 |
| REQ-12 | 충족 | 전 회차 artifacts missing=none(필수 목록에 gaps·failed-keys 포함), 가설 A~E 판정, E 분리 한계(bottleneck:49), D 처리량(:24) |
| REQ-13 | 충족 | README 명령과 commands.log의 up/setup/prepare/dry-run/run/down/regen-integrity가 대응 |

## 기준 ID별 판정

| 기준 ID | 판정 | 비고 |
| --- | --- | --- |
| BE-03 | 충족 | |
| BE-04 | 충족 | 개선-2·3 |
| OPS-01 | 충족 | |
| OPS-03 | 충족 | 비밀 패턴 0건. review.md 기록은 메인이 |
| OPS-04 | 충족 | 매 회차 restore → readiness(9095) → 예열 → 경계 |
| OPS-06 | 충족 | |
| OPS-07 | 충족 | 결측 0, gaps.txt 12/12, Tomcat은 `name="http-nio-8080"`(k6_monitor.py:87, 대시보드 379줄) |
| SEC-05 | 충족 | |
| SEC-07 | 충족 | 제안-5(exporter 권한 한계)는 review.md에 기록 필요 |
| PERF-01 | 충족 | 회차 environment.md(발생기 유형·커밋·시작 조건·크레딧) |
| PERF-02 | 충족 | |
| PERF-03 | 충족 | |
| PERF-06 | 충족 | 개선-4 |
| PERF-07 | 충족(개선-1) | |

## 표본 대조 (모두 일치)
- S1 r1~r3: 무너진 요청률, 직전·무너진 p95, CPU, Hikari, Tomcat, 150 단계 타임아웃(6,138/1,113/1,976)과 오류율(43.7/7.7/13.9%)
- S2: r2 120 단계(오류 97.7%, 타임아웃 5,424), r3 전 열(1,561ms, 45%, 99, 1.12s, 9, 7 → 1,397ms)
- S3 r2 전 열
- S5 r1·r3: p95, 배치 시간, 배치 창 p95·CPU, 배치 밖 지표, relogin 범위, CTR EXPLAIN
- 정합성: S1·S2·S3·S5의 201 수, 새 완료 수, 키 수, 반영 수, 잔차가 summary·bottleneck 표와 일치

## 적용 제외 항목
OPS-02·05·08, SEC-01~04·06·08, BE-01·02·05~08, PERF-04(Q-04), PERF-05. 모두 이유가 타당합니다.

## 마지막 회차 메모
- 남은 문제는 모두 개선 수준이라 완료를 막지 않습니다.
- 메인이 review.md에 적기로 한 한계는 완료 전에 실제로 기록됐는지 확인해야 합니다: 제안-5 exporter 권한, 제안-6 Grafana UI 대체와 SSM 포트 포워딩 미확인, 제안-7 추적하지 않는 dryrun/ 산출물.
- 479eefc 처리 결정도 사용자 보고에 넣어야 합니다.

(원문 마지막 줄) 최종 판정: 통과 권고

## 요구사항별 검증

| 요구사항 ID | 구현 위치 | 테스트·실행 증거 | 판정 |
| --- | --- | --- | --- |
| REQ-01 | infra/terraform/modules/perf/main.tf, envs/perf/ | perf/results/ec2-baseline/env/infra.md(같은 AZ 2d, t3.small unlimited·c7i.large, SSM Online, 0.0.0.0/0 0건, SSM 정책만), terraform validate·fmt | 통과 |
| REQ-02 | perf/run-ec2.py cmd_down | env/destroy.md(state 비어 있음, 태그 지연 7건 직접 NotFound, 운영 plan 종료 코드 0 — 작업 전과 같음) | 통과 |
| REQ-03 | infra/compose/perf/docker-compose.target.yml·Caddyfile·target.env.example, perf/ec2/target.sh setup·environment, perf/README.md "운영과 다른 점" | env/target/environment-target.md(실행 digest), README 차이 표 | 통과 |
| REQ-04 | perf/sql/seed-10m.sql, perf/prepare-accounts.py, target.sh seed·baseline·snapshot·restore, perf/ec2/verify_login.py | env/target/seed.log·baseline.log·disk.log, env/loadgen/prepare.log·restore-verify.log | 통과 |
| REQ-05 | infra/compose/perf/docker-compose.loadgen.yml·loadgen/(대시보드·up 패널), metrics_proxy.py, k6_monitor.py gaps, target.sh sampler·logs, run-ec2.py cloudwatch_credit | 회차별 loadgen/prometheus/query_range.json.gz·gaps.txt(12/12 invalid=no), target/pg_stat_activity.csv·postgres-excerpt.log, environment.md 크레딧 | 통과 |
| REQ-06 | perf/k6/s1-mixed.js, k6_monitor.py watch·analyze | s1/r1~r3/loadgen/analysis.json·monitor.log, summary.md S1 | 통과 |
| REQ-07 | perf/k6/s2-hot-account.js, k6_monitor.py | s2/r1~r3/loadgen/analysis.json, summary.md S2 | 통과 |
| REQ-08 | perf/k6/s3-spike.js, k6_monitor.py analyze s3 | s3/r1~r3/loadgen/analysis.json, summary.md S3 | 통과 |
| REQ-09 | run-ec2.py run_batches·noload, target.sh batch·explain, extract_slow_sql.py, k6_monitor.py outside_batches·relogin_per_minute | s5/r1~r3/target/batch.txt·batch-counts.txt, noload/target/batch.txt·batch-explain.txt, analysis.json | 통과 |
| REQ-10 | target.sh integrity·failed-keys, lib/ec2.js FAILKEY, loadgen.sh 추출, run-ec2.py copy_file·write_integrity | 12개 회차 integrity.md·target/integrity-target.txt·failed-keys-target.txt, 대사 batch 로그 exit 0 | 통과 |
| REQ-11 | 측정 조건 "반복·변동", run-ec2.py check_artifacts, k6_monitor.py gaps | 전 회차 artifacts.txt(missing=none, metrics_gap_invalid=no), 발생기 CPU ≤27%, summary.md | 통과 |
| REQ-12 | run-ec2.py REQUIRED·check_artifacts, 결과 문서 | 회차 폴더 파일, summary.md, bottleneck-analysis.md(가설 A~E, E 한계, D 처리량) | 통과 |
| REQ-13 | perf/README.md "EC2 부하 테스트" 절, perf/run-ec2.sh·run-ec2.py | env/commands.log(up→setup→prepare→dry-run→run→down→regen-integrity) | 통과 |

## 지적별 처리

- 2/3 중요-1~4: 보완 후 재측정으로 해결(3/3에서 해결 확인). 처리 커밋 151cccc·91dd857·891fd69·f158174·b8e7cd2·c9956c3·a2f21f7·b1b2c1c, 재측정 0759923, 문서 bd5e671.
- 2/3 제안-1·2·4·8: 문서 반영(3/3 확인). 제안-3(드롭 수)은 미반영 → 아래 3/3 개선-1과 같이 후속.
- 2/3 제안-5(exporter 권한): 한계로 기록 — postgres-exporter는 앱 계정(jbank)으로 접속하고, cAdvisor의 `/var/run:ro` 마운트는 docker.sock API 호출을 막지 못한다. 대상은 공개 인바운드가 없고 측정 때만 존재하는 일회성 환경이라 수용했다.
- 2/3 제안-6(Grafana UI): 한계로 기록 — 회차마다 대시보드 전 패널 쿼리를 query_range로 저장하고 수집 대상별 결측을 gaps.txt로 검사해 "같은 시간축으로 본다"를 대체했다. Grafana 화면 캡처와 SSM 포트 포워딩 접속은 이번 세션에서 확인하지 않았다.
- 2/3 제안-7(작업 트리 dirty): 한계로 기록 — 결함이 있던 첫 드라이런 산출물(`perf/results/ec2-baseline/dryrun/` 바로 아래)은 커밋하지 않고 로컬에 남아 있다(하네스가 재귀 삭제를 막아 사용자가 지울지 결정).
- 3/3 개선-1(요약 드롭 수), 개선-3(S2 r3 문구), 개선-4(S5 메모리 열 이름), 개선-5(first-pass 머리말): 검사·리뷰 이후 파일을 바꾸면 검증 지문이 바뀌므로 이번 작업에서는 고치지 않고 후속 문서 정리로 넘긴다(원자료에 값이 있어 기준 위반은 아님).
- 3/3 개선-2(정합성 스냅샷 순서): 후속 측정(입금 비동기 재측정) 전에 stop-api → integrity → failed-keys 순서로 바꿀 것. 현재 원자료에서 잔차는 모두 0 이상, S3는 0.
- 3/3 개선-6(479eefc 메시지 불일치): 사용자에게 보고하고 reword·강제 push 여부를 묻는다.

## 완료 기준별 근거

- REQ-01~13: 위 표와 3/3 verifier 판정 모두 충족.
- perf 환경 destroy·잔여 0: env/destroy.md(2026-09-28T14:06).
- 기존 필수 검사: verify-003 6개 통과(snapshot 08c9bb650a3e17d5f1d61f25f4f2a606bd1e0c27c30d2c73ed5bde463b501569).
- 후속 작업 입력: bottleneck-analysis.md 가설 A 확인과 S2 비교 기준(무너짐 80, 최대 지속 60, 이체 p95 1,561ms, 병행 조회 8→1,397ms, 전파 3/3).

## 성능 테스트 확인

- 필요: 예(작업 본체). 조건: task.md 측정 조건(Q-07 반영). 부하 발생기 c7i.large, CPU ≤27%, 무효 회차 없음.
- 측정값(대표): S1 무너짐 초당 100건·최대 지속 50(CPU 83%), S2 무너짐 80·최대 지속 60(락 대기 0.77s, 전파 3/3), S3 처리 172.4/s·오류 88.1%·회복 100초(3/3 미회복), S5 초당 35건에서 무너짐 없음·CTR Parallel Seq Scan 9,105ms·배치 1.31~1.54배.
- 변경 전후 비교: 이 작업이 후속 작업의 변경 전 결과. 1차 측정(first-pass/)과 비교하면 S1은 같고, S2는 1차 90/70에서 80/60, S3 회복은 85초에서 100초(1차는 지표 공백·재로그인 몰림 결함이 있어 비교 기준으로 쓰지 않음).
- 로그 위치: perf/results/ec2-baseline/<시나리오>/<회차>/, summary.md, bottleneck-analysis.md.

## 발견한 문제

- 작업 범위의 차단·중요 문제 없음.
- 범위 밖 발견(작업 칩으로 분리): 운영 backend-cd SSM 배포 실패, 운영 `/actuator/prometheus` 401(운영 api 지표 미수집 가능성), CTR 배치 기준일이 컨테이너 UTC 자정 경계.
- 측정으로 드러난 금융 위험: 타임아웃 응답을 받은 이체가 사실상 전부 실제로 완료됨(중복 송금 위험) — 후속 개선 후보.

## 판정과 이유

결과 리뷰 3/3 verifier 통과 권고, REQ-01~13 충족, 필수 검사 통과, 미해결 차단·중요 지적 없음. 개선 6건은 완료를 막지 않으며 처리 방향을 위에 적었다. 통과로 등록한다.

## 확인하지 못한 부분

- verifier는 gz 원자료(query_range·k6 로그)를 읽지 못해 로그인 서버 p95(315/715ms)와 가설 E 사고 수치는 메인만 계산했다.
- AWS 실제 상태와 git 이력은 verifier가 기록으로만 확인했다(메인은 명령으로 확인).
- Grafana UI 화면·SSM 포트 포워딩 접속은 확인하지 않았다.
