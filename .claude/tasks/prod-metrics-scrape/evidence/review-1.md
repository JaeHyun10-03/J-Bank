# 검토 결과

## 검토 범위

결과 리뷰 1/3회차(배포 전 범위). 작업 시작 c88dde0 → 41fdf45, 커밋 7개(커밋 계획 1~7). verify-001 통과 증거 기준.
REQ-09·AC-06·OPS-04·OPS-07·E2E-02·SEC-05(운영 로그)·SEC-07(외부 curl)은 PR 병합·운영 배포 뒤에만 확인할 수 있어 이 회차 범위 밖이다.

## 독립 검증 결과

- 호출: verifier, agentId ae6db37349a059d0f, 2026-10-01(review-begin 1/3 직후).
- 대상 snapshot: f11cad36a23fd22c97e71fb05254fec4be2661af65651e4a1337292bcd197d32
- 응답 원문(인용 표시 `> `만 붙이고 내용 그대로):

> # 결과 리뷰 1/3회차: prod-metrics-scrape
>
> ## 검증 대상
> - 모드: 결과 리뷰. 작업 ID: prod-metrics-scrape.
> - snapshot: `f11cad36a23fd22c97e71fb05254fec4be2661af65651e4a1337292bcd197d32`. evidence/checks.json 기준 git head `41fdf45`, dirty=false, unchanged_during_checks=true.
> - 변경 범위: c88dde0 → 41fdf45, 커밋 7개, 파일 18개(evidence/changes.txt).
>
> ## 읽은 자료
> - 문서: docs/testing-policy.md, task.md, plan-review.md(3회차 권고 부분), progress.md
> - 검사 설정과 증거: .claude/checks.json, evidence/checks.json, verify-001/check-2·4·5·6.log, logs/compose-config.txt
> - 백엔드 코드: PrometheusScrapeRequestMatcher.java, SecurityConfig.java, GlobalExceptionHandler.java, JbankApiApplication.java, application.yml(+ prod/dev/local yml의 management 설정 유무)
> - 백엔드 테스트: PrometheusScrapeRequestMatcherTest, ActuatorManagementPortIntegrationTest, ActuatorSamePortIntegrationTest, GlobalExceptionHandlerTest, FullFlowIntegrationTest(추가 부분), SecurityConfigTest(추가 부분)
> - 프론트: route.ts, route.test.ts
> - 인프라: docker-compose.prod.yml, prometheus.prod.yml, Caddyfile, host/boot.sh(healthy 대기 방식)
> - 문서: ADR 0013, 06_인프라아키텍처 2절·3.4·3.5절, perf/README "운영과 다른 점"
> - 실행 결과 XML: apps/jbank-api/build/test-results/test/*.xml
>   - 대상 스위트의 결과를 직접 확인했습니다.
>   - 전체 스위트에서 failures·errors·skipped가 0이 아닌 파일은 없습니다.
>   - 모든 XML의 timestamp가 verify 실행 구간(08:05~08:08Z) 안입니다.
> - 저장소 전체에서 `actuator/health`·`actuator/prometheus`·`MANAGEMENT_SERVER_PORT`를 grep했습니다. 남은 호출처가 로컬(8080 동일 포트) 전용인지 확인했습니다.
>
> ## 확인하지 못한 범위
> - 저는 테스트를 실행하지 않았습니다. 통과 판정은 verify 로그와 결과 XML을 읽어서 내렸습니다. snapshot 해시 값은 직접 재계산하지 않았습니다.
> - 배포 후 대기 항목: REQ-09, AC-06, OPS-04, OPS-07, E2E-02, SEC-05(운영 로그), SEC-07(외부 curl 부분). logs/prod-check.md가 아직 없습니다.
> - 관리 포트 하위 컨텍스트에서 매핑 없는 경로의 실제 응답은 실행 증거가 없습니다(개선-3).
> - 개발일지(커밋 8)와 작업 기록(커밋 9)은 아직 작성 전입니다.
>
> ## REQ별 대조
>
> | REQ | 구현 위치 | 테스트 | 실행 증거 | 판정 |
> | --- | --- | --- | --- | --- |
> | REQ-01 | PrometheusScrapeRequestMatcher.java:28-37, SecurityConfig.java:62-63, compose MANAGEMENT_SERVER_PORT | ActuatorManagementPortIntegrationTest:67-73: 관리 포트 200, `jvm_`·`hikaricp_` 포함, 두 포트가 다름을 단언 | XML 5/5 통과 | 충족 |
> | REQ-02 | 같은 위치. 본 포트에서 actuator 제거 | :81-89: 미인증 401, 로그인 404 COMMON_004, `jvm_` 없음 | 통과 | 충족 |
> | REQ-03 | Matcher:33-34(`managementPort != null && != serverPort`) | 단위: 같은 포트(별칭 상태), -1(값 없음). 통합: SamePort:64-67 미인증 401 | 통과 | 충족(아래 판단 1) |
> | REQ-04 | Matcher:18-19, 36(GET `/actuator/prometheus` 정확 일치) | 단위: info·하위 경로·POST 불허. 통합: 관리 포트 info 401 | 통과 | 충족 |
> | REQ-05 | application.yml:53-60(additional-path), PUBLIC_PATHS:37-38 | SamePort:52-61(200, 본문 "UP"), MgmtPort:100-108(200), SecurityConfigTest:45-52 | 통과 | 충족 |
> | REQ-06 | JbankApiApplication.java:10 exclude | FullFlowIntegrationTest:81-83 빈 부재, 전체 흐름 테스트 | 통과. FullFlow XML에 "Started"가 있어 자체 컨텍스트를 기동했고, 생성 비밀번호 문구는 0건입니다(직접 grep). | 배포 전 범위 충족. 운영 로그는 배포 후 확인 |
> | REQ-07 | route.ts:14 `/readyz` | route.test.ts:7-35(200·502·503 online, URL `/readyz$`, 연결 실패·3초 초과 offline) | check-4: 31/31 통과 | 충족 |
> | REQ-08 | docker-compose.prod.yml:38, 45, prometheus.prod.yml:9, Caddyfile:4 | 설정 렌더링 | compose-config.txt: ports 없음, 9095 published 없음, Caddy에 9095 없음, 대상 api:9095 | 충족 |
> | REQ-09 | 배포 후 | 배포 전 근거: MgmtPort:102-107의 본 포트 미인증 `/actuator/health` 404 COMMON_004 | 없음 | 배포 후 대기 |
> | REQ-10 | ADR 0013, 06 문서 2절·3.4·3.5, perf README:387-388 | 리뷰 | 아래 기준 ID 표 | 충족 |
> | REQ-11 | GlobalExceptionHandler.java:42-48 | GlobalExceptionHandlerTest:52-69(NoResourceFound·NoHandlerFound 모두), MgmtPort:88, 107(실제 요청 경로) | 통과 | 충족 |
>
> ## 메인이 알린 차이에 대한 판단
>
> **1. 같은 포트 별칭 조건 추가: 적절합니다.**
> - Boot의 `SameManagementContextConfiguration`은 `local.management.port`를 조회할 때 `local.server.port` 값을 돌려주는 property source를 추가합니다. -1(DISABLED)이면 이 별칭이 없습니다.
> - 그래서 "관리 포트 != 본 포트" 조건은 꼭 필요합니다. 이 조건이 없으면 기본 구성(로컬·테스트)과 운영에서 설정이 빠진 경우 모두 8080 지표가 미인증으로 열립니다.
> - 그 결함은 SamePort 통합 테스트가 실제로 잡았고, 수정 후 401로 통과했습니다.
> - REQ-03·AC-02의 기대 동작과 사례 목록(미설정·같은 값·-1·포트 불일치·경로 불일치)은 바뀌지 않았습니다. 바뀐 것은 task.md:29-32 재점검 항목의 "값이 없다"는 전제와 단위 테스트를 흉내 내는 방식뿐입니다. 테스트 기준 변경이 아니라서 계획을 다시 검증받을 필요는 없다고 봅니다.
> - ADR 0013:21과 매처 javadoc에는 이미 바른 내용이 적혀 있습니다. 남은 일은 기록 정정뿐입니다(개선-1).
>
> **2. `@AutoConfigureObservability`: 적절합니다.**
> - 운영은 지표 내보내기가 기본으로 켜져 있어, 테스트를 운영 조건에 맞춘 것입니다.
> - build.gradle.kts에 tracing 의존성이 없어서 tracing까지 켜도 부작용이 없습니다.
>
> **3. 계획 리뷰 3회차 권고 반영: 확인했습니다.**
> - 본 포트 미인증 `/actuator/health` 404 COMMON_004: MgmtPort:102-107.
> - Host 위조: 소켓으로 직접 보내고, 같은 Host 헤더로 관리 포트에 보낸 요청 200을 대조합니다(:93-98, :120-133). 테스트가 공허하게 통과하지 않습니다.
> - NoHandlerFoundException 404: 단위 테스트로 확인했습니다.
>
> **4. REQ-06 판정 근거: 적절합니다.**
> - 빈 부재 단언은 전체 컨텍스트에서 실행되므로 판정 근거로 유효합니다.
> - 결과 XML을 전부 grep하면 생성 비밀번호 문구가 9건 나옵니다. 모두 `@WebMvcTest` 슬라이스입니다(예: AuthControllerTest:31). 슬라이스는 `@SpringBootApplication`의 exclude가 적용되지 않는 테스트 전용 구성이라 운영에는 영향이 없습니다(개선-2).
>
> ## 기준 ID별 판정
> - **SEC-01: 충족.**
>   - 관리 포트 prometheus 미인증 200.
>   - 본 포트는 미인증 401, 로그인 404.
>   - Host 위조 401. 관리 포트 info 401.
>   - 미분리·같은 값·-1은 401 또는 불허(통합 + 단위 테스트).
> - **SEC-02: 충족.** 관리 포트 info 401이고 기존 컨트롤러 테스트가 전부 통과했습니다.
> - **SEC-05: 배포 전 범위 충족.** 근거는 빈 부재 테스트와 FullFlow 로그 0건입니다. 운영 로그 확인은 배포 후 대기입니다. 문서와 증거에서 비밀값은 보지 못했습니다(compose-config.txt는 임시값 사용을 명시하고 값은 출력하지 않음).
> - **SEC-07: 구현 범위 충족.** 호스트 매핑 없음, Caddy는 8080만 프록시, 렌더링 결과로 확인했습니다. 외부 curl은 배포 후 대기입니다.
> - **SEC-03·04·06·08: 해당 없음 판정이 타당합니다.** 입력 처리·쿠키/CSRF/CORS·의존성·요청 제한 변경이 없습니다.
> - **BE-02: 충족.** REQ-01~05·11의 응답 코드 계약과 테스트가 일치합니다.
> - **BE-03: 충족.** Testcontainers 기반 SpringBootTest 통합 테스트가 통과했습니다.
> - **BE-01·04~08: 해당 없음 판정이 타당합니다.**
> - **FE-01: 충족.** lint·tsc·build 종료 코드 0. 경고는 기존 `<img>` 경고뿐이고 변경 파일과 무관합니다.
> - **FE-03: 충족.**
> - **FE-02·04~07: 해당 없음이 타당합니다.**
> - **OPS-03: 충족.**
> - **OPS-05: 충족.** 근거는 다음과 같습니다.
>   - 새 compose + 옛 이미지: 9095 prometheus는 401(옛 이미지에는 매처가 없음). healthcheck는 `/actuator/health/**` 공개라 healthy.
>   - 옛 compose + 새 이미지: 같은 포트 별칭 때문에 매처가 꺼져 401(SamePort 테스트로 확인).
>   - 옛 프론트 → 새 api: 404 응답도 응답이라 online 유지.
>   - 새 프론트 → 옛 api: `/readyz`가 401이어도 online 유지.
> - **OPS-04·07, E2E-02: 배포 후 대기.**
> - **E2E-06: 충족.** FullFlowIntegrationTest 2/2 통과.
> - **E2E-01·03·04·05, OPS-01·02·06·08: 해당 없음이 타당합니다.**
> - **성능: 해당 없음 사유가 타당합니다.** 요청 처리 경로 변경이 없고, perf 대상에서 같은 구성으로 이미 측정했습니다.
> - **checks.json 명령:** 하네스 unittest, 프론트 lint·tsc·jest·build, 백엔드 `gradlew test spotlessCheck`로 모두 실제 앱 검사입니다. skipped_checks는 비어 있습니다. compileTestJava가 UP-TO-DATE였지만 `:test`는 실행됐고, 결과 XML 시각이 verify 구간과 일치합니다.
>
> ## 지적 사항
> 차단: 없음. 중요: 없음.
>
> **[개선-1] task.md 재점검 항목의 틀린 전제를 기록으로 정정하기**
> - 위치: task.md:29, task.md:32. "같은 값이면 `local.management.port`가 없다"고 적혀 있습니다.
> - 실제: Boot가 본 포트 값의 별칭으로 채웁니다.
> - 영향: 동작과 테스트 기준에는 영향이 없습니다. 나중에 읽는 사람이 오해할 수 있습니다.
> - 수정: task.md는 그대로 두고, review.md와 개발일지에 "전제 오류 → SamePort 테스트가 결함을 검출 → 매처 조건 추가"를 남깁니다. progress.md:22에는 이미 있습니다.
>
> **[개선-2] 슬라이스 테스트의 생성 비밀번호 로그**
> - 위치: 결과 XML 9개(AccountControllerTest, AuthControllerTest 등 `@WebMvcTest`).
> - 영향: 운영 영향은 없습니다. 다만 SEC-05 증거를 "테스트 출력 전체 0건"으로 쓰면 사실과 다릅니다.
> - 수정: 증거 문구를 "전체 컨텍스트 통합 테스트(FullFlow 등) 0건 + 빈 부재"로 한정합니다. 운영 확인은 AC-06에서 합니다.
>
> **[개선-3] 관리 포트의 매핑 없는 경로 응답은 실행 증거가 없음**
> - 현재 NoHandlerFoundException 처리는 단위 테스트로만 확인됐습니다.
> - 이 포트는 외부에서 닿지 않아 보안·제품 영향이 없습니다.
> - 필요하면 MgmtPort 테스트에 로그인 쿠키로 관리 포트의 없는 경로 404 단언을 한 줄 추가합니다. 선택 사항입니다.
>
> **[개선-4] perf/README.md:388 문구**
> - "본 포트에는 `/readyz`·`/livez`만"은 업무 API까지 없는 것처럼 읽힐 수 있습니다.
> - "본 포트의 상태 확인 경로는 `/readyz`·`/livez`"로 바꾸면 명확합니다. 선택 사항입니다.
>
> ## 이전 지적 처리 상태
> - 계획 리뷰 1회차 중요-1~3, 2회차 중요-A와 권고 1~7: 구현에 반영된 것을 확인했습니다. 해결.
> - 3회차 권고 1~4: 테스트 기대값, health 404 단언, Host 위조 대조, NoHandlerFound 처리가 모두 구현됐습니다. 해결.
> - 3회차 권고 5(원문 보존): plan-review.md에 3회차 원문이 보존돼 있습니다. 해결.
>
> ## 남은 일
> - 배포 후 확인을 logs/prod-check.md에 기록: REQ-09, AC-06, OPS-04, OPS-07, E2E-02, SEC-05·SEC-07 외부 확인.
> - 커밋 8(개발일지), 커밋 9(작업 기록).
> - 둘 다 끝나기 전에는 작업 완료가 아닙니다.
>
> 최종 판정: 통과 권고(배포 전 범위)

## 요구사항별 검증

| 요구사항 ID | 구현 위치 | 테스트·실행 증거 | 판정 |
| --- | --- | --- | --- |
| REQ-01 | PrometheusScrapeRequestMatcher.java, SecurityConfig.java, docker-compose.prod.yml `MANAGEMENT_SERVER_PORT` | ActuatorManagementPortIntegrationTest(관리 포트 200·`jvm_`·`hikaricp_`), verify-001 | 통과 |
| REQ-02 | 관리 포트 분리로 본 포트 actuator 제거, SecurityConfig | ActuatorManagementPortIntegrationTest(미인증 401, 로그인 404 COMMON_004·지표 없음) | 통과 |
| REQ-03 | PrometheusScrapeRequestMatcher(관리 포트 != 본 포트 조건) | PrometheusScrapeRequestMatcherTest(같은 포트·-1), ActuatorSamePortIntegrationTest(미인증 401) | 통과 |
| REQ-04 | PrometheusScrapeRequestMatcher(GET `/actuator/prometheus`만) | 단위(info·하위 경로·POST 불허), 통합(관리 포트 info 401) | 통과 |
| REQ-05 | application.yml additional-path, SecurityConfig PUBLIC_PATHS | ActuatorSamePort·ManagementPort 통합(readyz·livez 200), SecurityConfigTest | 통과 |
| REQ-06 | JbankApiApplication.java exclude | FullFlowIntegrationTest 빈 부재·전체 흐름 통과. 운영 로그는 배포 후 | 배포 전 범위 통과, 운영 로그 대기 |
| REQ-07 | apps/frontend/app/api/server-status/route.ts | route.test.ts(`/readyz` 호출, online/offline 판정), verify-001 check-4 | 통과 |
| REQ-08 | docker-compose.prod.yml, prometheus.prod.yml | logs/compose-config.txt | 통과 |
| REQ-09 | 배포 후 | 배포 전 근거: 본 포트 미인증 `/actuator/health` 404 COMMON_004(통합). 운영 증거 없음 | 배포 후 대기 |
| REQ-10 | ADR 0013, docs/06 2·3.4·3.5절, perf/README | verifier 대조 | 통과 |
| REQ-11 | GlobalExceptionHandler.java | GlobalExceptionHandlerTest, ActuatorManagementPortIntegrationTest(로그인 404·health 404 COMMON_004) | 통과 |

## 지적별 처리

- 차단·중요: 없음.
- 개선-1(task.md 재점검 전제 오류): task.md는 계획 지문 유지를 위해 두고, 이 문서·progress.md·개발일지에 "전제 오류 → SamePort 통합 테스트가 본 포트 미인증 200 검출 → 매처에 관리 포트 != 본 포트 조건 추가"를 기록한다.
- 개선-2(슬라이스 테스트 생성 비밀번호 로그): SEC-05 배포 전 증거를 "전체 컨텍스트 통합 테스트 0건 + UserDetailsService 빈 부재"로 한정한다. `@WebMvcTest` 슬라이스는 운영 구성이 아니다.
- 개선-3(관리 포트 없는 경로 실행 증거): 이 포트는 외부에서 닿지 않아 추가하지 않는다. NoHandlerFoundException 처리는 단위 테스트로 확인.
- 개선-4(perf/README 문구): 다음 수정에서 "본 포트의 상태 확인 경로는 `/readyz`·`/livez`"로 고친다.

## 완료 기준별 근거

- AC-01·02·03·04·05·07·08: 위 표와 verify-001, compose-config.txt로 충족.
- AC-06: 배포 후 logs/prod-check.md 대기.

## 성능 테스트 확인

불필요(task.md 성능 테스트 절). 요청 처리 경로 변경 없음, perf 대상이 같은 관리 포트 구성으로 이미 측정됨. verifier도 타당하다고 판정.

## 발견한 문제

- 구현 중 발견: 관리 포트 미설정(같은 포트)일 때 Boot가 `local.management.port`를 본 포트 값으로 채워 초안 매처가 본 포트 prometheus를 미인증으로 열었다. 커밋 fbdd395 전에 테스트로 잡아 고쳤다.

## 판정과 이유

review fail(1/3). 코드 범위는 차단·중요 지적 없이 통과 권고를 받았지만 REQ-09 운영 증거가 없어 통과로 등록할 수 없다. PR 병합·배포 후 logs/prod-check.md를 남기고 verify·결과 리뷰 2회차에서 통과를 등록한다.

## 확인하지 못한 부분

- 운영 배포 뒤 항목(REQ-09, AC-06, OPS-04, OPS-07, E2E-02, SEC-05 운영 로그, SEC-07 외부 curl).
- verifier는 테스트를 실행하지 않았고 verify 로그·결과 XML을 읽어 판정했다.
