# 검토 결과

## 1회차

회차 snapshot: af97e9cb37f427964fddce3242866acfeff1f26236b1ec0048af0f30a22e3eae
회차 판정: 수정 필요
호출: verifier a2caab863f58d3d99, 2026-09-30T16:11Z(review-begin 1/3 직후), 검사 verify-002

### verifier 응답 원문

> # 결과 리뷰: async-credit, 1회차(최초)
>
> ## 1. 검증 대상
> - 모드: 결과 리뷰 1회차
> - 작업 ID: async-credit
> - 검증 대상 snapshot: `af97e9cb…3eae`(evidence/checks.json, verify-002). HEAD는 f979270이고, 작업 파일만 dirty 상태입니다.
> - 브랜치: perf/async-credit
>
> ## 2. 읽은 자료와 확인하지 못한 범위
>
> **읽은 자료**
> - 기준·작업 문서: docs/testing-policy.md, task.md, plan-review.md(해석 기준 I-A~I-F), progress.md, state.json, .claude/checks.json
> - 검사 증거: evidence/checks.json, verify-001·002의 check-5.log, changes.txt, logs/perf-tool-tests.log, logs/e2e.log
> - 백엔드 테스트 결과: apps/jbank-api/build/test-results/test/*.xml
> - 운영 코드: V18, PendingCredit, PendingCreditRepository, CreditApplier, PendingCreditWorker, TransferService, AccountRepository, AccountService(changeStatus·close), TransactionHistoryService, LedgerReconciliationTasklet·JobConfig, application(-batch).yml
> - 테스트 코드: PendingCreditApplyScenarioTest, ReceiverLockScenarioTest, AccountStatusChangeRaceScenarioTest, AbstractConcurrencyTest, PendingCreditWorkerConfigTest, LedgerReconciliationTaskletTest, LedgerReconciliationJobIntegrationTest, Transfer·Otp·TransactionHistory 통합 테스트 일부
> - 프론트: domain-error-map(.test).ts
> - 측정 도구: perf/ec2/sql/credit-integrity.sql, target.sh(관련 부분), run-ec2.py(credit_lag·write_integrity), perf/ec2/tests 2개, 대시보드 JSON
> - 문서: ADR 0012, docs/03 API설계, docs/04 화면플로우차트, README 77행
> - 결과: ec2-async-credit/summary.md, r1~r6의 analysis.json·credit-lag.md, r4·r5 integrity.md, 전 회차 boundary.env·recon 로그, r5 gaps.txt, env 기록(environment-target.md·commands.log)
> - 기준선: ec2-hot-account/summary.md, 기준선 r1~r7의 80~110 단계 p95
>
> **적용 기준 ID**
> BE-01·02·03·04·05·07, PERF-01·02·03·04(대체)·06·07, OPS-05·06·07, FE-01·03, E2E-06, SEC-05
>
> **확인하지 못한 범위**
> - 테스트는 제가 직접 돌리지 않았습니다. 백엔드 결과는 build/test-results XML로 확인했습니다. 12:44~12:46Z에 실행됐고, 새 테스트 클래스를 포함해 전부 통과였습니다. 두 verify에서는 모두 `:test UP-TO-DATE`였습니다.
> - query_range.json.gz(바이너리)는 읽지 못했습니다. 그래서 요약의 steal 최대값과 받는 계좌 락 대기 0.13초는 검증하지 못했습니다.
> - git 이력(af89f3c diff, 490ed10..de2b827 앱 변경)은 명령 실행 없이 확인할 수 없었습니다.
> - Hibernate가 PESSIMISTIC_WRITE를 FOR NO KEY UPDATE로 만든다는 사실은 progress·ADR의 기술뿐입니다. 프로브 파일은 삭제된 상태입니다.
> - 다음 두 가지도 기록으로만 봤습니다: SEC-05 스캔(적중 0, 257개 파일), destroy 후 잔여 0.
>
> ## 3. 판정: 수정 필요
>
> ---
>
> ## 4. 지적 사항
>
> ### [차단] B1. REQ-07 목표 미달(r5)을 사용자 결정 없이 보고로만 처리함
> - **기준:** REQ-07("무너지기 전 단계에서 모두 1초 이내"), Q-05, verification.md("성능 테스트의 기준 미달은 통과가 아니다")
> - **근거:** `perf/results/ec2-async-credit/s2f/r5/credit-lag.md`
>   - r5의 무너짐 지점은 200입니다.
>   - 무너지기 전 단계인데 1초를 넘은 곳이 두 곳입니다: 160 단계 p95 2,046ms, 180 단계 p95 1,270.8ms.
>   - 같은 160 단계에서 hikari_pending_max가 148이었습니다(analysis.json). 워커가 API와 같은 커넥션 풀(10개)을 쓰다가 밀린 것으로 보입니다.
> - **영향:** 사용자가 정한 수치 목표가 6회 중 1회 충족되지 않았습니다. task.md·progress.md 어디에도 이를 수용한다는 사용자 결정이 없습니다.
> - **선택지(사용자 결정 필요)**
>   - (a) 미달을 수용하거나 REQ-07 적용 범위를 다시 정합니다. 예: 기준선이 무너진 구간 이하, 또는 회차 기준 판정. 그다음 task.md를 고치고 계획을 다시 검증받은 뒤 start를 갱신합니다.
>   - (b) 워커를 개선하고 재측정합니다. 예: 워커 전용 커넥션·풀 분리, 주기·묶음 크기 조정.
>   - (c) 미완료로 보류합니다.
>
> ### [중요] M1. 요약·ADR·README의 수치·문구가 원자료와 다르고, 기준선이 넘긴 비교 항목이 빠짐
> - **기준:** REQ-09("무관한 조회 p95·전파 … 기준선과 비교"), REQ-10, 기준선 summary 79행("비교할 때 쓸 값: … 전파 7/7")
>
> **a. 전파 비교가 빠졌습니다**
> - r1~r6 analysis.json의 `propagation_at_collapse`가 6/6 모두 true입니다. 기준선은 7/7이었으니 무너질 때의 전파는 그대로입니다.
> - 그런데 summary의 회차 표와 비교 표 어디에도 전파 항목이 없습니다. 불리한 결과가 빠진 셈입니다.
>
> **b. 같은 부하 단계 비교표가 r6을 빼고 계산됐습니다**
> - 표 제목은 "회차별 값의 중앙값", 비교 표 제목은 "개선 후(6회)"입니다.
> - 원자료로 다시 계산하니 표의 값은 r1~r5 다섯 회차의 중앙값과 일치합니다.
> - r6을 넣어 다시 계산한 값은 다음과 같습니다.
>
> | 항목 | 표의 값 | r6 포함 재계산 |
> | --- | --- | --- |
> | 100 rps 이체 p95 | 28ms | 33ms(r1 23.6, r2 37.2, r4 33.3, r5 20.0, r6 62.4) |
> | 110 rps 이체 p95 | 78ms | 66ms |
> | 110 rps 무관한 조회 p95 | 32ms | 29ms |
> | 110 rps 커넥션 대기 | 30 | 15 |
>
> - 기준선 쪽 값(71·238·395·295)은 유효 6회 기준으로 맞습니다.
> - ADR 0012 39~41행과 README 77행이 "28ms"를 인용합니다.
>
> **c. 반영 지연 문장에 조건이 빠졌습니다**
> - summary 59행: "초당 150건 이하 단계는 모든 회차에서 p95 404ms 이하"
> - 반례: r1 90 단계 823.6ms, r6 120 단계 3,206ms, r4 120 단계 12,357ms, r4 130 단계 53,618ms.
> - "무너지기 전 단계"라는 조건을 붙여야만 맞는 문장입니다.
> - ADR 42행 "1~2초까지 늘었다"는 과부하 때 수십 초로 늘어나는 한계를 드러내지 않습니다.
>
> **d. 회복 회차를 잘못 적었습니다**
> - summary 51행: "대부분 … 다음 단계에서 회복(r1·r3·r6)"
> - 기준(p95 ≤ 200ms)으로 회복한 회차는 r1·r2·r3, 즉 3/6입니다. r6은 다음 단계가 235ms라 회복이 아닙니다. "대부분"도 맞지 않습니다.
>
> **e. 발생기 CPU 최대값이 틀렸습니다**
> - summary 29행: "발생기 CPU 최대 13%"
> - r5 analysis.json의 `loadgen_cpu_max`는 0.2048(20.5%)입니다. 유효성 기준(80%)에는 영향이 없습니다.
>
> **f. steal 상관의 반례가 빠졌습니다**
> - progress 24행에는 "r1 40·50 단계는 steal 0.155·0.175에도 정상"이라는 반례가 있는데 summary에는 없습니다.
> - summary의 값은 최대값인데 analysis.json의 `target_steal_avg`는 0.04~0.08입니다. 어떤 지표인지 명시가 필요합니다.
>
> - **영향:** 개선 방향이라는 결론은 유지됩니다. 하지만 비교 근거가 명세와 다르게 계산됐고, 불리한 항목이 빠져 과장으로 읽힙니다.
> - **필요한 수정:**
>   - r6을 넣어 단계 표를 다시 계산합니다.
>   - 전파 항목을 추가합니다(6/6 대 7/7).
>   - c~f 문장을 고치고, ADR·README에 인용한 값을 맞춥니다.
>
> ### [중요] M2. OPS-06·07, REQ-07 지표 수집, PERF-04 대체의 증거가 없음
> - **기준:** OPS-06·07 행("미반영 대기 수·최고 나이·반영 지연 시계열 수집(결측 0)", 증거 위치 analysis.json), PERF-04 대체("s2f 중 미반영 대기 수 시계열로 적체 관측"), REQ-07 둘째 문장
> - **근거**
>   - export는 대시보드 패널 쿼리만 저장합니다(k6_monitor.py 316~327행).
>   - 그런데 `infra/compose/perf/loadgen/provisioning/dashboards/json/jbank-perf.json`에는 jbank_credit_* 패널이 없습니다(grep 결과 0).
>   - analysis.json에도 해당 필드가 없습니다. 인스턴스는 이미 destroy됐습니다.
>   - 간접 근거는 있습니다. 전 회차 gaps.txt에서 api 결측이 0이고, 풀이 고갈된 r4 130 단계(대기 192)에서도 api 결측이 없었습니다. "스크레이프가 DB를 기다리지 않음"을 뒷받침합니다.
> - **영향:** 합의한 기준의 필수 근거가 부족합니다. 적체 시계열(PERF-04 대체)은 전혀 관측되지 않았습니다.
> - **필요한 조치:**
>   - 대시보드에 jbank_credit_* 패널을 추가하고, 다시 측정하거나 로컬에서 수집을 확인합니다. 최소한 부하 중 `/actuator/prometheus`에 세 지표가 노출·수집되는 실행 로그가 필요합니다.
>   - 또는 증거 공백을 사용자가 수용한다는 결정을 기록합니다.
>
> ### [중요] M3. 계획에 있던 테스트 사례가 빠짐
> - **REQ-06 "대사 중 반영이 끼어도 오탐 없음"(task.md 159행)**
>   - 이 사례의 테스트가 없습니다. REPEATABLE READ 설정(LedgerReconciliationJobConfig 40~47행)이 실제로 적용되는지 검증하는 테스트가 없습니다.
>   - 참고로 "미반영 포함 전역식"은 실제 DB 근거가 있습니다. r4·r5 recon 로그에서 대변 + 미반영 − 차변이 모두 20,000,000,000(시드 차이)으로, 미반영이 0인 회차와 같습니다.
> - **BE-07 "연속 실패 로그 억제"(task.md 140행)**
>   - PendingCreditWorker의 catch·억제 경로를 검증하는 테스트가 없습니다. 실패 격리는 CreditApplier 단위로만 확인됐습니다.
> - **필요한 조치:** 두 사례를 추가하거나, 제외 사유를 기록합니다.
>
> ### [중요] M4. 계획 외 SQL 수정(af89f3c) 뒤 측정 도구 검사를 다시 돌린 기록이 없음
> - **기준:** verification.md("코드 변경 후 이전 증거는 재사용하지 않는다"), I-F
> - **근거**
>   - logs/perf-tool-tests.log에는 12:49Z(base=2d26adb) 실행분만 있습니다.
>   - r5·r6의 중복·유실 판정은 수정된 SQL로 만들어졌습니다.
>   - integrity-sql-test.sh는 `b_le=0`으로 호출하므로, 새로 넣은 경계 필터(`entry_id > :b_le`)는 검사되지 않습니다.
>   - 제가 SQL을 읽어 본 바로는 로직이 맞습니다. 다만 실행 증거는 없습니다.
> - **필요한 조치:** 검사 두 개를 다시 실행해 로그를 남기고, 경계 이전 원장 행이 있는 사례를 추가하기를 권합니다.
>
> ### [중요] M5. REQ-10 개발일지가 없음
> - **근거:** docs/devlog에 이 작업의 파일이 없습니다. 커밋 계획 11번이 아직 남아 있습니다.
> - **필요한 조치:** 완료 전에 작성하고 재리뷰에서 확인받아야 합니다.
>
> ### [개선] 완료를 막지 않는 제안
> - **I1. 해지 계좌 방어 경로의 로그 폭주**
>   - CreditApplier 63~66행의 CLOSED 방어 경로는 억제 없이 `log.error`를 남깁니다. 발생하면 200ms마다 오류가 쌓입니다.
>   - 예외를 던져 워커의 1분 억제를 타게 하거나, 여기서도 억제하는 방안을 권합니다. 설계상 도달하지 않는 경로입니다.
> - **I2. 워커 한 곳이 막히면 전체가 멈춤**
>   - `lockForBalanceUpdate`에는 lock_timeout이 없습니다. 한 계좌가 오래 잠기면(예: 배치) 워커 단일 스레드 전체가 멈춥니다.
>   - `SET LOCAL lock_timeout`이나 계좌 락의 `SKIP LOCKED`를 검토해 보세요.
> - **I3. 계좌 선택 순서가 없음**
>   - `findAccountIdsWithUnapplied`는 ORDER BY 없이 limit 100입니다. 미반영 계좌가 계속 100개를 넘으면 일부 계좌가 굶을 수 있습니다.
>   - 한계를 주석으로 남기거나 오래된 순으로 정렬하기를 권합니다.
> - **I4. 측정 공정성 기록**
>   - 개선 후 이미지는 떠다니는 `eclipse-temurin:21-jre-alpine`으로 대상 인스턴스에서 빌드했습니다. environment-target.md의 이미지 digest가 비어 있고 JRE 버전도 기록이 없습니다.
>   - 기준선 이미지 490ed10과 작업 base de2b827 사이의 앱 변경 유무도 summary에 적어 두기를 권합니다.
> - **I5. E2E 상품 가입 실패의 원인 기록**
>   - product 패키지는 transfer·credit을 참조하지 않습니다.
>   - 테스트는 같은 시드 고객으로 반복 실행하면 j-farm 계약이 2건이 되는 구조라, strict mode 위반은 DB 누적 때문으로 보입니다.
>   - 이 근거를 progress에 남기고 별도 과제로 분리하기를 권합니다.
> - **I6. API 설계 문서 문구**
>   - docs/03 479행 "반영은 보통 1초 안에 끝나므로"는 OTP 대기 입금이면 최대 3분, 과부하 때는 더 길어집니다. 문구 보완을 권합니다.
> - **I7. application.yml 주석**
>   - 39행 주석은 "코드 기본값은 꺼짐이라 테스트·배치에서는 켜지 않는다"입니다.
>   - 실제로는 테스트는 @TestPropertySource로, 배치는 application-batch.yml로 끕니다. 주석이 오해를 부릅니다.
> - **I8. verify 로그만으로는 무엇이 실행됐는지 안 보임**
>   - 두 verify 모두 백엔드 `:test UP-TO-DATE`라 로그에 테스트 수가 없습니다.
>   - `--rerun-tasks`를 쓰거나 test-results 요약을 첨부하면 증거가 명확해집니다.
> - **I9. 같은 계좌의 송금과 반영이 동시에 도는 테스트가 없음**
>   - 한 계좌가 보내면서 동시에 반영되는 경우를 테스트하지 않습니다. 1,000건 대사는 반영을 마지막에 한 번 합니다. 테스트 추가를 권합니다.
> - **I10. 일시 정지 원인 가설(확인 안 됨)**
>   - 핫 행에 KEY SHARE 여러 개와 주기적인 UPDATE가 겹치면 MultiXact 비용이 생깁니다. 이것이 일시 정지에 기여하는지는 조사되지 않았습니다.
>   - summary는 steal 상관만 적고 있습니다.
>
> ---
>
> ## 5. 금융 정합성 검증(코드 읽기로 확인, 결함 없음)
> - **중복 반영**
>   - 워커는 계좌 행을 NKU로 잡은 뒤 새 문장으로 `applied_at IS NULL` 행을 `FOR UPDATE SKIP LOCKED`로 가져옵니다. 대기하던 반영기는 앞 트랜잭션 커밋 뒤 최신 행을 봅니다.
>   - `transaction_id UNIQUE`도 있습니다. 동시 4개 반영 테스트에서 50건이 한 번씩만 반영됐습니다.
> - **유실**
>   - 차변 원장·입금 대기·COMPLETED가 한 트랜잭션에 기록됩니다.
>   - EC2 6회 모두 이체별 대기 불일치 0, 대변 불일치 0, 새 불일치 계좌 0입니다.
> - **해지 경합**
>   - 해지는 FOR UPDATE로 처음 읽고, 그 뒤 hasIncomingInProgress를 확인합니다.
>   - 이체·OTP 완료의 수신 KEY SHARE와는 순서가 정해집니다. 반영(NKU) 뒤에는 최신 잔액을 보고 ACC_008로 거절합니다.
>   - ReceiverLockScenarioTest의 20회 반복 두 종과 AccountStatusChangeRaceScenarioTest가 이를 확인합니다. progress에 따르면 수정 전 코드로는 실패를 재현했습니다.
> - **락 모드**
>   - 모두 네이티브 쿼리로 고정했습니다: 송금 NKU, 수신 KEY SHARE, 워커 NKU, 해지·상태 변경 FOR UPDATE.
>   - 수신 엔티티는 변경하지 않아 UPDATE가 나가지 않습니다. 워커의 전체 컬럼 UPDATE도 키가 그대로라 NKU입니다. 교착 경로는 찾지 못했습니다.
> - **배치 JVM의 워커 꺼짐(실제 근거)**
>   - r4 대사의 "1분 넘은 미반영 986건"이 정합성 시점 미반영 986건과 같습니다. r5도 246건으로 같습니다.
>
> ## 6. REQ별 대조
>
> | REQ | 판정 | 근거 |
> | --- | --- | --- |
> | REQ-01 | 충족 | TransferServiceIntegrationTest 110~149행, ReceiverLock "NKU 3초 중 이체 2초 미만" |
> | REQ-02 | 충족 | OtpVerificationServiceIntegrationTest 112~132행 |
> | REQ-03 | 충족 | PendingCreditApplyScenarioTest 6개(동시 반영, 트리거 실패 격리·재시도, 500+20건) |
> | REQ-04 | 충족 | TransactionHistory 215~238행, 이체 직후 수신 잔액 불변 |
> | REQ-05 | 충족 | 통합 테스트 ACC_012 두 건, ReceiverLock 3종, StatusChangeRace 2종, 프론트 jest |
> | REQ-06 | 부분 | 전역식은 실제 DB 근거 있음. 대사 중 반영 사례 없음(M3) |
> | REQ-07 | 미충족 | r5 목표 초과(B1), 지표 수집 증거 없음(M2) |
> | REQ-08 | 충족 | 5종 통과(XML) |
> | REQ-09 | 부분 | 회차 기록은 있음. 전파 비교 누락, 단계 표 계산 기준 불일치(M1) |
> | REQ-10 | 부분 | ADR·README·perf 요약 있음. 수치 정정 필요(M1), 개발일지 없음(M5) |
> | REQ-11 | 충족 | ConfigTest 3개. 계획한 "운영 설정 컨텍스트" 대신 yml 읽기로 확인했지만, EC2 운영 이미지에서 반영이 실제로 일어나 보강됨 |
>
> ## 7. 계획과 다르게 처리한 부분
> - **대상 인스턴스 빌드:** 타당합니다. packages 권한이 없고 :latest를 덮을 위험이 있다는 이유가 맞습니다. 기록 보완만 필요합니다(I4).
> - **r5 후처리 재실행:** 타당합니다.
>   - 00:11 이전에 api를 멈춰 DB 상태가 고정돼 있었습니다.
>   - integrity → failed-keys → credit-dump → batch를 원래 순서대로 다시 실행했습니다(commands.log 457~475행).
>   - integrity-target.txt는 24줄로 새로 쓰였습니다.
>   - 다만 도구 검사 재실행 기록이 없습니다(M4).
> - **r6 추가:** 기준선 규칙(범위가 한 단계를 넘으면 1회 추가)과 같습니다. 대표 중앙값(115, 105, 115.05)은 원자료와 일치합니다. 단계 표만 r6이 빠졌습니다(M1b).
> - **E2E 상품 가입 실패를 범위 밖으로 판단:** 타당합니다(I5). 이체 E2E는 첫 실패 원인(/tmp/backend.log 없음)을 보존한 채 다시 돌려 4/4 통과했습니다.
> - **REQ-07 r5 초과 보고:** 사실대로 보고한 점은 적절합니다. 하지만 목표 미달을 어떻게 처리할지는 사용자 결정이 필요합니다(B1).
>
> ## 8. 적용 제외 항목
> BE-06, BE-08, PERF-05, E2E-01~05, OPS-01~04·08, AI, SEC-05 외 보안 항목의 제외는 계획 리뷰 때 판단과 같고 여전히 타당합니다. 빠진 적용 영역은 없습니다. PERF-04 대체는 수용된 방식이지만 실제 증거가 없습니다(M2).
>
> ## 9. 이전 지적(계획 리뷰의 해석 기준 I-A~I-F)
>
> | 지적 | 상태 | 확인 내용 |
> | --- | --- | --- |
> | I-A | 반영 | REQ-11을 포함해 대조 |
> | I-B | 반영 | KEY SHARE 의미로 테스트 |
> | I-C | 반영 | |
> | I-D | 반영 | ACC_012 409, 검사 순서 009→008→010→012, 화면플로우차트 232행, @Operation 미변경 |
> | I-E | 반영 | @SpringBootTest 5개에 워커 끄는 속성, 프론트 테스트 새 파일 |
> | I-F | 부분 | 로그 머리말은 갖춤. af89f3c 뒤 재실행 기록 없음(M4) |
>
>
> (원문 마지막 줄) 최종 판정 문구: 수정 필요

### 메인 처리 내역

- B1: 사용자 결정(2026-10-01) "목표 범위를 명확히 하고 수용". REQ-07 판정 범위를 기준선이 버틴 부하(초당 150건 이하)의 무너지기 전 단계로 고치고 그 위는 기록만 함. task.md 수정, 계획 재검증 후 start.
- M2: 사용자 결정(2026-10-01) "패널 추가 + 로컬 부하로 수집 확인". 측정 대시보드에 jbank_credit_* 패널 추가, 로컬 Prometheus 수집 로그를 증거로 남김.
- M1·M3·M4·M5와 개선 일부는 2회차 처리 내역에 적음.


## 2회차

회차 snapshot: 18a3d0e3db3d09b8847a1d29d52155734201c2bba61f022eebeffe54f6f5c661
회차 판정: 수정 필요
호출: verifier a5207ba994d2d3b8d, 2026-10-01(review-begin 2/3 직후), 검사 verify-003

### verifier 응답 원문

> # 결과 리뷰 2회차(재리뷰 1회차) 보고: async-credit
>
> ## 1. 검증 대상
> - **모드:** 결과 리뷰 2회차(재리뷰 1회차)
> - **작업 ID:** async-credit
> - **대상 snapshot:** `18a3d0e3db3d09b8847a1d29d52155734201c2bba61f022eebeffe54f6f5c661`
>   - 출처: evidence/checks.json, verify-003, HEAD 68d9bd5
> - **이번 verify에서 백엔드 테스트가 실제로 돌았는지**
>   - 이번에는 `:test`가 실행됐습니다(UP-TO-DATE 아님). check-5.log 8행, "1 executed"로 확인했습니다.
>   - test-results-summary.log의 타임스탬프(01:21~01:23Z)가 verify 실행 구간(01:20:38~01:23:19Z) 안에 있습니다.
>   - 결과는 241개 중 실패 0입니다. 새로 넣은 `LedgerReconciliationJobIntegrationTest` 7개, `PendingCreditWorkerTest` 2개가 포함돼 있습니다.
>
> ## 2. 읽은 자료와 확인하지 못한 범위
> **읽은 자료**
> - 작업 문서: task.md, review.md, progress.md, plan-review.md(5회차 V-1~V-3)
> - 검사 증거: evidence/checks.json, verify-003의 check-5.log·changes.txt
> - 새 로그: logs/recon-rc-control.log, perf-tool-tests.log, credit-metrics.log, test-results-summary.log
> - 새 테스트: LedgerReconciliationJobIntegrationTest, PendingCreditWorkerTest
> - 운영 코드: PendingCreditWorker, LedgerReconciliationTasklet
> - 측정 도구: integrity-sql-test.sh, credit-integrity.sql, jbank-perf.json 패널 19~22, k6_monitor.py export
> - 문서: summary.md, ADR 0012, README 77행, docs/03 477·479행, 개발일지
> - 원자료
>   - 개선 후 r1~r6: credit-lag.md, r5 credit-lag.json, analysis.json
>   - 기준선 r1~r7: analysis.json
>   - 측정 환경: environment-target.md
>
> **적용 기준 ID**
> BE-01·02·03·04·05·07, PERF-01·02·03·04(대체)·06·07, OPS-05·06·07, FE-01·03, E2E-06, SEC-05
>
> **확인하지 못한 범위**
> - 테스트, 로컬 Prometheus 수집, 대조군 실행은 제가 직접 돌리지 않았습니다. 로그와 XML 요약으로만 확인했습니다.
> - `git log 2d26adb..HEAD -- apps/jbank-api/src/main`이 비어 있다는 사실은 명령을 실행할 수 없어 메인 기록에 의존했습니다. 측정 이미지가 perf-f535020이라는 점은 environment-target.md 5행에서 확인했습니다.
> - query_range.json.gz(steal 최대값, 수신 락 대기 0.13초)는 1회차와 마찬가지로 읽지 못했습니다.
> - E2E-06은 이전 실행(logs/e2e.log)을 재사용한 것입니다. 이후 프론트와 운영 코드 변경이 없으므로 타당하다고 봅니다.
>
> ## 3. 판정: 수정 필요
> - 이번에 새로 생긴 [중요] 1건 때문입니다. 1회차 M1을 고치면서 새로 넣은 숫자가 원자료와 다릅니다.
> - 고치는 데는 문서의 숫자 4개만 바꾸면 되고, 재측정은 필요 없습니다.
> - 1회차 지적(B1, M1~M5)은 이 1건을 빼면 모두 해결됐습니다.
>
> ## 4. 지적 사항
>
> ### [중요] N1. 비교표의 기준선 "대상 CPU 평균" 열이 원자료 중앙값과 다르고, 90 단계에서는 증감 방향이 반대로 보임
> - **기준:** REQ-09(기준선 대비 비교), REQ-10(perf 요약), 1회차 M1(문서 수치는 원자료와 일치해야 함)
> - **위치:** perf/results/ec2-async-credit/summary.md 49~52행, 마지막 열
> - **근거:** 기준선 유효 r2~r7 analysis.json의 `target_cpu_avg`를 표 머리말과 같은 규칙(그 단계를 실제로 돈 회차의 중앙값)으로 다시 계산했습니다.
>
> | 요청률 | 회차별 값(%) | 실제 중앙값 | 표에 적힌 값 |
> | --- | --- | --- | --- |
> | 80 | 41.8, 42.0, 42.3, 43.8, 43.9, 47.0 | **43** | 40 |
> | 90 | 42.1, 44.4, 46.8, 48.5, 50.5, 52.1 | **48** | 50 |
> | 100 | r2 61.6, r3 47.9, r4 52.7, r6 49.1 | **51** | 50 |
> | 110 | r2 61.1, r3 52.9, r4 60.3, r6 57.6 | **59** | 60 |
>
> - **원인 추정:** 기준선 값만 소수 첫째 자리(0.43→0.4)로 반올림하고, 개선 후 값(46, 49, 53, 59)은 1% 단위로 적었습니다.
>   - 개선 후 값은 제가 다시 계산한 값과 모두 일치합니다(46.4, 49.35, 53.1, 59.2).
>   - 같은 열의 다른 값(이체 p95, 무관한 조회 p95, 커넥션 대기)도 양쪽 모두 원자료와 일치합니다.
> - **영향**
>   - 80 단계: 실제 +3%p가 +6%p로 보입니다.
>   - 90 단계: 실제 48→49로 늘었는데 50→49로 줄어든 것처럼 보입니다.
>   - 요약은 "다음 병목은 CPU"라고 결론 내리므로, 같은 부하에서 CPU가 늘었는지가 해석에 직접 쓰이는 값입니다.
>   - 1회차 M1과 같은 종류의 원자료 불일치가 이번 수정에서 다시 생긴 회귀입니다.
> - **필요한 수정**
>   - 기준선 CPU를 43 / 48 / 51 / 59로 고칩니다. 또는 양쪽을 같은 정밀도로 맞춥니다.
>   - 진행 기록에 남긴 "CPU 40/50/50/60" 문구도 정정합니다.
>   - ADR, README, 개발일지는 CPU 비교값을 인용하지 않아 따로 고칠 곳이 없습니다.
>
> ### [개선] 완료를 막지 않는 제안
> - **I-a. 대사 오탐 테스트가 "차변/대변 불일치 없음"까지는 확인하지 않음**
>   - 계획 문구(task.md 169행)는 "계좌 불일치 0·차변/대변 불일치 없음"입니다. 테스트(LedgerReconciliationJobIntegrationTest 247~307행)는 해당 계좌의 불일치 없음만 단언합니다.
>   - 테스트 데이터에 송금 쪽 차변 원장이 없습니다. 그래서 REPEATABLE READ에서도 전역 검사는 차변 0, 대변 0, 미반영 100으로 경고를 남깁니다. 대조군 로그 8행에서도 전역 불일치가 찍혔습니다.
>   - 핵심 성질은 대조군으로 증명됐습니다. 같은 스냅샷이 유지되고, READ COMMITTED로 바꾸면 실패합니다(recon-rc-control.log).
>   - 권장: `from`에 차변 100 원장을 넣고 전역 불일치 없음도 단언합니다. 또는 이 차이를 progress에 기록합니다.
> - **I-b. task.md 117행 커밋 계획과 실제 범위가 어긋남**
>   - 117행에 "application.yml 주석 보완"이 남아 있습니다. 하지만 V-1 처리로 범위에서 뺐습니다(progress 44행). 문구만 맞추면 됩니다.
> - **I-c. ADR 0012 9~11행의 출처 표기**
>   - 배경의 "8ms에서 1,397ms"는 ec2-baseline(S2) 값입니다. 그런데 문장에는 ec2-hot-account 경로가 붙어 있습니다.
>   - s2f 기준선 값은 "6~11ms → 193~1,115ms"(hot-account summary 56행)입니다. 출처를 맞추기를 권합니다.
> - **I-d. 단계 표에 짧은 꼬리 구간이 섞임**
>   - "실제로 돈 회차"에 기준선 r2의 110 단계가 들어 있습니다. 이 구간은 7초, 742건짜리 꼬리입니다.
>   - 규칙(요청 수 > 0)상 일관되지만, 50초를 다 채운 단계와 섞였다는 점을 각주로 남기면 좋습니다. 110 단계 비교값 302ms의 근거가 됩니다.
>
> ## 5. 이전 지적별 해결 여부
>
> | 지적 | 상태 | 확인 내용 |
> | --- | --- | --- |
> | B1 | 해결 | 사용자 결정 Q-10을 task.md에 반영했고, 계획 5회차에서 통과 권고를 받았습니다. 판정값 r1 281, r2 340, r3 220, r4 333, r5 404, r6 266ms는 credit-lag.md·json 단계값과 일치합니다. r5의 150 이하 최대값은 140 단계 404.0ms입니다. 원래 기준, 조정 일자와 이유, 150 초과 회차값(160 2,046 / 170 434 / 180 1,271 / 190 677)이 summary 67~74행에 적혀 있고, ADR 43~45행에도 160·180 값과 조정 사실이 있습니다. r5 160 단계의 CPU 74%, 대기 148도 analysis.json과 일치합니다 |
> | M1-a | 해결 | 전파 6/6 대 6/6입니다. 기준선 r2~r7의 `propagation_at_collapse`가 모두 true입니다 |
> | M1-b | 해결(회귀 N1) | r6 포함 재계산값(25/35/33/66, 12/17/15/29, 0/0/0/15)과 기준선 p95·조회·대기 값이 원자료와 일치합니다. 기준선 CPU 열만 불일치합니다(N1) |
> | M1-c | 해결 | "무너지기 전" 조건을 명시했고, 무너진 단계 지연(824ms, 3,206ms, 12,357ms, 53,618ms)과 "수십 초"를 기재했습니다. ADR 45행 "54초"도 맞습니다 |
> | M1-d | 해결 | 회복 r1·r2·r3(3/6)입니다. 기준선은 0/6이며, 다음 단계 p95가 2,056 / 925 / 295 / 1,871 / 707 / 598ms로 모두 200ms를 넘습니다 |
> | M1-e | 해결 | 발생기 CPU 20.5%(r5) |
> | M1-f | 해결 | steal "단계 내 최대값"과 평균 0.04~0.08을 구분했고 반례도 적었습니다. 최대값 자체는 gz 파일이라 확인하지 못했습니다 |
> | M1(ADR·README 인용) | 해결 | ADR 39~41행, README 77행이 33ms·15ms와 전파 6/6으로 표에 맞습니다 |
> | M2 | 해결 | 사용자 결정 Q-11. 패널 20~22를 추가했고 job="api"가 기존 18개 식과 같습니다. export가 패널 식을 그대로 저장하므로 다음 측정부터 원자료에 남습니다. credit-metrics.log에서 미반영 10, 나이가 10.5→25.4→40.6→55.6으로 증가, 해제 후 0, Timer count 0→10, max 60.63초(V-2 기준 60.9초 근처), up 결측 0을 확인했습니다. 게이지는 캐시를 읽습니다(PendingCreditWorker 30~48행). 시계열이 없다는 한계도 summary 75~76행에 적혀 있습니다 |
> | M3 | 해결(I-a는 개선) | 대사 중 반영 오탐 테스트를 추가했고, READ COMMITTED 대조군이 실패합니다. 워커 로그 억제 테스트 2개는 억제를 없애거나 성공 시 remove를 없애면 실패하도록 구성돼 있습니다. 1분 재로그 사례는 범위에서 뺐다고 task.md 172행에 명시했습니다 |
> | M4 | 해결 | 01:13Z에 b5750ff 기준으로 두 검사를 다시 돌린 로그가 있습니다. b_le=1 경계 이전 원장 사례와 b_le=0 대조(=2)가 있고, '>=' 변이 시 실패하는 것도 기록돼 있습니다. SQL 로직과 기대값을 읽어서 대조했고 일치합니다 |
> | M5 | 해결 | docs/devlog/2026-10-01_입금비동기반영.md 작성 |
> | I6 | 해결 | docs/03 479행에 "평소 1초, 과부하 때 더, OTP 대기 최대 약 4분" |
> | I7 | 범위 제외(타당) | V-1로 운영 코드 변경 0을 유지합니다. 문구 정합은 I-b |
> | I8 | 해결 | test-results 요약을 첨부했고, `:test`가 실제로 실행됐습니다 |
> | I1·I2·I3·게이지 정지·히스토그램 상한 | ADR 한계로 기록 | ADR 54~57행 |
>
> ## 6. REQ별 판정
>
> | REQ | 판정 | 근거 |
> | --- | --- | --- |
> | REQ-01 | 충족 | 1회차 근거(TransferServiceIntegrationTest, ReceiverLockScenarioTest)가 유지되고 운영 코드 변경이 없습니다. 이번 verify에서 12개, 5개 통과 |
> | REQ-02 | 충족 | OtpVerificationServiceIntegrationTest 6개 통과 |
> | REQ-03 | 충족 | PendingCreditApplyScenarioTest 6개, PendingCreditWorkerTest 2개(연속 실패 로그 1회, 다른 계좌 매번 반영, 성공 후 재실패 시 재로그) |
> | REQ-04 | 충족 | TransactionHistoryServiceIntegrationTest 4개 통과 |
> | REQ-05 | 충족 | AccountServiceIntegrationTest 16개, ReceiverLock, StatusChangeRace, 프론트 jest(verify-003) |
> | REQ-06 | 충족 | 대사 중 반영 오탐 테스트와 READ COMMITTED 대조군 실패, 1분 초과 경고 테스트, EC2 대사 로그. 전역식 단언 보강은 개선(I-a) |
> | REQ-07 | 충족 | Q-10 범위에서 6회 모두 ≤ 404ms이고 원자료와 일치합니다. 지표 노출·수집, 패널, 스크레이프가 DB를 기다리지 않는 점은 로컬 로그와 코드로 확인했습니다 |
> | REQ-08 | 충족 | 동시성 5종 통과(XML 요약) |
> | REQ-09 | 부분 | 회차별 기록, 전파, 반영 지연, 정합성 비교가 원자료와 일치합니다. 기준선 CPU 비교값만 불일치합니다(N1) |
> | REQ-10 | 부분 | ADR, README, 요약, 개발일지, Q-10 투명성 문구가 모두 있습니다. 요약 표의 CPU 값 정정이 필요합니다(N1) |
> | REQ-11 | 충족 | PendingCreditWorkerConfigTest 3개 통과, EC2 운영 이미지에서 실제 반영됨(1회차와 같음) |
>
> ## 7. 적용 제외 항목
> - 제외 항목의 판단은 1회차, 계획 5회차와 같고 여전히 타당합니다: BE-06, BE-08, PERF-05, E2E-01~05, OPS-01~04·08, AI, SEC-05 외 보안 항목.
> - PERF-04 대체를 "단계별 지연 증가로 적체 관측, 시계열 부재는 한계로 기록"으로 낮춘 것은 Q-11 사용자 결정과 일치하고, 요약에 명시돼 있습니다.
> - SEC-05: 새 로그(credit-metrics.log 등)를 읽어 보니 토큰이나 비밀번호 같은 비밀값은 없습니다. 결과 폴더는 이번 회차에 summary.md만 바뀌었습니다.
>
> ## 8. 남은 조치
> - **필수(N1):** summary.md 49~52행의 기준선 CPU를 43 / 48 / 51 / 59로 정정합니다. 문서만 바뀌므로 다시 테스트할 필요는 없습니다.
> - **선택:** I-a~I-d.
> - **관련 파일**
>   - /Users/imjaehyeon/Documents/[01] - Project/j-bank/perf/results/ec2-async-credit/summary.md
>   - /Users/imjaehyeon/Documents/[01] - Project/j-bank/perf/results/ec2-hot-account/s2f/r2~r7/loadgen/analysis.json
>   - /Users/imjaehyeon/Documents/[01] - Project/j-bank/apps/jbank-api/src/test/java/com/jbank/integration/LedgerReconciliationJobIntegrationTest.java
>   - /Users/imjaehyeon/Documents/[01] - Project/j-bank/.claude/tasks/async-credit/task.md
>   - /Users/imjaehyeon/Documents/[01] - Project/j-bank/docs/adr/0012-async-credit.md
>
>
> (원문 마지막 줄) 최종 판정 문구: 수정 필요

### 1회차 지적 메인 처리 내역(2회차 대상)

- B1: Q-10으로 판정 범위 조정, summary·ADR에 원래 기준·조정·초과값 기록(b9051bf).
- M1: summary 재계산·전파·회복·발생기 CPU·steal 표기, ADR·README 인용값(b9051bf).
- M2: 패널 20~22(3d7636f), 로컬 수집 로그 logs/credit-metrics.log.
- M3: 37d5318(대조군 logs/recon-rc-control.log), b5750ff.
- M4: 6b41869, logs/perf-tool-tests.log 재실행.
- M5: 68d9bd5.

### 2회차 지적 메인 처리 내역

- N1: 메인 계산 실수(기준선 CPU만 소수 첫째 자리로 반올림한 뒤 백분율로 바꿈). summary 표를 43/48/51/59로 정정, progress 문구 정정.
- I-a: 송금 쪽 차변 원장을 넣으면 송금 계좌 잔액이 음수가 돼 계좌 제약(chk_accounts_hold_amount_range)에 걸리고, 입금 원장으로 채우면 한쪽 원장이 생겨 전역식이 다시 깨진다. 전역식은 차변·대변·미반영이 같은 금액만큼 반대로 움직여 이 끼어들기로 판별되지 않으므로(계획 5회차 I-1 확인) 계좌식 단언과 대조군으로 판단하고 progress에 기록.
- I-b: task.md를 고치면 계획 지문이 바뀌므로 문구는 그대로 두고 범위 제외를 progress·plan-review 5회차 처리 내역에 기록(V-1).
- I-c: ADR 배경의 출처를 ec2-baseline(S2)으로 맞추고 s2f 기준선 값을 함께 적음.
- I-d: summary 단계 표에 기준선 r2 110 단계가 7초 꼬리 구간이라는 각주 추가.

