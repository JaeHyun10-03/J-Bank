# 검토 결과

## 검토 범위

결과 리뷰 2/3회차. 작업 시작 c88dde0 → 39fd011(PR #10으로 main 81c5f57에 병합) + 미커밋 개발일지 운영 반영 절. 최신 검사 verify-002.
1회차(review fail, 배포 전 범위 통과 권고, 원문은 evidence/review-1.md)에서 남았던 운영 배포 뒤 항목을 logs/prod-check.md로 확인했다.

## 독립 검증 결과

- 호출: verifier, agentId a40783cba131cb1bf, 2026-10-02(review-begin 2/3 직후).
- 대상 snapshot: b8c38b4ef43db85efb6658adca40ca1549cdc3da16ea45a4cc255950f92baa06
- 응답 원문(인용 표시 `> `만 붙이고 내용 그대로):

> # 결과 리뷰 2/3회차: prod-metrics-scrape
>
> ## 검증 대상
> - 모드: 결과 리뷰(재리뷰). 작업 ID: prod-metrics-scrape.
> - snapshot: `b8c38b4ef43db85efb6658adca40ca1549cdc3da16ea45a4cc255950f92baa06`(evidence/checks.json). git head `39fd011`, dirty=true(개발일지 미커밋분), unchanged_during_checks=true, skipped_checks 없음.
> - state.json: review_attempts 2, 2회차 호출 기록의 snapshot이 위 값과 같습니다.
>
> ## 읽은 자료
> - 문서: docs/testing-policy.md, task.md, review.md(1회차 원문·처리 내역), progress.md, state.json
> - 운영 증거: logs/prod-check.md
> - 검사 증거: evidence/checks.json(verify-002), verify-002/changes.txt, verify-002/check-4.log·check-5.log, verify-001/changes.txt·check-5.log
> - 변경 문서: docs/devlog/2026-10-01_운영지표수집복구.md(미커밋 "운영 반영" 절 포함), perf/README.md 387~388행, docs/adr/0010(프론트 호스팅 확인용)
>
> ## 확인하지 못한 범위
> - 운영 원출력은 없습니다. prod-check.md는 메인이 만든 요약이라 SSM 명령 ID(c25dd024…, dda7e5bf…)와 시각으로만 추적할 수 있습니다. 실제 SSM·curl 실행 여부, Prometheus 재시작에 대한 사용자 승인은 읽기만으로 확인할 수 없습니다.
> - 운영 프론트(Vercel)가 어느 커밋을 배포했는지는 확인하지 못했습니다(권고-2 참고).
> - 테스트는 직접 실행하지 않았습니다. snapshot 해시도 재계산하지 않았습니다.
>
> ## 1. 운영 증거 대조(REQ-09·AC-06 등)
> | 기준 | task.md 기대값 | prod-check.md 근거 | 판정 |
> | --- | --- | --- | --- |
> | 반영 전제 | 새 이미지·compose 반영 | HEAD 81c5f57, IMAGE_TAG와 이미지 revision 라벨이 81c5f57로 같음, 컨테이너 env `MANAGEMENT_SERVER_PORT=9095`(새 compose에만 있는 값), Up 16분은 09:02 기동이라 sync-latest 시각과 맞음 | 확인 |
> | OPS-07 | target up, `up`=1, `jbank_credit_pending_count` 결과 있음 | target `api:9095` health=up, lastError 없음. `up{instance="api:9095"}`=1로 9095 시계열임을 명시. 남은 `api:8080`=0은 재시작으로 staleness marker 없이 남은 5분 lookback 시계열이라는 설명이 타당함. `jbank_credit_pending_count{instance="api:9095"}`=0(결과 있음). 목표의 Hikari 지표도 `hikaricp_connections_max`=10으로 확인 | 충족 |
> | OPS-04 | api healthy, readiness는 관리 포트 | `(healthy)`, 컨테이너 안 9095 readiness `UP`. healthcheck 정의 자체는 docker inspect로 보지 않았지만, 새 compose로 만든 컨테이너(env로 확인)라 REQ-08의 compose 렌더링 근거와 연결됨 | 충족 |
> | REQ-09 외부 | 미인증 health 404, prometheus 비200, readyz 200 | 모두 "미인증" 요청으로 명시. health는 404 `COMMON_004_NOT_FOUND`라 Caddy가 아니라 api 본 포트가 응답했다는 증거임. prometheus는 401 `COMMON_002`, readyz·livez 200 | 충족 |
> | SEC-05 | 생성 비밀번호 로그 0건, 비밀값 미기록 | api 로그 0건. 대상 컨테이너가 새 이미지로 기동한 것이라 조회 범위가 적절함. `.env`는 IMAGE_TAG 줄만, env는 MANAGEMENT_SERVER_PORT만 출력. 문서에 비밀값 없음(인스턴스 ID·SSM 명령 ID는 비밀 아님) | 충족 |
> | SEC-07 | 외부에서 prometheus 200 아님 | 401. 호스트 미매핑·Caddy 미프록시는 1회차 compose-config로 확인됨 | 충족 |
> | E2E-02 | 외부 readyz 200, 프론트 `{"online":true}` | 둘 다 확인 | 충족(권고-2) |
> | AC-06 | 결과를 logs/prod-check.md에 저장 | 저장됨 | 충족 |
>
> 재시작 전 상태(`api:8080` 401)와 그 원인(단일 파일 바인드 마운트)이 task.md 재점검 항목의 예상대로 기록돼 있습니다. 운영 데이터 변경은 없습니다.
>
> ## 2. 1회차 이후 회귀 여부(verify-002)
> - 1회차 이후 추가된 변경 파일은 docs/devlog 2개뿐입니다. perf/README.md는 원래 목록에 있던 파일이고 이번에는 문구만 바뀌었습니다. 앱·테스트·인프라 코드는 그대로입니다(verify-001과 verify-002의 changes.txt 비교).
> - 6개 검사 모두 exit 0입니다. 프론트 jest는 31/31 통과(check-4)입니다.
> - 백엔드 check-5는 `:test UP-TO-DATE`라서 이번에 테스트를 다시 실행하지 않았습니다. Gradle은 입력(소스·클래스패스)이 그대로일 때만 이 판정을 내립니다. 백엔드 입력은 1회차 이후 바뀌지 않았으므로 verify-001의 실제 실행 결과(`:test` 실행, 08:08Z)가 그대로 유효합니다. 회귀는 없습니다(권고-1).
>
> ## 3. 1회차 개선 지적 처리
> - 개선-1 해결: review.md "발견한 문제"와 개발일지 "겪은 문제"에 "전제 오류 → SamePort 테스트가 검출 → 조건 추가"가 기록됐습니다.
> - 개선-2 해결: review.md에서 증거 범위를 "전체 컨텍스트 통합 테스트 0건 + 빈 부재"로 한정했습니다. 이번에 운영 로그 0건이 더해졌습니다.
> - 개선-3 해결(미반영 결정): 외부에서 닿지 않는 포트라 추가하지 않는다는 판단은 선택 사항에 대한 결정으로 타당합니다.
> - 개선-4 해결: perf/README.md:388이 "본 포트의 상태 확인 경로는 `/readyz`·`/livez`"로 고쳐졌습니다(9c385cc).
>
> ## REQ별 판정
> | REQ | 판정 | 근거 |
> | --- | --- | --- |
> | REQ-01~05, 07, 08, 10, 11 | 통과 | 1회차 판정을 유지합니다. 코드·테스트 입력은 바뀌지 않았고 verify-002는 통과입니다. REQ-01·05는 운영(관리 포트 jvm_ 56줄, 외부 readyz·livez 200)에서도 확인됐습니다 |
> | REQ-06 | 통과 | 빈 부재·FullFlow 통과 + 운영 로그 0건으로 "운영 로그 대기"가 해소됐습니다 |
> | REQ-09 | 통과 | 위 1절 |
>
> review.md 요구사항별 검증 표는 REQ-06·REQ-09를 포함해 REQ-01~11 모두 "통과"로 등록해도 됩니다. 이때 REQ-06·REQ-09 행에 logs/prod-check.md를 증거로 적고, "완료 기준별 근거"의 AC-06도 충족으로 바꾸십시오.
>
> ## 지적 사항
> 차단: 없음. 중요: 없음.
>
> **[권고-1] 백엔드 검사는 Gradle UP-TO-DATE 판정임을 기록**
> - 근거: verify-002/check-5.log:8 `> Task :test UP-TO-DATE`.
> - 영향: 결과는 유효하지만, review.md에 "verify-002에서 백엔드 테스트를 다시 실행했다"고 적으면 사실과 다릅니다.
> - 수정: "백엔드 입력이 바뀌지 않아 Gradle이 verify-001 실행 결과를 유지(UP-TO-DATE)"라고 적으십시오. 재실행은 필요하지 않습니다.
>
> **[권고-2] E2E-02의 online만으로는 새 프론트 코드 경로를 증명할 수 없음**
> - route.ts는 응답이 오기만 하면 online으로 판정합니다. 그래서 옛 프론트가 `/actuator/health` 404를 받아도 `{"online":true}`가 나옵니다.
> - task.md의 기대값(외부 readyz 200 + online)은 그대로 충족됐고 `/readyz` 호출은 REQ-07 단위 테스트가 보장하므로, 차단 사유는 아닙니다.
> - 필요하면 Vercel 배포 커밋이 81c5f57 이후인지 한 줄 덧붙이십시오.
>
> **[권고-3] 실행 기록 형식 보완(선택)**
> - prod-check.md에는 시각·SSM 명령 ID·결과·기대값이 있지만, 실제 조회 명령(예: `wget localhost:9090/api/v1/query?query=...`, curl 옵션)은 적혀 있지 않습니다.
> - testing-policy.md:33은 실행 기록에 명령을 남기라고 합니다. 후속 PR 전에 명령 줄을 짧게 추가하면 재현성이 좋아집니다.
> - progress.md에도 2026-10-02 운영 확인·재시작 항목이 아직 없으니 함께 남기십시오.
>
> ## 적용 제외 항목
> 1회차 판정(SEC-03·04·06·08, BE-01·04~08, FE-02·04~07, E2E-01·03~05, OPS-01·02·06·08, 성능)을 유지합니다. 새 근거는 없습니다.
>
> ## 이전 지적 처리
> - 1회차 개선-1~4는 모두 해결됐습니다(위 3절).
> - 1회차의 "배포 후 대기" 항목(REQ-09, AC-06, OPS-04, OPS-07, E2E-02, SEC-05 운영 로그, SEC-07 외부 확인)도 모두 해결됐습니다.
>
> ## 남은 일
> - review pass를 등록하고 complete를 실행하십시오. 후속 PR(커밋 10·11)도 남아 있습니다.
> - 미커밋 개발일지·prod-check.md·progress.md를 커밋한 뒤 snapshot이 바뀌어 하네스가 재검사를 요구하면 그 절차를 따르십시오.
>
> 최종 판정: 통과 권고

## 요구사항별 검증

| 요구사항 ID | 구현 위치 | 테스트·실행 증거 | 판정 |
| --- | --- | --- | --- |
| REQ-01 | PrometheusScrapeRequestMatcher.java, SecurityConfig.java, docker-compose.prod.yml `MANAGEMENT_SERVER_PORT` | ActuatorManagementPortIntegrationTest(관리 포트 200·`jvm_`·`hikaricp_`), verify-001·002, 운영 9095 `jvm_` 56줄(logs/prod-check.md) | 통과 |
| REQ-02 | 관리 포트 분리로 본 포트 actuator 제거, SecurityConfig | ActuatorManagementPortIntegrationTest(미인증 401, 로그인 404 COMMON_004·지표 없음), 운영 외부 prometheus 401 | 통과 |
| REQ-03 | PrometheusScrapeRequestMatcher(관리 포트 != 본 포트 조건) | PrometheusScrapeRequestMatcherTest(같은 포트·-1), ActuatorSamePortIntegrationTest(미인증 401) | 통과 |
| REQ-04 | PrometheusScrapeRequestMatcher(GET `/actuator/prometheus`만) | 단위(info·하위 경로·POST 불허), 통합(관리 포트 info 401) | 통과 |
| REQ-05 | application.yml additional-path, SecurityConfig PUBLIC_PATHS | 통합(readyz·livez 200), SecurityConfigTest, 운영 외부 readyz·livez 200 | 통과 |
| REQ-06 | JbankApiApplication.java exclude | FullFlowIntegrationTest 빈 부재·전체 흐름 통과, 운영 api 로그 생성 비밀번호 0건(logs/prod-check.md) | 통과 |
| REQ-07 | apps/frontend/app/api/server-status/route.ts | route.test.ts(`/readyz` 호출, online/offline 판정), verify-002 check-4, 운영 프론트 81c5f57 배포·online | 통과 |
| REQ-08 | docker-compose.prod.yml, prometheus.prod.yml | logs/compose-config.txt, 운영 api env 9095·healthy | 통과 |
| REQ-09 | 운영 배포(81c5f57) + prometheus 재시작 | logs/prod-check.md: target api:9095 up, up=1, jbank_credit_pending_count 결과, 외부 health 404·prometheus 401·readyz 200, 로그 0건 | 통과 |
| REQ-10 | ADR 0013, docs/06 2·3.4·3.5절, perf/README, 개발일지 | verifier 1·2회차 대조 | 통과 |
| REQ-11 | GlobalExceptionHandler.java | GlobalExceptionHandlerTest, ActuatorManagementPortIntegrationTest(로그인 404·health 404 COMMON_004), 운영 외부 health 404 COMMON_004 | 통과 |

## 지적별 처리

- 차단·중요: 없음.
- 권고-1: verify-002의 백엔드 검사는 입력 불변으로 Gradle `:test UP-TO-DATE`였고, 실제 테스트 실행 결과는 verify-001(1회차 이후 백엔드 입력 변경 없음)이다. 재실행했다고 적지 않는다.
- 권고-2: Vercel Production 배포가 81c5f57 success(2026-10-01T11:53:51Z)임을 GitHub deployments API로 확인해 logs/prod-check.md 4절에 추가.
- 권고-3: logs/prod-check.md에 실행 명령 절 추가, progress.md에 2026-10-02 운영 확인 기록 추가.
- 1회차 개선-1~4: 해결(위 원문 3절).

## 완료 기준별 근거

- AC-01·02·03·04·05·07·08: verify-001(백엔드 실제 실행)·verify-002, logs/compose-config.txt.
- AC-06: logs/prod-check.md로 충족.
- 하네스 verify: verify-002 통과.

## 성능 테스트 확인

불필요(task.md 성능 테스트 절). 요청 처리 경로 변경 없음. 1·2회차 verifier 모두 타당하다고 판정.

## 발견한 문제

- 구현 중: 관리 포트 미설정(같은 포트)일 때 Boot가 `local.management.port`를 본 포트 값으로 채워 초안 매처가 본 포트 지표를 미인증으로 열었다. 통합 테스트로 잡아 fbdd395 전에 고쳤다.
- 운영 반영 중: 부팅 시 Prometheus가 sync-latest의 git reset보다 먼저 떠서 옛 설정을 읽었다. 계획에서 예상한 대로 수동 재시작으로 해결. 부팅 반영 경로에는 설정 변경 시 재시작 단계가 없다는 한계가 남는다.

## 판정과 이유

review pass(2/3). 모든 REQ가 코드·테스트·운영 증거로 확인됐고 차단·중요 지적이 없다.

## 확인하지 못한 부분

- verifier는 테스트·운영 명령을 실행하지 않았고 verify 로그와 메인이 기록한 prod-check.md를 읽어 판정했다. 운영 원출력은 SSM 명령 ID로만 추적 가능하다.
