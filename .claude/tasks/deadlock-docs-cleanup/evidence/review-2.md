# 검토 결과

## 결과 리뷰 1회차 원문

(verifier 반환 원문, 인용 표시 `> `만 붙임. 대상 snapshot 66eb8eb0dbaa4e6918195411629620e4839485649f6cf4690516dc48f9eecd69, HEAD 599cff1, verify-001 통과)

> # 결과 리뷰 1회차: deadlock-docs-cleanup
>
> 결론은 **통과 권고**입니다. 차단 0건, 중요 0건, 권고 8건입니다. REQ-01~07과 완료 기준 1~5에서 구현·테스트와 어긋나는 결함은 찾지 못했습니다. 권고는 안내 위치, 문구 정확도, 증거 기록의 빈틈이며 완료를 막지 않습니다.
>
> - 모드: 결과 1회차(읽기 전용)
> - 대상 snapshot: `66eb8eb0dbaa4e6918195411629620e4839485649f6cf4690516dc48f9eecd69`. checks.json과 state.json의 review_pending이 같고 HEAD는 599cff1입니다.
> - 읽은 자료
>   - 작업 문서: task.md, plan-review.md, progress.md, state.json, logs 3개, evidence/verify-001(checks.json, changes.txt, 로그의 결과 줄)
>   - 기준: testing-policy.md, testing/security.md, checks.json
>   - 변경 파일: 5개 커밋의 파일 11개 중 긴 문서 05·06·07은 관련 절과 버전 이력만, 나머지는 전체
>   - 대조 자료: 프록시 route.ts, SecurityConfig, JacksonConfig, GlobalExceptionHandler, page.tsx, 가입 화면, AccountRepository, ADR 0015, fds.md, restore.md, 이전 작업의 progress.md·review.md 일부, 브랜치 reflog
> - 적용 기준: SEC-05
> - 확인하지 못한 범위
>   - 명령과 테스트는 실행하지 않았습니다. git diff도 직접 뽑지 못해 changes.txt의 통계와 현재 파일로 대조했습니다.
>   - 시나리오 228개, 변이 검사, shellcheck, `--check` 결과는 기록으로만 보았습니다.
>   - 사본의 sha256 값과 13:54 출력의 원본 여부는 독립 확인이 안 됩니다.
>
> ## [차단] 없음
>
> ## [중요] 없음
>
> ## [권고]
>
> 1. **재개 중단 로그가 잘못된 runbook 절을 가리킴** (`perf/run-deadlock.sh:197`)
>    - 근거: 로그는 "runbook 4-3"을 안내합니다. 재개 잔액 부족 절차는 runbook 4-2의 103~104행에 있습니다. 4-3(111~130행)은 무응답·시간 초과 복구이고 잔액 이야기가 없습니다.
>    - 영향: 할 일은 로그 문장 안에 있어 동작에는 문제가 없습니다. 다만 다른 절차를 따라갈 수 있습니다.
>    - 수정: 스크립트 문구를 4-2로 바꾸거나, runbook 4-3에 "재개가 잔액 부족으로 멈추면 4-2"를 한 줄 넣으세요.
>
> 2. **runbook이 확정하지 않은 원인을 사실처럼 적음** (`docs/runbook/transfer-deadlock-experiment.md:93`)
>    - 근거: "FDS 관찰 약 25분(실험 이체가 많으면 더 길다)"입니다. fds.md:18과 REQ-05(c)는 시간이 늘어난 원인을 확정하지 않았다고 합니다.
>    - 영향: 같은 작업에서 고친 보고서·개발일지 서술과 어긋납니다.
>    - 수정: "거래량이 다르면 시간이 다를 수 있다(원인 미확정)" 정도로 낮추세요.
>
> 3. **새 개발일지의 과장·모호한 문장** (`docs/devlog/2026-11-25_교착실험후속정리.md`)
>    - 14행: "처음 생각한 대로 … 쓰면", "되풀이될 뻔했다", "비교하도록 바꿨다"입니다. 그런 코드를 쓰려던 기록이 없고, 계획 검증 1회차는 구현 전에 "그렇게 구현하면"이라고 지적했습니다. "계획에 금액이 문자열이라는 사실이 없었고, 그대로 비교했다면 통과했을 것이다"가 사실에 맞습니다.
>    - 15행: "잘못된 구현도 통과했다"입니다. 당시 시나리오는 계획의 표였고 실행되지 않았습니다. "통과했을 것이다"가 맞습니다.
>    - 17행: 제목 "'알려 왔다'를 잘못 적었다"가 본문과 맞지 않습니다. 본문과 이전 review.md:678에서 틀린 말은 "주소로 직접 열었다"입니다. 고친 교착 개발일지 29행은 오히려 "알려 왔다"를 씁니다. "확인하지 않은 개설 방법을 적었다"로 바꾸세요. 본문 사실은 맞습니다(이전 progress.md:184의 "준비 끝").
>
> 4. **계좌 준비 절차의 "첫 계좌"가 화면과 어긋날 수 있음** (`runbook:87~88`)
>    - 근거: 홈은 API가 준 목록의 첫 항목을 맨 위에 둡니다(page.tsx:52). 목록 조회 경로에서 정렬 지정을 찾지 못했습니다(AccountRepository:22~24, account 패키지에 Sort·PageableDefault 없음). 먼저 연 계좌가 늘 맨 위라는 보장이 없습니다.
>    - 영향: 한 계좌에 두 번 입금할 수 있습니다. 새 잔액 검사가 막아 주므로 피해는 작습니다.
>    - 수정: "홈 맨 위 계좌"와 "그 아래 계좌"로 적으세요. 주소와 "이체 버튼 주소에서 ID 확인" 설명은 맞습니다(page.tsx:180).
>
> 5. **6회가 모두 끝난 상태의 재실행** (`perf/run-deadlock.sh:516`, `runbook:89`)
>    - 근거: REQ-01의 전제는 "결과가 없는 회차가 있음"입니다. 실제 검사는 6회가 모두 끝난 상태에서도 돕니다. 마지막 복귀만 실패해 `--from` 없이 다시 실행하면, 한쪽이 1억 미만일 때 복귀도 멈춥니다.
>    - 영향: 로그가 `--from`을 안내하고 5절 수동 복귀도 있어 막다른 길은 아닙니다. 다만 runbook 89행의 "설정 복귀만 한다"는 이제 잔액이 충족될 때만 맞습니다.
>    - 수정: runbook에 "잔액 확인을 통과하면"과, 복귀만 다시 할 때의 방법(`--from` 또는 5절)을 한 줄 더하세요.
>
> 6. **시나리오 증거의 빈틈** (`logs/balance-check.md`)
>    - 3행: 실행 시각 23:17:30이 커밋 599a8c8의 시각(reflog 1795616250 = 23:17:30 KST)과 초까지 같습니다. 실행이 아니라 기록 작성·커밋 시각으로 보입니다. 구분해 적으세요.
>    - 5행·383~426행: 가짜 `docker`·`k6` 전문과 변이 10종의 실제 변경 내용이 없습니다.
>    - 328행: 폴더 수를 `conditions.json` 개수로 셉니다. task.md:90의 판정은 새 `*-vu*-r1` 폴더 0개입니다. 지금 코드는 검사가 `run_round`보다 먼저라 결과가 같습니다.
>    - 350행: "해제 미확인 + 합 부족" 사례는 사유 로그와 curl 2회를 판정하지 않습니다. 출력의 curl=2와 바로 아래 대조 사례가 보완합니다.
>    - 24행: "초과" 변이가 3건만 실패했으므로 새 시작 쪽만 바꾼 것입니다. 재개 쪽 "초과"는 변이로 돌리지 않았습니다. 다만 합이 정확히 2억인 통과 사례 4개가 있어 걸립니다.
>    - 6행: shellcheck와 `bash -n`, progress.md:17의 `--check` 일치는 명령·종료 코드·시각 없이 문장만 있습니다(testing-policy "실행 기록").
>
> 7. **06의 범위가 넓은 표현** (`docs/06_J-Bank_인프라아키텍처.md:55`)
>    - 근거: "목록에 없으면 요청이 403으로 거절된다(2026-11-23 운영에서 확인)"입니다. 운영 확인은 POST뿐입니다(cors-check.md:3). 같은 출처 GET에는 보통 `Origin`이 없습니다. 05는 "로그인과 가입 같은 요청"이라 정확합니다.
>    - 수정: 06도 "`Origin`이 붙는 요청(로그인·가입 등)"으로 맞추세요.
>
> 8. **cors-check 36행에 같은 종류의 서술이 남음** (`perf/results/deadlock/prod/cors-check.md:36`)
>    - 근거: "사용자는 그 화면에서 계좌 2개를 열고"는 교착 개발일지 29행의 "어떤 방법으로 열었는지는 확인하지 않았다"와 어긋납니다. REQ-05(a)가 본문 유지를 정해 이번 위반은 아닙니다.
>    - 수정: 후속에서 "사용자 보고"를 붙이세요.
>
> ## 항목별 확인 결과
>
> **(1) REQ-01~04와 완료 기준 1: 충족**
> - 금액 해석(189~202행)은 맞습니다.
>   - 문자열이면서 `^[0-9]+([.][0-9]+)?$`인 값만 정수부 × 100 + 소수 두 자리로 바꿉니다.
>   - 셋째 자리부터는 버려 부족을 통과시키지 않습니다. 실제 금액 범위에서 정수 정밀도 문제도 없습니다.
>   - 없음·null·빈 문자열·문자·음수·숫자 타입은 모두 "잔액을 읽지 못했다"로 멈춥니다. 숫자 타입 거부는 JacksonConfig:18의 문자열 직렬화와 맞고 안전한 쪽입니다.
> - 새 시작은 각 1억 미만, 재개는 합 2억 미만에서 멈춥니다. 둘 다 정확한 경계값은 통과합니다(Q-01).
> - `check_start_balance`는 `if`의 조건이 아니라 본문에서 불려 `set -e`가 살아 있습니다. 재개 때 `wait_release`(528행)와 모든 `host` 호출보다 앞이고, 앞선 외부 호출은 로그인과 계좌 조회뿐입니다.
> - 불변성
>   - `login_and_resolve_accounts`는 값을 그대로 담기만 합니다. 잔액이 없어도 `one_transfer`와 복귀 확인이 깨지지 않습니다.
>   - `local`·`prod-check`·`prod-restore-check` 경로에는 검사 호출이 없습니다. usage 4~7행 위치와 계좌 부족 문구도 그대로입니다.
>   - 변경량(+26, −1)이 이 내용과 맞습니다.
>
> **(2) 시나리오와 변이 검사: 근거로 인정**
> - 4회차 권고 1~5가 들어 있습니다(계좌 순서 11·10·9, REQ-03의 재개와 양쪽 계좌, base 69e0665 비교, 재개 중단 `balance` 2억, 짧은 쿠키).
> - 스크립트를 따라 세면 검사 수가 정확히 228개입니다(45+23+150+7+3).
> - 변이 실패 수도 코드로 다시 계산한 값과 맞습니다.
>   - 첫 계좌만·둘째만 10, 합만 15, 각 1억 요구 9
>   - `balance`를 읽음·검사 없음 132, 값 없음을 0으로 봄 60
> - 통과 사례의 curl 3회와 복귀 확인의 curl 4회·aws 2회도 코드 흐름과 맞습니다.
>
> **(3) 문서: 충족**
> - REQ-07
>   - 항목이 모두 있습니다. 22시 시작(91·93), 23시 판단(134), 다음 부팅(151), 실험일(172), 중단 두 경우(102·104), `--from`(103), 0원·2억 한계(105), 계좌 준비(86~88), 이전 결과 이동(89)입니다.
>   - `18시|17:40|14시`는 0건입니다(검색으로 확인).
>   - 86행의 "없는 날짜면 500"은 이전 progress.md:182의 재현과 처리기 부재(GlobalExceptionHandler)로 뒷받침됩니다.
> - REQ-06
>   - 05는 두 단정을 모두 고치고 SameSite 이유를 유지했습니다. 06은 본문, 07은 원문 유지와 정정 문장이며 버전 이력도 있습니다.
>   - route.ts:14~16(`host`·`content-length`만 제거)과 SecurityConfig:83~88에 맞습니다.
> - REQ-05
>   - (a) 23~31행이 cors-output.md와 모두 같고 403 세 행과 수정 전 표는 유지됐습니다.
>   - (b)~(e) 반영됐습니다. (e)의 ENABLED는 restore.md:8과 맞습니다.
>   - README는 표 밖 두 줄(135·136)만 바뀌어 `--check`에 영향이 없습니다.
>
> **(4) 커밋과 범위**
> - 5개 커밋은 승인된 계획과 순서·내용이 같고 원자적입니다. 변경 파일 11개가 모두 범위 안이며 앱 코드·인프라 변경은 없습니다.
> - 과장 서술은 권고 2·3뿐입니다.
>
> **(5) SEC-05: 충족**
> - 목록 25개, 패턴 식, 종료 코드 1·0줄, stderr 0바이트가 기록돼 있습니다.
> - 같은 패턴을 작업 폴더·docs·perf/results/deadlock에서 검색해 0건을 확인했습니다.
> - `review.md` 등 이후 파일을 포함한 push 직전 재실행이 남아 있습니다.
>
> **검사 증거**
> - checks.json의 6종은 실제 앱 검사이고 모두 종료 코드 0입니다.
> - 백엔드 `:test`는 UP-TO-DATE라 이번에 다시 실행되지 않았습니다(check-5.log:7). 앱 입력이 바뀌지 않아 문제로 보지 않지만 기록에 적어 두세요.
>
> **적용 제외**
> - 프론트·백엔드·AI·DevOps·성능·전체 흐름과 SEC-05 외 보안 항목의 제외는 타당합니다. 앱·배포 변경이 없고 새 검사는 서버에 닿기 전에 끝납니다.
>
> ## 이전 지적별 상태
>
> | 지적 | 상태 | 근거 |
> | --- | --- | --- |
> | 계획 1회차 중요 1~4, 2회차 중요 1 | 해결 | 구현과 시나리오에서 확인 |
> | 4회차 권고 1~5 | 반영 | balance-check.md의 스크립트와 출력 |
>
> 관련 파일
> - /Users/imjaehyeon/Documents/[01] - Project/j-bank/perf/run-deadlock.sh
> - /Users/imjaehyeon/Documents/[01] - Project/j-bank/docs/runbook/transfer-deadlock-experiment.md
> - /Users/imjaehyeon/Documents/[01] - Project/j-bank/docs/devlog/2026-11-25_교착실험후속정리.md
> - /Users/imjaehyeon/Documents/[01] - Project/j-bank/docs/06_J-Bank_인프라아키텍처.md
> - /Users/imjaehyeon/Documents/[01] - Project/j-bank/perf/results/deadlock/prod/cors-check.md
> - /Users/imjaehyeon/Documents/[01] - Project/j-bank/.claude/tasks/deadlock-docs-cleanup/logs/balance-check.md
>
> 최종 판정: 통과 권고

### 1회차 처리

- 판정: 통과 권고(차단·중요 0, 권고 8). 이 작업의 목적이 문서를 사실에 맞추는 것이라, 새로 쓴 문서의 부정확한 서술을 고치고 재검토받기로 했다. 통과 등록은 하지 않고 start로 구현 단계에 돌아간다.

### 1회차 지적별 처리

- 권고 1(runbook 절 번호): 53e33a3. 권고 2·4·5·7(runbook·06 서술): 7ad6b79. 권고 3(개발일지 서술): 31a6060, 5016c19(변이 개수).
- 권고 6(증거 기록): balance-check.md를 다시 실행해 시각·종료 코드·변이 정의·가짜 명령 전문을 남겼다(시나리오 233개, 변이 11종).
- 권고 8(cors-check 36행): REQ-05(a)가 본문 유지를 정해 그대로 두고 후속으로 남긴다.
- verify-001·002의 백엔드 `:test`는 Gradle UP-TO-DATE다. 앱 입력이 바뀌지 않았다.

## 결과 리뷰 2회차 원문

(verifier 반환 원문, 인용 표시 `> `만 붙임. 대상 snapshot 2f743ca40570c92872d904ba5df418703e39c442ee4b07c8b04c5b300bf13f87, HEAD 5016c19, verify-002 통과)

> # 결과 리뷰 2회차(재리뷰): deadlock-docs-cleanup
>
> 결론은 **통과 권고**입니다. 차단 0건, 중요 0건, 권고 3건입니다. 1회차 권고 1~5·7은 고쳐졌고, 6의 기록 보강은 다른 증거와 맞습니다. 8을 두는 것도 타당합니다. 수정으로 생긴 회귀는 찾지 못했습니다.
>
> - 모드: 결과 2회차(읽기 전용)
> - 대상 snapshot: `2f743ca40570c92872d904ba5df418703e39c442ee4b07c8b04c5b300bf13f87`. checks.json과 state.json의 review_pending이 같고 git head는 5016c19입니다. approved_plan_snapshot(236952ee…)은 마지막 계획 검증(4회차) 때 값과 같습니다.
> - 읽은 자료
>   - 작업 문서: task.md, review.md, progress.md, state.json, plan-review.md 일부, logs 3개
>   - 검사 증거: evidence/verify-002의 checks.json, changes.txt, 로그 결과 줄
>   - 변경 파일: run-deadlock.sh·runbook·새 개발일지·cors-check 전체, 05·06·07의 CORS 서술, 교착 개발일지와 보고서의 관련 줄
>   - 대조 자료: page.tsx, fds.md, 브랜치 reflog, checks.json
> - 적용 기준: SEC-05
> - 확인하지 못한 범위
>   - 명령과 테스트는 실행하지 않았습니다.
>   - 사본의 sha256 `1447ff99…`이 커밋된 파일과 같은지, shellcheck·`bash -n`·`--check`의 종료 코드가 실제로 0이었는지는 기록으로만 보았습니다.
>
> ## [차단] 없음
>
> ## [중요] 없음
>
> ## [권고] 완료를 막지 않음
>
> 1. **개발일지의 "구현 전에 … 전 단위 정수로 비교하는 조건을 정했다"에 근거가 없음** (`docs/devlog/2026-11-25_교착실험후속정리.md:14`)
>    - 근거: 계획 검증과 task.md가 구현 전에 정한 것은 "문자열 십진수이고, 해석되지 않으면 중단"까지입니다(plan-review.md:41·161, task.md:46). 전 단위 정수는 두 문서에 없고 구현에서 고른 방법입니다(run-deadlock.sh:186).
>    - 영향: 문장 하나의 시점 표현입니다. 동작과 다른 서술에는 영향이 없습니다.
>    - 수정: "구현 전에 문자열 십진수로 해석하고 해석되지 않으면 멈추는 조건을 정했다. 구현에서는 전 단위 정수로 바꿔 비교한다" 정도로 나누세요.
>
> 2. **runbook 89행의 문장 순서가 헷갈릴 수 있음** (`docs/runbook/transfer-deadlock-experiment.md:89`)
>    - 근거: "새로 처음부터 다시 실험할 때" 항목 가운데에 "마지막 설정 복귀만 실패했다면 …"이 들어갔습니다. 바로 뒤의 "회차 폴더와 … 옮긴 뒤 시작한다"가 복귀 실패 때 할 일처럼 읽힐 수 있습니다.
>    - 영향: 복귀만 하려던 사람이 결과 폴더를 옮기면, `--from unsorted-for-update-vu100`이 그 회차를 건너뛰지 않고 다시 돕니다(run-deadlock.sh:534).
>    - 내용 자체는 맞습니다. 이 명령은 결과가 있는 마지막 회차를 건너뛰고 `prod_restore`로 갑니다(534~546행).
>    - 수정: 복귀 실패 문장을 별도 항목으로 빼고 "이때는 결과 폴더를 옮기지 않는다"를 붙이세요.
>
> 3. **review.md의 "1회차 처리"에 지적별 처리가 없음** (`.claude/tasks/deadlock-docs-cleanup/review.md:139~141`)
>    - 근거: testing-policy.md:78은 각 지적의 처리 근거를 review.md에 덧붙이라고 합니다. 지금은 방침만 있고, 커밋별 내역과 8을 둔 이유는 progress.md:20에만 있습니다.
>    - 영향: 기록의 위치 문제이며 내용은 남아 있습니다.
>    - 수정: 1~8의 처리(커밋 또는 남긴 이유)를 review.md에 옮겨 적으세요. 1회차가 기록을 권한 백엔드 테스트의 UP-TO-DATE 사실도 함께 적으면 됩니다. verify-002에서도 같습니다(check-5.log:7).
>
> ## 이전 지적별 상태
>
> | 1회차 권고 | 상태 | 근거 |
> | --- | --- | --- |
> | 1 runbook 절 번호 | 해결 | run-deadlock.sh:197이 4-2를 가리키고 절차는 runbook 104행에 있음. `4-3` 안내는 스크립트에 남지 않음 |
> | 2 원인 단정 | 해결 | runbook:93 "2026-11-25 실측 약 23분 … 확정하지 않음". fds.md:9·18, README:136과 일치 |
> | 3 개발일지 세 문장 | 해결(14행에 권고 1 남음) | 14·15행은 "통과했을 것이다"로 낮아졌고 계획 검증 기록과 맞음. 17행 제목과 본문은 교착 개발일지 29행과 맞음 |
> | 4 "첫 계좌" | 해결 | runbook:88 "홈 맨 위 계좌"와 "그 아래 계좌". page.tsx:88·166~188(상세 링크는 맨 위만, 이체 버튼 주소에 ID)과 맞음 |
> | 5 6회 완료 뒤 재실행 | 해결(권고 2는 배치 문제) | runbook:89에 잔액 조건과 `--from`·5절 방법이 있고 코드와 맞음 |
> | 6 증거 기록 | 해결 | 아래 확인 결과 |
> | 7 06의 범위 | 해결 | 06:55 "`Origin`이 붙는 요청(로그인·가입 등)", "POST로 확인". cors-check.md:3·12·13과 맞음 |
> | 8 cors-check 36행 | 남기는 것이 타당 | REQ-05(a)가 본문을 그대로 두라고 정함. 고치려면 요구사항 변경과 계획 재검증이 필요함. 틀렸다고 확인된 서술이 아니고 후속으로 기록됨(progress.md:20) |
>
> ## 권고 6의 기록 확인
>
> - **시각**: 앞뒤가 맞습니다.
>   - 시나리오 23:25:56~23:26:05, 변이 ~23:27:18, 기록 23:27:35, 커밋 53e33a3 23:27:37(reflog 1795616857) 순입니다. "커밋 전 작업 폴더"라는 말과도 맞습니다.
>   - 5016c19는 23:28:06이고, verify-002는 23:28:07~23:28:57에 그 커밋에서 돌았습니다. 비밀값 검사 23:28:58과 review-begin 23:29:06이 뒤를 잇습니다.
> - **검사 수**: 스크립트를 따라 세면 45 + 28 + 150 + 7 + 3 = 233개입니다. 늘어난 5개는 "해제 미확인 + 합 부족"에 빠져 있던 판정이며 사유 로그와 curl 2회가 들어갔습니다.
> - **폴더 판정**: 새 `*-vu*-r1` 폴더 수로 바뀌었고 사전 파일 폴더만 제외합니다(366행). task.md:90과 맞습니다.
> - **변이 11종**: mutate.py의 치환 대상은 현재 코드에 있습니다. 실패 수를 코드로 다시 계산한 값이 표와 모두 같습니다.
>   - first_only·second_only 10, start_sum_only 15, start_strict_greater 3, string_compare 30
>   - resume_each 9, resume_strict_greater 13(통과 사례 4개 × 3 + 대조 1)
>   - no_check·uses_balance 135(늘어난 판정 3개만큼 132에서 증가), null_as_zero 60, tonumber_in_login 34
>   - 1회차에 빠져 있던 재개 쪽 "초과" 변이도 들어갔습니다.
> - **전문과 종료 코드**: 시나리오, 가짜 curl·aws·docker·k6, mutate.py 전문이 있습니다. `bash -n`·shellcheck·`--check`는 명령·종료 코드·시각이 적혔습니다.
>
> ## 전체 범위와 회귀
>
> - **REQ-01~04**: 스크립트에서 바뀐 것은 안내 문구의 절 번호뿐입니다. `jq` 판정, 호출 위치, 다른 모드의 경로는 1회차에 확인한 내용과 같습니다.
> - **REQ-05**: cors-check 23~31행이 cors-output.md와 같습니다. (a)의 표와 README·교착 개발일지의 (c), 개발일지의 (d) 서술이 유지됐습니다.
> - **REQ-06**: 05·06·07에 정정이 있고 남은 단정은 모두 정정 문장과 함께 있습니다. 세 문서끼리 어긋나지 않습니다.
> - **REQ-07**: 항목이 모두 있고 `18시|17:40|14시`는 0건입니다(검색으로 확인).
> - **커밋**
>   - 추가 4개는 스크립트, 문서, 개발일지로 나뉘었습니다. 변경 파일은 1회차와 같은 11개이고 앱 코드·인프라 변경은 없습니다.
>   - 추가 커밋은 승인된 1~6번 계획에는 없습니다. 같은 범위의 정정이므로 결함은 아니지만, 최종 보고에서 사용자에게 알려 주세요.
> - **SEC-05**: 28개 파일 목록과 패턴, 종료 코드 1·0줄, stderr 0바이트가 기록돼 있습니다. 같은 패턴을 작업 폴더, docs, perf/results/deadlock의 md에서 검색해 0건을 확인했습니다. push 직전 재실행은 남아 있습니다.
> - **검사 증거**: verify-002의 6종은 실제 앱 검사이고 모두 종료 코드 0입니다. 하네스 37개, 프론트 32개가 통과했고 빌드도 성공했습니다.
> - **적용 제외**: 1회차와 같은 이유로 타당합니다.
>
> 관련 파일
> - /Users/imjaehyeon/Documents/[01] - Project/j-bank/docs/devlog/2026-11-25_교착실험후속정리.md
> - /Users/imjaehyeon/Documents/[01] - Project/j-bank/docs/runbook/transfer-deadlock-experiment.md
> - /Users/imjaehyeon/Documents/[01] - Project/j-bank/perf/run-deadlock.sh
> - /Users/imjaehyeon/Documents/[01] - Project/j-bank/.claude/tasks/deadlock-docs-cleanup/review.md
> - /Users/imjaehyeon/Documents/[01] - Project/j-bank/.claude/tasks/deadlock-docs-cleanup/logs/balance-check.md
>
> 최종 판정: 통과 권고

### 2회차 처리

- 판정: 통과 권고. 차단·중요 0, 권고 3. 통과로 등록한다.
- 권고 1(개발일지 14행의 시점 표현), 권고 2(runbook 89행의 문장 순서)는 결과 파일을 고치면 snapshot이 바뀌어 재검토가 필요하므로 이번에 고치지 않고 후속 문서 정리로 남긴다. 권고 2는 "복귀만 실패했을 때는 결과 폴더를 옮기지 않는다"가 핵심이라 후속에 먼저 넣는다.
- 권고 3(지적별 처리)은 위 "1회차 지적별 처리"로 반영했다.

## 요구사항별 검증

| 요구사항 ID | 구현 위치 | 테스트·실행 증거 | 판정 |
| --- | --- | --- | --- |
| REQ-01 | perf/run-deadlock.sh check_start_balance, prod-all | logs/balance-check.md 시나리오(새 시작) | 통과 |
| REQ-02 | perf/run-deadlock.sh check_start_balance resume | logs/balance-check.md 시나리오(재개, 해제 미확인) | 통과 |
| REQ-03 | perf/run-deadlock.sh cents 해석 | logs/balance-check.md 시나리오(값 없음·null·문자·숫자 타입·계좌 1개) | 통과 |
| REQ-04 | perf/run-deadlock.sh login_and_resolve_accounts | logs/balance-check.md 시나리오(local·prod-check·prod-restore-check·비밀번호) | 통과 |
| REQ-05 | prod/cors-check.md, prod/alert-mails.md, README.md, 교착 개발일지 | logs/cors-output.md 대조, deadlock-summary.py --check | 통과 |
| REQ-06 | docs/05, 06, 07 | grep 검토(리뷰 2회차) | 통과 |
| REQ-07 | docs/runbook/transfer-deadlock-experiment.md | 낡은 시각(18시, 17:40, 14시) 검색 0건, 항목별 검토 | 통과 |

## 독립 검증 결과

- verifier(읽기 전용) 2회차 최종 판정: 통과 권고. 차단·중요 지적 없음.
- 대상 snapshot: 2f743ca40570c92872d904ba5df418703e39c442ee4b07c8b04c5b300bf13f87
