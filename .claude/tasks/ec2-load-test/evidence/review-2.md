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

## 요구사항별 검증

(수정 후 재리뷰 결과로 작성)

## 지적별 처리

(수정 후 작성)
