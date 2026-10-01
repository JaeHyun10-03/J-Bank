# 진행 기록

- 2026-09-30: 사용자 요청("1순위 진행시켜")으로 작업 생성. 결정 Q-01~09(A 방식, 모든 이체, 반영 후 노출, 송금자 즉시 완료, 1초 이내, s2f 5회 비교, 해지 경합 락, 정지·휴면 그대로 반영, 처리 중 입금 있으면 해지 거절 ACC_012). 2순위 3건은 별도 세션 작업 칩으로 분리.
- 계획 리뷰 1회차 수정 필요(해지 경합 차단 등 8건) → 2회차 수정 필요(OTP 대기 중 해지 N1) → 3회차 통과 권고. 문구 정합 I-A~C는 plan-review.md 3회차 처리 내역의 해석 기준으로 적용.

다음 행동: start → 커밋 1(V18).
- 커밋 2: 반영 서비스·워커·지표, 테스트 9개(PendingCreditApplyScenarioTest 6, PendingCreditWorkerConfigTest 3) 통과, 백엔드 전체 test·spotlessCheck 통과. 이 시점엔 대기 생산자가 없어 동작 변화 없음.
- 커밋 3: lockForStatusChange(FOR UPDATE)로 changeStatus·close 첫 로드. AccountStatusChangeRaceScenarioTest 2개 — 수정 전 코드로 되돌리면 둘 다 실패(잔액 덮어쓰기 재현), 수정 후 통과. 전체 test·spotless 통과.
- 커밋 4: ACC_012_PENDING_CREDIT_EXISTS(409), AccountRepository.hasIncomingInProgress(네이티브, 이체 도메인 의존 없음), AccountServiceIntegrationTest 2개(미반영 입금·PENDING_OTP 입금 → ACC_012), 프론트 domain-error-map.test.ts, 전체 백엔드 test·spotless·ArchUnit 통과, 프론트 jest·tsc 통과. @Operation 설명은 바꾸지 않아 openapi 계약 영향 없음.
- 커밋 5: 거래내역 Specification에 미반영 입금 대기 NOT EXISTS 조건, TransactionHistoryServiceIntegrationTest 1개(반영 전 받는 쪽 0건·보낸 쪽 1건, 반영 후 1건). 전체 test·spotless 통과.
- 커밋 6: 대사 전역식 차변 = 대변 + 미반영, 1분 초과 미반영 경고, 스텝 REPEATABLE READ. LedgerReconciliationTaskletTest 2개(단위), 통합 테스트 1개(2분 전 대기 → 경고). 전체 test·spotless 통과.
- 커밋 7: TransferService 송금 lockSenderByAccountNumber(NO KEY UPDATE)·수신 lockReceiverByAccountNumber(KEY SHARE), OTP 완료는 id 기준 같은 모드, executeTransfer는 차변 원장 + 입금 대기. CreditApplier는 @Transactional(REQUIRED)로 변경(워커는 트랜잭션 밖에서 계좌마다 호출하므로 호출마다 새 트랜잭션, 테스트 트랜잭션 안에서는 같은 트랜잭션에서 반영). 같은 트랜잭션에서 만든 대기의 created_at null 방어.
  - 기존 테스트 6개 수정(반영 후 확인): TransferServiceIntegration(이체 직후 수신 불변·대기 1건 → 반영 후 잔액·원장), OtpVerification, TransactionHistory, FullFlow, ConcurrentBidirectional, ConcurrentLedgerReconciliation(1,000건 → 반영 후 대사 일치).
  - 새 ReceiverLockScenarioTest 5개: NO KEY UPDATE로 수신 계좌를 3초 잡아도 이체 < 2초, FOR UPDATE 1초 잡으면 이체 ≥ 0.8초 대기, 이체·해지 동시 20회(둘 중 하나만 성공, CLOSED면 미반영 0), PENDING_OTP 중 해지 ACC_012 → OTP 완료 → 반영 정상, 반영·해지 동시 20회(해지 실패, 잔액 = 원장 합).
  - 임시 프로브: KEY SHARE를 쥔 행에 JPA PESSIMISTIC_WRITE(findByIdForUpdate) 대기 32ms → Hibernate가 PostgreSQL에서 FOR NO KEY UPDATE를 생성(입금·출금·이자 경로도 이체 수신 KEY SHARE와 충돌 없음). 프로브 파일은 삭제.
  - 전체 백엔드 test·spotlessCheck 통과.
- 커밋 8: perf/ec2/sql/credit-integrity.sql, target.sh(has_pending·경계 대기 B_PENDING·integrity 추가·credit-dump), run-ec2.py(write_integrity 미반영 포함 식, credit_lag·credit-lag.md, regen에도 적용), k6_monitor p95_no_relogin_s. 검사: integrity-sql-test ok, test_async_credit 4개 ok(로그 .claude/tasks/async-credit/logs/perf-tool-tests.log), 기존 hot-account r2 재생성 판정 유지.
- 추가 커밋(계획 외, 측정 도구): PERF_BUILD_IMAGE=1 → target.sh setup build(대상에서 perf-<sha> 이미지 빌드). 이유: gh 토큰에 packages 권한 없음, backend-cd로 브랜치 이미지를 만들면 :latest를 덮어 운영 부팅 작업이 미머지 코드를 받을 위험.
- verify 통과(f535020 시점). E2E(로그 .claude/tasks/async-credit/logs/e2e.log): 전체 13개 중 11 통과·2 실패 → 원인 확인: ① product-subscribe "j-farm" 글자 2개 strict mode 위반(상품 화면·데이터, 이번 변경과 무관, 범위 밖) ② OTP 테스트가 /tmp/backend.log에서 OTP 번호를 읽는데 로그 경로 불일치(환경). 링크 후 transfer.spec 4개 전부 통과(이체 흐름 회귀 없음, E2E-06).
- EC2 측정 시작(21:53): up(SSM 등록 정상) → setup build(대상에서 perf 이미지 빌드 약 4분 30초) → prepare → s2f 드라이런(20260930-220807): missing=none, invalid=no, 정합성 전부 같음(미반영 0, 중복·유실 0), 반영 지연 p95 214~226ms(판정값 226ms). 본 측정 r1 22:19 시작.
- r1: 판정 무너짐 90(p95 0.879s, Hikari 대기 88, 병행 조회 0.68s)인데 100 단계는 p95 24ms·대기 0으로 회복 → 지속적 락 줄이 아닌 일시 정지 양상. 정합성 전부 같음, 반영 지연 판정값 281ms(90 단계 p95 824ms·최대 3.8s). 원인은 요약 때 지표(체크포인트·GC·I/O)로 확인.
- r2: 무너짐 110(p95 0.276s, 대기 30, CPU 0.60), 최대 지속 100, 120 단계 p95 0.171s·처리 120, 반영 지연 판정 340ms. CPU가 오르며 병목이 락에서 CPU·풀로 이동하는 양상.
- r1 90 단계 지표(query_range): 수신 락 대기 최대 0.13s, 발신 락 대기 최대 0.22s, PG idle in transaction 최대 8, Hikari 대기 88, CPU 최대 0.62, GC 비율 최대 0.0105, 디스크·메모리 특이 없음 → 원인 미확정(같은 발신 계좌 동시 요청 줄서기 추정).
- r3: 무너짐 80(p95 0.418s, 대기 37), 90 단계 0.035s로 회복, 최대 지속 70, 반영 지연 판정 220ms. 튄 단계와 CPU steal 상관: r1 90 0.119, r2 110 0.177·60 0.138(대기 19), r3 80 0.180(부하 낮은 r1 40·50은 steal 0.155·0.175에도 정상) → t3 steal이 부하와 겹칠 때 일시 정지로 추정(상관만). 정합성 검사 SQL(credit-integrity)이 1천만 건에서 72초 걸림(측정 후 처리라 영향 없음, 기록).
- r4: 110까지 p95 ≤ 78ms, 무너짐 120(p95 8.5s·오류 2.4%·대기 192·CPU 0.71), 130 CPU 1.0·오류 98% → CPU 포화 붕괴(기준선의 락 줄서기와 다름). 최대 지속 110, 반영 지연 판정 333ms.
- r5 정합성 단계 24분+ 지연 원인: credit-integrity.sql이 이체마다 ledger_entries(transaction_id 인덱스 없음) 전체 스캔. fix 커밋(경계 이후 원장만 집계 후 조인) push, 실행 중 쿼리 취소 → r5 후처리 재실행 예정. r1~r4 정합성 결과는 이전 SQL로 정상 완료(결과 동일 의미).
- r5: 후처리 재개(고친 SQL로 정합성 44초). 무너짐 200(p95 0.236s), 최대 지속 190, 210 단계 205 TPS, CPU 0.82. 정합성: 새 불일치 0, 중복·유실 0, "다름" 1건은 실패 응답 반영(기존 현상). 반영 지연 ≤150 단계 p95 ≤ 404ms, 160 단계 2,046ms(Hikari 148)·180~210 단계 1.1~1.3s → 판정값 2,046ms로 REQ-07 목표(1s) 초과(r5만).
- 회차 무너짐 90/110/80/120/200 — 한 단계 초과 변동 → 기준선 규칙대로 r6 추가.
- r6(기준선 규칙에 따른 추가 1회): 무너짐 120(p95 2.8s·대기 192·CPU 0.74), 최대 지속 110, 최대 처리 130 TPS, 반영 지연 판정 266ms. 정합성 이상 없음(다름 1건은 실패 응답 반영 1건).
- 6회 요약: 무너짐 중앙값 115(80~200), 최대 지속 105(70~190), 최대 처리 TPS 115.05(90~205.1) vs 기준선 95/85/103.75. 같은 부하 단계 중앙값: 90 rps p95 238→35ms, 100 rps 395→28ms, 110 rps 295→78ms, 무관한 조회 p95 181→17, 307→13, 181→32ms. 반영 지연 판정 6회 중 5회 ≤ 340ms, r5만 2,046ms(160 단계).
- perf destroy 12개, 직접 조회로 잔여 0, 운영 dev plan No changes.
- 결과 커밋: SEC-05 스캔 257개 파일 적중 0(gz 포함), k6-summary setup_data 비어 있음.
- 문서 커밋: ADR 0012, ec2-async-credit/summary.md, README 3번 결과 한 줄, perf/README(PERF_BUILD_IMAGE·정합성·반영 지연).
- 2026-10-01 결과 리뷰 1회차 수정 필요(B1 r5 반영 지연 목표 초과, M1 요약 수치, M2 지표 수집 증거, M3 테스트 2개, M4 도구 검사 재실행, M5 개발일지). review.md 기록, review fail.
- 사용자 결정: B1은 목표 범위를 명확히 하고 수용(Q-10, 초당 150건 이하·무너지기 전 단계 판정, 6회 모두 최대 404ms로 충족), M2는 패널 추가 + 로컬 수집 확인(Q-11). task.md 수정 → 계획 재검증(4회차, 사용자 범위 조정 결정에 따른 추가 검증) → start.

- 계획 4회차 수정 필요(M-A 판정 정의·투명성, M-B 지표 재현 조건, M-C 측정 뒤 코드 변경) → task.md 수정, I1·I3 범위 제외 → 사용자 허락으로 5회차 → 통과 권고 → start.
- 37d5318 test(batch): 대사 중 반영 오탐 테스트(원장 저장소 스파이로 첫 집계 직후 다른 스레드가 반영 커밋). 대조군: 스텝 격리를 READ COMMITTED로 바꾸면 실패(logs/recon-rc-control.log), 원복 후 7/7 통과.
- b5750ff test(transfer): 워커 로그 억제 단위 테스트 2개(연속 3회 실패 로그 1번·다른 계좌 매번 반영, 성공 뒤 재실패 재로그) 통과.
- 6b41869 test(perf): 정합성 SQL 검사에 경계 이전 원장 행(b_le=1) 사례와 b_le=0 대조. '>='로 바꾸면 실패 확인. 도구 검사 두 개 재실행 ok(logs/perf-tool-tests.log 끝).
- 3d7636f feat(perf): 측정 대시보드 패널 20~22(미반영 수·가장 오래된 나이·반영 지연 p95/max). 로컬 수집 확인(logs/credit-metrics.log): 미반영 10, 나이 10.5→55.6 증가, 해제 후 0, Timer count 0→10·max 60.6s, up 결측 0. 로컬 api /actuator/prometheus가 인증을 요구해 측정과 같은 metrics_proxy.py를 거쳐 수집.
- b9051bf docs: summary 단계 표 6회 재계산(양쪽 같은 규칙), 전파 6/6 항목, 회복 r1·r2·r3, 발생기 CPU 20.5%, steal 최대값 표기·반례, 반영 지연 원래 기준·조정·초과값·과부하 수십 초, 측정 이미지 perf-f535020. ADR 수치·한계(I1·I2·I3·게이지 정지·히스토그램 상한), README 33/15ms, API 설계 문구(OTP 대기 최대 약 4분).
- 68d9bd5 docs(devlog): 개발일지.
- application.yml 주석(I7)은 범위에서 뺌(plan-review 5회차 V-1). 2d26adb..HEAD에 apps/jbank-api/src/main 변경 0.

- verify-003 통과(백엔드 :test 실제 실행, 241개 실패 0, logs/test-results-summary.log). 결과 리뷰 2회차 수정 필요: N1 summary 기준선 CPU 열이 원자료와 다름(메인 계산 실수, 기준선만 소수 첫째 자리 반올림). 정정값 43/48/51/59(위 b9051bf 항목의 기준선 CPU 값은 틀렸음).
- 2회차 개선 처리: I-a 대사 오탐 테스트에 송금 차변을 넣지 않은 이유(잔액 음수 제약, 한쪽 원장 생김)와 전역식은 이 끼어들기로 판별되지 않아 계좌식·대조군으로 판단함을 기록. I-b task.md 117행의 application.yml 주석은 범위 제외(V-1), task.md는 계획 지문 때문에 그대로 둠. I-c ADR 배경 출처 정정. I-d summary 꼬리 구간 각주.

다음 행동: 문서 커밋 → verify → review-begin → 결과 리뷰 3회차(마지막).
- 3e615a7 docs: 기준선 CPU 정정·ADR 출처·꼬리 구간 각주. verify-004 통과. 결과 리뷰 3회차 통과 권고(E1 개선 1건, ADR 9행 표현은 남은 개선으로 보고). review.md 정리 → review pass → complete.

다음 행동: complete 후 harness 커밋, PR.
