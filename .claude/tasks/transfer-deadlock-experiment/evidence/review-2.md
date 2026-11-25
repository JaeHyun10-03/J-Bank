# 검토 결과

## 검토 범위

- 1회차(2026-11-04, 병합 전 1차 리뷰, 사용자 결정): 커밋 1~6, 완료 기준 1·2·4·6, REQ-01~06·08. 완료 기준 3(REQ-07)·5(REQ-09)는 병합 후 운영 단계라 최종 리뷰에서 다룬다.
- 대상 snapshot: 785e3c1a0eb64cabd52f97ca6b10bf1da4440be219af4f77da5d336e8ad7a054 (HEAD 87a0b97, verify-002·003 통과)

## 결과 리뷰 1회차 원문

(verifier 반환 원문, 인용 표시 `> `만 붙임)

> # 결과 리뷰 1/3회차: transfer-deadlock-experiment
>
> 결론부터 말하면 **수정 필요**입니다. 코드와 테스트(REQ-01~05)는 요구사항과 맞고, 거짓 통과로 이어질 허점도 찾지 못했습니다. 막히는 곳은 두 군데입니다. 하나는 운영 실행 스크립트의 결함 2건이고, 다른 하나는 보고서 서술이 원자료와 어긋나는 2건입니다. 4건 모두 수정 범위가 작습니다.
>
> ## 검증 대상과 범위
>
> - 모드: 구현 후 결과 리뷰. 판정 범위는 사용자가 정한 대로 커밋 1~6, 완료 기준 1·2·4·6, REQ-01~06·08입니다.
> - 검증 대상
>   - snapshot `785e3c1a…a7ad054`, HEAD 87a0b97
>   - verify-001(환경 문제로 실패), verify-002·003(통과). 세 번 모두 snapshot이 같습니다.
> - 읽은 자료
>   - 규칙·작업 문서: testing-policy.md, task.md, progress.md, plan-review.md(9회차, 머리 줄 형식), state.json
>   - 증거: evidence/checks.json, verify-001·002·003의 checks.json·changes.txt, verify-002·003 check-5.log, 테스트 리포트 XML(새 테스트 4종, ConcurrentBidirectional), 전체 리포트의 실패·오류·건너뜀 여부
>   - 코드: TransferLockStrategy, TransferService, AccountRepository, GlobalExceptionHandler(핸들러 목록)
>   - 테스트: 새 테스트 5개 파일과 AbstractConcurrencyTest
>   - 스크립트·설정: run-deadlock.sh, deadlock.js, deadlock-summary.py, docker-compose.yml·prod.yml, deploy.sh, .env.example, alerts.yml, .gitignore
>   - 문서·결과: runbook, README, 로컬 결과(r1과 vu100-r1·r3 원자료, 교착 ② r1, 18회 conditions.json·loki.json 건수, postgres.log 일부), logs/secret-check.md
> - 적용 기준 ID: BE-02·03·04·06·07, OPS-03·05·07, SEC-05, PERF-01·06·07(상세 기준 문서와 대조함)
> - 확인하지 못한 범위
>   - 명령·테스트·`--check`는 실행하지 않았습니다. 요약표는 원자료로 3개 행(교착 ① vu50-r1, ① vu100-r1, ② vu50-r1)만 직접 맞춰 봤고, 모두 맞았습니다.
>   - git diff는 보지 않았고, 현재 파일을 명세와 대조했습니다.
>   - 운영 환경의 실제 동작(SSM 권한, `.env` 소유자·끝 줄바꿈, 컨테이너 안 wget)과 k6 setup 실패 때 summary-export가 어떻게 되는지는 확인하지 못했습니다.
>
> ## REQ별 판정
>
> | REQ | 판정 | 구현 / 테스트 / 실행 증거 |
> | --- | --- | --- |
> | REQ-01 | 충족 | 구현: TransferService.java:83-93, 160-171. CURRENT일 때 기존 정렬·락 모드 그대로.<br>테스트: CurrentLockStrategyScenarioTest(빈 값 → current). 즉시·OTP 모두 성공 100, 교착 0(XML :1448, :2367).<br>기존 전체 테스트: verify-002에서 실패 0. |
> | REQ-02 | 충족 | 테스트: Unsorted 리포트 XML :1204, :1692에서 1회차 교착 91·93, 기타 0.<br>부분 저장 없음: ScenarioTest:72-79(거래·원장·입금 대기 수, 잔액 합 확인).<br>OTP 실패 건: :113-117에서 PENDING_OTP와 지급정지 유지 확인.<br>응답: GlobalExceptionHandlerTest:73-86, 로컬 ① 5xx가 전부 COMMON_006. |
> | REQ-03 | 충족 | 락 모드: ScenarioTest:134-182. 두 경로 × 송금·수신 4경우를 NOWAIT로 확인(for-update 두 전략은 55P03, current는 null).<br>Sorted: 교착 0, 성공 100. |
> | REQ-04 | 충족 | 구현: TransferLockStrategy:40-70.<br>테스트: ConfigTest 5건. XML의 system-out에서 INFO·WARN 수준, 기동 실패 로그까지 확인. |
> | REQ-05 | 충족 | 테스트 불변식: ScenarioTest:73-79, 113-120.<br>부하 18회: summary.py:66-73 계산과 원자료 3개 행이 일치. |
> | REQ-06 | **부분** | 18회 결과, 메일(mail.json), Loki(loki.json), postgres.log가 있습니다. 다만 "① 5xx와 교착 증가량 대조"가 빠졌습니다(중요 4). |
> | REQ-08 | **부분** | 요약표·조건·한계·재현 실험이라는 점은 모두 있습니다. 다만 발견 경위 서술이 원자료와 어긋납니다(중요 3). |
>
> ## [차단]
>
> 없습니다.
>
> ## [중요]
>
> ### 1. run_k6가 k6 실패를 성공으로 바꿔 돌려줌
>
> - 위치: perf/run-deadlock.sh:209-217 / 기준: PERF-07, REQ-07의 회차 분류
> - 근거
>   - 함수의 반환값이 마지막 명령인 `mv`의 결과입니다.
>   - 이 함수는 `run_k6 … || fail_round` 자리(:342, :350)에서 불려서 함수 안에서는 `set -e`가 꺼집니다. 그래서 k6가 0이 아닌 값으로 끝나도 무시됩니다.
>   - k6 요약 파일이 없으면 `jq … > tmp`가 빈 파일을 만들고 `mv`는 성공합니다.
> - 영향
>   - setup 로그인 실패 같은 k6 중단이 `measured=yes`, "완료"로 기록됩니다.
>   - 그 뒤 deadlock-summary.py:27의 `json.loads`가 빈 파일에서 죽습니다.
>   - 운영은 재실행이 없는 1회 실행이라, 분류가 틀린 채 그대로 남습니다.
> - 수정: k6 종료 코드를 받아 돌려주고, 요약 파일이 비어 있지 않은지 확인합니다.
>
> ### 2. 비밀번호가 jq 인자와 here-string으로 넘어감
>
> - 위치: run-deadlock.sh:115-117 / 기준: Q-13("파일·인자·로그에 남기지 않는다"), SEC-05(인자 금지)
> - 근거
>   - `jq -cn --arg p "$PASSWORD"`는 비밀번호를 jq 실행 인자로 넘깁니다. 그동안 `ps`에 보입니다.
>   - `<<< "$body"`는 macOS 기본 bash 3.2에서 임시 파일로 구현됩니다(어떤 bash가 쓰이는지는 확인하지 못했습니다).
> - 영향: 실제 노출 시간은 아주 짧지만, 사용자와 합의한 규칙을 문자 그대로 어깁니다. 운영 비밀번호가 이 경로를 지납니다.
> - 수정
>   - `PASSWORD="$PASSWORD" jq -cn --arg l "$LOGIN_ID" '{loginId:$l,password:env.PASSWORD}' | curl … --data-binary @-`
>   - 또는 printf 내장 명령에서 파이프로 넘깁니다.
>
> ### 3. 보고서의 "교착을 어떻게 발견하나" 서술이 원자료와 맞지 않음
>
> - 위치: perf/results/deadlock/README.md:63-65 / 기준: REQ-08
> - 근거
>   - 보고서는 07:03:50 알림을 "측정 시작 약 30초 뒤, group_wait 30초"로 설명합니다.
>   - 실제 첫 교착은 워밍업 중인 07:02:47에 났습니다(r1 postgres.log:12).
>   - r1 before.json(07:03:19)의 교착 카운터가 이미 42입니다.
>   - 동시 100 회차들은 5xx 비율·락 대기 알림 발생 시각이 before.json 시각과 같습니다(vu100-r1 07:14:50, vu100-r3 08:21:50). 즉 워밍업이 알림을 일으킨 것입니다.
> - 영향: 첫 교착에서 메일까지 실제로는 약 63초인데 30초로 줄여 말하게 됩니다. 포트폴리오의 핵심 서술("어떤 로그로 발견했나")에서 사실과 다릅니다.
> - 수정
>   - 시간 순서를 "첫 교착 07:02:47(워밍업) → 메일 07:03:50"으로 고칩니다.
>   - ① 조건은 워밍업(VU 5, 20초)에서도 교착 42건이 난다는 점을 적습니다.
>   - 알림 시각에 워밍업이 섞인다는 점을 적습니다.
>
> ### 4. REQ-06의 "① 5xx 수와 교착 증가량 대조"가 보고서에 없음
>
> - 위치: README.md:52-58 / 기준: REQ-06, PERF-06
> - 근거
>   - vu50-r1은 교착 202건인데, 실패 응답은 5xx 199 + 타임아웃 1 = 200건입니다. 실패 응답보다 교착이 2건 많습니다.
>   - 동시 100은 5xx 105~134건에 교착 194~222건입니다.
>   - 표에 숫자는 나란히 있지만, 차이를 설명하는 문장이 없습니다.
> - 영향: "500 = 교착"이라는 해석을 숫자로 받쳐 주지 못합니다.
> - 수정: 차이의 원인을 적습니다. 후보는 클라이언트 타임아웃 뒤 서버에서 교착한 경우, k6 gracefulStop으로 중단돼 집계에서 빠진 요청, 기준값 직전 워밍업의 남은 요청입니다. 설명할 수 없는 잔차가 있으면 그렇다고 기록합니다.
>
> ## [권고]
>
> 완료를 막지 않는 항목입니다. 다만 5~10은 운영 실행 전에 고치기를 권합니다.
>
> - **5. 무효 회차 이름을 막지 않음** (run-deadlock.sh:404-431)
>   - `--from` 값이 PROD_ORDER에 없으면(오타) 모든 회차를 건너뛰고 곧바로 설정 복귀로 갑니다.
>   - 이미 결과가 있는 회차 이름을 넣으면 그 결과를 덮어씁니다. Q-05의 "재실행 없음"에 반하고, 기록이 없어질 수 있습니다.
>   - 값 목록 검사와 `result.json` 존재 검사를 넣으세요.
> - **6. api가 healthy가 되지 않아도 다음 단계로 감** (:194-197, :200-205)
>   - 대기 반복이 끝나도 실패로 처리하지 않습니다. 기동 로그 문구만 확인하므로, 내려간 api를 상대로 측정할 수 있습니다.
> - **7. `.env` 끝 줄바꿈을 보장하지 않음** (:199)
>   - 파일 끝에 줄바꿈이 없으면 실험 전략 줄이 앞 줄에 붙습니다. 그러면 복귀할 때 `sed '/^JBANK…/d'`가 그 줄을 지우지 못합니다.
>   - 덧붙이기 전에 `tail -c1` 검사를 권합니다.
> - **8. 측정은 끝났지만 "완료"가 아닌 회차를 요약표가 처리하지 못함** (summary.py:56-57)
>   - 운영의 측정 후 시간 초과·무응답, 또는 로그 백업 실패(:361) 회차는 before·after 값이 있어도 표에 "-"로 나옵니다.
>   - 계획은 이런 회차를 따로 기록한 종료값으로 불변식을 판정하라고 합니다. 그 파일을 읽는 처리가 필요합니다.
> - **9. 운영 해제 대기가 발생을 본 적이 없어도 해제로 판정할 수 있음** (:268-281)
>   - 알림이 0개면 6분 뒤 "해제 확인"이 됩니다. 발생을 한 번은 봤는지 함께 기록하세요.
> - **10. 운영 중단 처리**
>   - SSM 대기 상한에 걸려도 원격 명령을 취소하지 않습니다. flock을 기다리던 전략 적용이 스크립트가 끝난 뒤에 실행될 수 있습니다. `cancel-command`를 권합니다.
>   - 회차가 중간에 실패하면 메모리 표본 수집 프로세스(:237-249)가 계속 남습니다.
>   - Ctrl-C나 맥 절전 같은 사용자 중단은 처리하지 않습니다. INT·TERM trap과 runbook의 `caffeinate -i`를 권합니다.
> - **11. 보고서가 인용한 postgres.log가 저장소에 없음** (README.md:70, .gitignore:8 `*.log`)
>   - postgres.log와 k6.log가 무시 대상이라 커밋되지 않았습니다. 완료 기준 2는 로컬 파일로 충족하지만 근거가 오래 남지 않습니다.
>   - 발췌 파일을 커밋하거나, 로컬에만 있다고 적으세요.
> - **12. 로그 인용과 Loki 건수 문구** (README.md:67, :72-80)
>   - Loki의 "DETAIL:  Process" 건수에는 락 대기 로그("Process holding the lock")도 섞여 있습니다.
>   - 인용한 로그 블록은 postgres.log의 1, 3, 12~16줄을 이어 붙인 것입니다. "발췌"라고 적으세요.
>   - r1의 loki.json은 나중에 다시 조회해 만든 것이라는 점도 결과에 적어 두기를 권합니다.
> - **13. 테스트 동시 시작 대기가 매번 10초를 낭비함** (ScenarioTest:211, :225)
>   - 대기 래치 수(100)가 스레드 수(20)보다 커서 `ready.await`가 매번 10초 시간 초과로 끝납니다. 약 50초가 낭비되고(새 테스트 합계 165초, 예산 180초), 동시 시작 의도도 일부만 지켜집니다.
>   - 거짓 통과로 이어지지는 않습니다. 래치 수를 `min(THREADS, tasks)`로 고치면 됩니다.
> - **14. k6 집계 세부** (deadlock.js:22-31)
>   - gracefulStop 기본값 30초가 요청 타임아웃 30초와 같아, 끝 무렵 요청이 집계에서 빠질 수 있습니다(PERF-07 누락 요청). 35초 이상을 권합니다.
>   - "처리량 req/s"는 로그인 요청과 마무리 구간을 포함한 전체 시간 기준입니다. ①은 약 80~90초입니다. 주석을 다세요.
> - **15. 증거 기록**
>   - 최종 checks.json(verify-003)의 백엔드 검사는 Gradle 캐시입니다(check-5.log:7 `:test UP-TO-DATE`). 실제 실행 근거는 같은 snapshot의 verify-002(322초)와 리포트 XML입니다. progress.md에 verify-003을 실행한 이유와 이 점이 없습니다.
>   - conditions.json의 `git` 값은 HEAD만 남깁니다. r1은 945c35f에서 커밋하지 않은 스크립트로 돌았습니다.
> - **16. 기타**
>   - 로컬 사용 예시가 `PASSWORD=` 접두로 실행해 셸 기록에 남습니다.
>   - Cookie와 CSRF 값이 curl 인자로 넘어갑니다(:122, :134-135).
>   - OTP 경로의 for-update 쿼리 `lockForStatusChange`에 `@Timed`가 없습니다. 부하 실험에는 영향이 없습니다.
>   - 예외 처리기 테스트는 처리기 메서드를 직접 부릅니다. 실제 라우팅은 로컬 결과(전부 COMMON_006)로 보강됩니다.
>
> ## 기준 ID별 증거 충분성
>
> | 기준 | 판정 | 근거 |
> | --- | --- | --- |
> | BE-02 | 충분 | 처리기 테스트, 로컬 code 분포 |
> | BE-03 | 충분 | 4경우 NOWAIT, 부분 저장 0 |
> | BE-04 | 충분 | 회차별 교착 수 출력, 기타 예외 0 |
> | BE-06 | 충분 | PENDING_OTP와 지급정지 유지 |
> | BE-07 | 충분 | 테스트와 18회 모두 미반영 0 확인 뒤 불변식 |
> | OPS-03 | 충분 | 기동 실패, 빈 값·미설정 → current, 환경변수, 로그 |
> | OPS-05 | 운영 단계 | 로직만 검토. 권고 5·6·7·10 |
> | OPS-07 | 충분, 서술 수정 | 로컬 메일 6회 해제 확인, Loki 조회. 중요 3 |
> | SEC-05 | 1차 충분 | secret-check.md 0건·stderr 0, 200개 파일. 다만 중요 2(스크립트의 비밀번호 처리) |
> | PERF-01 | 충분 | 18회 conditions.json, 회차 순서를 시각으로 확인 |
> | PERF-06 | 충분, 해석 보완 | 중요 4 |
> | PERF-07 | 대체로 충분 | 권고 14, 다른 부하 없음(verify는 08:39 시작) |
>
> ## 계획과 다르게 구현한 부분
>
> - OTP 테스트 임계금액: `@TestPropertySource` 5000으로 정했습니다(1천 원은 즉시 이체, 1만 원은 인증 대기). 계획의 "임계금액을 낮춘 테스트"와 맞습니다.
> - Loki 조회: 여러 줄 조회식 대신 `count_over_time`과 표본 6줄로 바꿨습니다. REQ-06의 실체는 충족합니다. 변경과 r1 재생성은 progress.md:96에 기록돼 있지만 보고서에는 없습니다(권고 12).
> - plan-review.md 머리 줄: 1~8회차의 메타데이터 줄만 바꿨고, verifier 원문 인용은 그대로입니다. progress.md:87에 기록돼 있어 문제없습니다.
> - 커밋 1에 테스트 4곳의 `@Import`를 함께 넣었습니다. 빌드를 깨지 않으려는 이유가 기록돼 있어 타당합니다.
> - 9회차 권고 1~4: runbook 4-2(:93), 4-3(:105-107), 7(:193-196)에 반영된 것을 확인했습니다.
>
> ## 적용 제외 항목
>
> 프론트, AI, 전체 흐름 영역과 BE-01·05·08, OPS-01·02·04·06·08, SEC-01~04·06~08, PERF-02~05의 제외 이유는 변경 내용과 맞습니다.
>
> ## 이전 지적
>
> 결과 리뷰 첫 회차라 이전 지적이 없습니다.
>
> ## 병합 후 남은 일
>
> 이번 판정에는 반영하지 않았습니다.
>
> - 완료 기준 3(REQ-07, OPS-05·07): 운영 6회 실행, Gmail 수신 기록, `prod/restore.md`, 중지 스케줄 diff 0. 실행 전에 중요 1·2와 권고 5~10을 반영하기를 권합니다.
> - 완료 기준 5(REQ-09): 실험 당일 FDS 수동 실행 관찰.
> - 실험일 전 확인: SSM `echo ok`, `get-schedule` 권한.
> - SEC-05 재실행
>   - 커밋 1~6 push 직전에 실행하고 secret-check.md에 덧붙입니다.
>   - 운영 브랜치의 작업 기록 커밋 뒤 push 직전에 실행하고 progress.md와 최종 보고에 남깁니다.
> - README의 "운영 결과" 절, 개발일지(커밋 9), 작업 기록(커밋 10).
> - 최종 결과 리뷰.
>
> 관련 파일
> - /Users/imjaehyeon/Documents/[01] - Project/j-bank/perf/run-deadlock.sh
> - /Users/imjaehyeon/Documents/[01] - Project/j-bank/perf/deadlock-summary.py
> - /Users/imjaehyeon/Documents/[01] - Project/j-bank/perf/k6/deadlock.js
> - /Users/imjaehyeon/Documents/[01] - Project/j-bank/perf/results/deadlock/README.md
> - /Users/imjaehyeon/Documents/[01] - Project/j-bank/perf/results/deadlock/local/unsorted-for-update-vu50-r1/postgres.log (커밋되지 않음)
> - /Users/imjaehyeon/Documents/[01] - Project/j-bank/apps/jbank-api/src/test/java/com/jbank/concurrency/TransferLockStrategyScenarioTest.java
> - /Users/imjaehyeon/Documents/[01] - Project/j-bank/.claude/tasks/transfer-deadlock-experiment/evidence/verify-002/check-5.log
>
> 최종 판정: 수정 필요

### 1회차 처리 계획

- 판정: 수정 필요(차단 0, 중요 4, 권고 12). `review fail` 등록 후 수정 → verify → 2회차 리뷰.
- 중요 1(run_k6 종료 코드)·2(비밀번호 인자 전달): 스크립트 수정.
- 중요 3(발견 경위 시각)·4(5xx·교착 대조): 보고서 수정. 원자료로 다시 계산한다.
- 권고 5~10(운영 스크립트 견고성), 11(.log가 .gitignore로 빠짐 → 파일 이름 변경), 12(문구), 13(테스트 래치), 15(기록) 반영.
- 권고 14 gracefulStop: 로컬 18회가 기본값(30초)으로 돌았으므로 운영만 바꾸면 조건이 달라진다(PERF-01). 바꾸지 않고 보고서 한계에 적는다.
- 권고 16: 로컬 사용 예시를 파일에서 읽는 방식으로 고치고 Cookie·CSRF는 헤더 파일로 넘긴다. `lockForStatusChange`의 @Timed는 OTP 경로(부하 미사용)라 두고 한계로 적는다.

## 결과 리뷰 2회차 원문

(verifier 반환 원문, 인용 표시 `> `만 붙임. 대상 snapshot 9ca42157…e87a6f, HEAD 6cf3314, verify-004 통과)

> # 결과 리뷰 2/3회차: transfer-deadlock-experiment
>
> 결론은 **수정 필요**입니다. 1회차 중요 4건은 모두 해소됐고, 코드·테스트·로컬 결과 쪽(REQ-01~06·08)도 충족합니다. 다만 1회차에 놓친 운영 스크립트의 실제 결함 1건을 새로 찾았습니다. 원격 셸 스크립트를 표준 입력으로 넘기는 방식과 `docker compose exec -T`가 함께 쓰이는 곳이 문제입니다. 운영 실행 첫 회차부터 "측정 미실행"으로 끝날 가능성이 높고, Q-05 규칙상 그 회차는 다시 돌릴 수 없습니다. 고칠 범위는 작습니다. 이번 판정은 완료 기준 3을 실행하지 않았기 때문이 아니라 이 결함 때문입니다.
>
> ## 검증 대상과 범위
>
> - 모드: 구현 후 결과 리뷰. 범위는 커밋 7a16057..6cf3314, 완료 기준 1·2·4·6, REQ-01~06·08입니다.
> - snapshot: `9ca42157…e87a6f`(HEAD 6cf3314, verify-004 통과)
> - 읽은 자료
>   - review.md(1회차 원문·처리 계획), progress.md, task.md(REQ·Q-05·Q-13·무응답 절차 :166), testing-policy.md(실행 시점·리뷰 반복 제한)
>   - evidence/checks.json(verify-004), changes.txt, verify-004 check-5.log, `.claude/checks.json`
>   - 테스트 리포트 XML(새 시나리오 3종의 시각·교착 수, 전체 실패·오류 0)
>   - run-deadlock.sh 전체, deadlock-summary.py, deadlock.js, TransferLockStrategyScenarioTest.java
>   - runbook, README, .gitignore, logs/secret-check.md
>   - 로컬 원자료 일부: r1 postgres-log.txt·mail.json·conditions·before, vu100-r1 mail·before·after·요약, ① 워밍업 요약 6개, ① 첫 교착 시각 3개 회차, progress.txt
> - 기준 ID: BE-02·03·04·06·07, OPS-03·05·07, SEC-05, PERF-01·06·07
> - 확인하지 못한 범위
>   - 명령·테스트·`--check`는 실행하지 않았습니다. 요약표는 vu100-r1 행만 원자료로 다시 계산했고 맞았습니다(교착 222, 반영 8, 요청 277).
>   - git diff는 보지 않았습니다. 그래서 아래 중요 A가 이번 수정에서 생긴 것인지, 원래 있던 것인지는 확인하지 못했습니다.
>   - 운영 동작(SSM, 원격 docker CLI 버전, 컨테이너 안 wget)은 실행으로 확인하지 못했습니다.
>
> ## 1회차 지적별 해소 여부
>
> | 지적 | 판정 | 근거 |
> | --- | --- | --- |
> | 중요 1 run_k6 | 해결 | run-deadlock.sh:244-260. 종료 코드를 `rc`로 받고, 요약이 비었거나 0이 아니면 실패로 처리합니다. 대상이 응답하지 않으면 무응답, 응답하면 명령 실패로 나눕니다. deadlock.js에는 thresholds가 없어 정상 측정이 99로 끝나는 거짓 실패도 없습니다. |
> | 중요 2 비밀번호 인자 | 해결 | :136-138. 비밀번호를 `env.PASSWORD`로 읽어 파이프로 넘깁니다. 쿠키·CSRF는 `$TMP/auth.headers`(mktemp -d 폴더)에서 `-H @file`로 넘깁니다(:142-145, :158). |
> | 중요 3 발견 경위 | 해결 | README:68-76. 첫 교착 07:02:47은 postgres-log.txt:12, 기준값 07:03:19는 before.json(교착 42), 메일 07:03:50·07:06:50·07:13:50은 mail.json과 맞습니다. "16~26초"는 r1 16초, vu50-r2 17초, vu100-r3 26초로 확인했습니다. "워밍업 500 28~37건"은 워밍업 요약 6개와 맞습니다. |
> | 중요 4 5xx와 교착 대조 | 해결 | README:59-62. 동시 100의 차이 88·89·96은 원자료와 맞습니다. 잔차는 "확정하지 못했다"고 적었습니다. |
> | 권고 5 `--from` | 해결 | :458-461에서 목록에 없는 값을 막습니다. 결과가 있는 회차는 :480-483에서 건너뛰고, :361에서 이중으로 막습니다. |
> | 권고 6 healthy | 해결 | :220, :236에서 healthy가 아니면 `exit 1`입니다. |
> | 권고 7 `.env` 끝 줄바꿈 | 해결 | :228. 원격 heredoc 인용도 따라가 확인했습니다. 바깥 셸이 `$line`만 펼치고 `\$(tail -c1 .env)`는 안쪽 `bash -s`가 평가합니다. `'EOS'`로 인용했고 구분자가 0열이라 정상입니다. current일 때 `[ -n '' ]`로 줄 추가를 건너뜁니다. |
> | 권고 8 비완료 회차 요약 | 대체로 해결 | deadlock-summary.py:56-58. 결과 상태와 상관없이 요약·before·after가 있으면 계산합니다. 다만 runbook이 종료값을 어느 파일에 둘지 정하지 않았습니다(권고 C). |
> | 권고 9 발생 미관측 | 해결 | :322, :326 |
> | 권고 10 중단 처리 | 대체로 해결 | `cancel-command`(:118), 표본 수집 선종료(:282, :435), INT/TERM trap(:59-73), runbook caffeinate(:88)가 들어갔습니다. 남은 점은 권고 B·E에 적었습니다. |
> | 권고 11 *.log | 해결 | 결과 폴더에 `*.log`가 0개입니다. postgres-log.txt·k6-log.txt·progress.txt가 커밋 대상에 있습니다(changes.txt). |
> | 권고 12 문구 | 해결 | README:75("DETAIL에 락 대기 로그 포함"), :80("발췌", 1·3·12~16줄), :105(r1 loki.json 재생성) |
> | 권고 13 테스트 래치 | 해결 | ScenarioTest:212 `min(THREADS, tasks.size())`. verify-004 XML에서 Current 0.758초, Sorted 0.831초(이전에는 약 10초씩)입니다. Unsorted는 84.6초, 교착 88·86, 기타 0입니다. |
> | 권고 14 gracefulStop·처리량 | 수용 | README:104에 한계, :57에 처리량 산식을 적었습니다. PERF-01 조건 일관성을 이유로 값을 유지한 판단은 타당합니다. |
> | 권고 15 증거 기록 | 일부 | conditions.json의 git 값 문제는 README:105에 적었습니다. progress.md에는 verify-003 실행 이유와 verify-004 결과가 아직 없습니다(:118이 "다음: verify"에서 끝남). verify-004에서 :test가 실제로 다시 돌았으므로(check-5.log:8, XML 10:14) 증거 자체는 충분합니다. 기록 보완만 남았습니다. |
> | 권고 16 기타 | 해결 | 로컬 예시를 `set -a; . 파일`로 바꿨습니다(runbook:64, 스크립트:4). Cookie·CSRF는 헤더 파일로 넘깁니다. `lockForStatusChange`의 @Timed 누락은 README:106에 적었습니다. |
>
> ## [차단]
>
> 없습니다.
>
> ## [중요]
>
> ### A. 운영 원격 스크립트에서 `docker compose exec -T`가 남은 스크립트를 삼킴(1회차에 놓친 결함)
>
> - 위치: perf/run-deadlock.sh
>   - 원격 실행 방식: :89-92 `echo $b64 | base64 -d | runuser -u ec2-user -- bash`
>   - 영향받는 곳: :238-239(apply_strategy 운영), :313-315(wait_release 운영), :500-501(prod-restore-check)
> - 기준: REQ-07, OPS-05, OPS-07, task.md:166(측정 전 중단은 "측정 미실행"이고 재실행하지 않음)
> - 근거
>   - 운영에서 바깥 bash는 스크립트를 표준 입력(파이프)에서 읽습니다. bash는 파이프 입력을 1바이트씩 읽기 때문에, 아직 읽지 않은 줄은 자식 명령이 가져갈 수 있습니다.
>   - `docker compose exec -T`는 표준 입력을 컨테이너로 넘깁니다. 이 스크립트도 psql_json(:127)에서 heredoc으로 SQL을 넘기는 데 이 동작에 기대고 있고, 로컬 18회에서 그렇게 동작했습니다.
>   - 따라서 :238의 `$($DC exec -T api printenv …)`가 다음 줄 :239(`docker logs … | grep '이체 락 전략'`)를 읽어 버립니다. 그 줄은 실행되지 않습니다.
> - 영향
>   - 운영 회차마다 logline에 전략 줄이 없어 :387 "기동 로그 전략 불일치"로 끝납니다. 결과는 "측정 미실행(명령 실패)"입니다.
>   - task.md:166 규칙상 그 회차는 다시 돌릴 수 없고, 스크립트도 :361·:480에서 재실행을 막습니다. `--from`으로 재개해도 회차마다 같은 일이 반복돼 6회가 모두 사라질 수 있습니다.
>   - wait_release에서는 :313 exec가 `echo`와 Prometheus 조회 줄을 삼킵니다. 그러면 `tail -1`이 Alertmanager 배열을 받고, `jq '.data.result'`가 실패해 prom 값이 비어 해제 판정이 나지 않습니다. ① 회차마다 15분을 다 기다린 뒤 "해제 미확인(상한 도달)"로 끝납니다.
>   - prod_restore와 prod-restore-check가 남기는 restore.md에 "이체 락 전략: current" 줄이 빠집니다. OPS-05 증거가 비게 됩니다.
>   - 로컬은 `bash -c "$1"`(:84)로 실행해 이 문제가 없습니다. 그래서 로컬 스모크로는 찾을 수 없었습니다.
>   - 동작은 경쟁 조건에 기대지만, 남은 스크립트가 이미 파이프 버퍼에 있어 거의 확실하게 삼켜집니다. 실행으로는 확인하지 않았습니다.
> - 수정(둘 중 하나)
>   - 표준 입력이 필요 없는 exec 호출(:204, :238, :313, :315, :500)에 모두 `< /dev/null`을 붙입니다.
>   - 또는 host()가 스크립트를 표준 입력 대신 파일이나 `bash -c`로 넘기게 바꿉니다.
> - 필요한 확인: runbook 4-1(실험일 전 확인)에 읽기 전용 SSM 점검을 넣습니다. 예를 들어 `exec -T api printenv` 다음 줄에 `echo 다음줄` 같은 표시를 두고, 그 표시가 출력되는지 봅니다. 고친 뒤 로컬 스모크와 `bash -n`도 다시 확인하기를 권합니다.
>
> ## [권고]
>
> 완료를 막지 않는 항목입니다.
>
> - **B. 측정 중 Ctrl-C를 "측정 미실행"으로 기록** (:64-67, :399-401)
>   - `measured=yes`가 run_k6가 끝난 뒤에야 설정됩니다. 그래서 측정 도중 중단하면 부하가 일부 걸렸는데도 "측정 미실행(사용자 중단)"으로 남습니다.
>   - after.json이 있으면 ②·③ 회차도 release가 "해제 미확인(사용자 중단)"으로 남습니다(:63).
>   - 측정 시작 표시를 run_k6 앞에 두고, ① 이외 회차는 release를 "-"로 남기기를 권합니다.
> - **C. runbook 4-3의 종료값 기록 위치** (runbook:106)
>   - summary.py는 `<회차>/after.json`만 읽습니다(:55). 그런데 runbook은 "SSM으로 기록"이라고만 적었습니다.
>   - snapshot과 같은 형식으로 그 회차의 after.json에 저장한다고 적으세요.
>   - runbook:39의 결과 이름 목록에 "사용자 중단"도 넣으세요.
> - **D. 0바이트 k6-summary.json이면 요약 스크립트가 멈춤** (summary.py:27)
>   - run_k6는 `-s`일 때만 정리하므로 0바이트 파일은 그대로 남습니다. 발생 가능성은 낮습니다. 비어 있으면 None으로 처리하기를 권합니다.
> - **E. 중단 처리의 남은 틈**
>   - SSM 대기 중 Ctrl-C가 오면 원격 명령을 취소하지 않습니다(:99-123과 trap). trap에 진행 중 명령 취소를 넣기를 권합니다.
>   - 재개(runbook:112)와 prod-restore-check(:150) 예시에는 `caffeinate -i`가 없습니다.
> - **F. README:60 문구**
>   - 동시 50에서 r1은 교착이 실패 응답보다 2건 많고, r2·r3은 실패 응답이 3건 많습니다. 방향이 다르다는 점을 한 줄 적으면 정확해집니다.
> - **G. 진행 기록**
>   - progress.md에 verify-003 실행 이유와 verify-004 결과(통과, :test 실행)를 남기세요.
>
> ## REQ별 판정
>
> | REQ | 판정 | 구현 / 테스트 / 실행 증거 |
> | --- | --- | --- |
> | REQ-01 | 충족 | Current XML: 즉시·OTP 성공 100, 교착 0(:1448, :2367). verify-004 백엔드 :test 실행, 전체 리포트 실패·오류 0. |
> | REQ-02 | 충족 | Unsorted XML: 즉시 교착 88(:1727), OTP 교착 86(:1216), 기타 0. 부분 저장과 PENDING_OTP·지급정지 단언은 ScenarioTest:72-79, :111-120. 로컬 ①의 5xx는 모두 COMMON_006. |
> | REQ-03 | 충족 | 락 모드 NOWAIT 4경우(ScenarioTest:134-182). Sorted XML: 교착 0, 성공 100(:1449, :2373). |
> | REQ-04 | 충족 | 1회차 확인 뒤 코드 변경 없음. ConfigTest가 verify-004에서 통과. |
> | REQ-05 | 충족 | 테스트 불변식, 18회 모두 "유지". vu100-r1 원자료 재계산이 맞음. |
> | REQ-06 | 충족 | 18회 결과, mail.json, loki.json, postgres-log.txt(커밋 대상), 5xx·교착 대조(README:59-62). |
> | REQ-08 | 충족 | 요약표·조건·발췌 로그(계좌번호는 `$1`)·메일 시각·재현 실험 명시·한계. `--check` 일치는 progress.md:117 기록이며 직접 실행하지는 않았습니다. |
>
> ## 기준 ID별 증거 충분성
>
> | 기준 | 판정 | 근거 |
> | --- | --- | --- |
> | BE-02 | 충분 | 처리기 테스트, 로컬 code 분포 |
> | BE-03 | 충분 | 4경우 NOWAIT, 부분 저장 0 |
> | BE-04 | 충분 | verify-004 회차별 교착 수 출력, 기타 0 |
> | BE-06 | 충분 | PENDING_OTP와 지급정지 유지 |
> | BE-07 | 충분 | 테스트와 18회 모두 미반영 0 확인 뒤 불변식 |
> | OPS-03 | 충분 | ConfigTest |
> | OPS-05 | 운영 단계, 스크립트 결함 | 중요 A(restore.md에 기동 로그 누락). 권고 E |
> | OPS-07 | 로컬 충분, 운영 스크립트 결함 | 로컬 메일 6회·Loki. 운영 wait_release는 중요 A |
> | SEC-05 | 1차 충분 | secret-check.md 2절: 268개 파일, 0건, stderr 0, setup_data 비어 있음. 비밀번호·쿠키 인자 전달 해소. 커밋 1~6 push 직전 재실행은 남았습니다. |
> | PERF-01 | 충분 | 조건 고정, 회차 순서(progress.txt) |
> | PERF-06 | 충분 | 중요 4 해소 |
> | PERF-07 | 대체로 충분 | gracefulStop을 한계에 적음. verify는 실험이 끝난 뒤 실행 |
>
> ## 적용 제외 항목
>
> 1회차와 같습니다. 프론트, AI, 전체 흐름 영역과 BE-01·05·08, OPS-01·02·04·06·08, SEC-01~04·06~08, PERF-02~05의 제외 이유는 변경 내용과 맞습니다. `.claude/checks.json`은 실제 앱 검사 6종이고, 성공만 돌려주는 명령은 없습니다.
>
> ## 병합 후 남은 일
>
> 이번 판정에는 반영하지 않았습니다.
>
> - 완료 기준 3(REQ-07, OPS-05·07): 운영 6회, Gmail 수신 기록, `prod/restore.md`, 중지 스케줄 diff 0. 실행 전에 중요 A 수정과 운영 SSM 점검이 필요합니다.
> - 완료 기준 5(REQ-09): 실험 당일 FDS 수동 실행 관찰
> - 실험일 전 확인: SSM `echo ok`, `get-schedule` 권한, 컨테이너 안 wget 동작, 중요 A의 읽기 전용 점검
> - SEC-05 재실행: 커밋 1~6 push 직전, 그리고 작업 기록 커밋 뒤 push 직전
> - README "운영 결과" 절, 개발일지, 작업 기록, 최종 결과 리뷰
>
> ## 이전 지적 요약
>
> 중요 1~4는 해결됐습니다. 권고 5~7, 9, 11~13, 16도 해결됐습니다. 권고 8과 10은 대체로 해결됐고, 남은 점은 권고 B·C·E에 적었습니다. 권고 14는 수용했고, 권고 15는 일부만 반영됐습니다(G). 새 지적은 중요 A와 권고 B~G입니다.
>
> 관련 파일
> - /Users/imjaehyeon/Documents/[01] - Project/j-bank/perf/run-deadlock.sh
> - /Users/imjaehyeon/Documents/[01] - Project/j-bank/perf/deadlock-summary.py
> - /Users/imjaehyeon/Documents/[01] - Project/j-bank/docs/runbook/transfer-deadlock-experiment.md
> - /Users/imjaehyeon/Documents/[01] - Project/j-bank/perf/results/deadlock/README.md
> - /Users/imjaehyeon/Documents/[01] - Project/j-bank/.claude/tasks/transfer-deadlock-experiment/progress.md
> - /Users/imjaehyeon/Documents/[01] - Project/j-bank/.claude/tasks/transfer-deadlock-experiment/evidence/verify-004/check-5.log
>
> 최종 판정: 수정 필요

### 2회차 처리 계획

- 판정: 수정 필요(차단 0, 중요 1, 권고 6). 1회차 중요 1~4 해결 확인. `review fail` 등록 후 수정 → verify → 3회차(마지막) 리뷰.
- 중요 A: host()가 원격 스크립트를 표준 입력 대신 임시 파일로 넘기고(`bash 파일 < /dev/null`), 같은 방식을 로컬 docker로 흉내 낸 점검으로 확인한다. runbook 4-1에 읽기 전용 SSM 점검을 넣는다.
- 권고 B(측정 중 중단 판정), C(종료값 파일·결과 이름), D(0바이트 요약), E(SSM 대기 중 중단 취소·재개 예시 caffeinate), F(README 문구), G(진행 기록) 반영.
