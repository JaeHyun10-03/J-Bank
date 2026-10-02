# 검토 결과

## 검토 범위

결과 리뷰 1/3회차(배포 전 범위). 작업 시작 dc2179e → 15900e7, 커밋 4개(커밋 계획 1~4). verify-001 통과 증거 기준.
REQ-09·AC-05(운영 배포 뒤)와 OPS-02의 PR `openapi-drift` 결과는 PR·병합 뒤에만 확인할 수 있어 이 회차 범위 밖이다.

## 독립 검증 결과

- 호출: verifier, agentId a106ee499a9caa831, 2026-10-02(review-begin 1/3 직후).
- 대상 snapshot: 2a8a827686889069ce64d3e8eb8bec45645b7f8cccae4ce82cdc9ef6cc206227
- 응답 원문(인용 표시 `> `만 붙이고 내용 그대로):

> ## 결과 리뷰 1/3회차: openapi-description
>
> 결론: 배포 전 범위에서는 통과를 권고합니다. 차단·중요 지적은 없습니다. 운영 배포 뒤 확인(REQ-09·AC-05)과 PR CI 확인(OPS-02)은 아직 증거가 없어 대기로 남깁니다.
>
> ### 검증 대상
> - snapshot `2a8a827686889069ce64d3e8eb8bec45645b7f8cccae4ce82cdc9ef6cc206227`
>   - 출처: `.claude/tasks/openapi-description/evidence/checks.json`
>   - git head 15900e7, dirty false, passed true, skipped_checks 없음
> - 작업 시작 커밋 dc2179e에서 HEAD까지의 커밋 4개: 8071a47, 894eeba, 406a4b9, 15900e7
>
> ### 읽은 자료
> - 정책·작업 문서
>   - `docs/testing-policy.md`
>   - `docs/testing/{backend,security,e2e,devops}.md`에서 BE-02, SEC-04, E2E-02, E2E-06, OPS-02 행
>   - task.md, plan-review.md(1~3회차), progress.md
> - 증거
>   - evidence/changes.txt, evidence/checks.json, verify-001/check-5.log
>   - logs/contract-check.txt, logs/local-swagger.md
>   - `.claude/checks.json`
> - 코드
>   - `OpenApiConfig.java`, `SecurityConfig.java`, `CsrfDoubleSubmitFilter.java`, `JwtAuthenticationFilter.java`
>   - `DepositService.java`(멱등 처리 부분)
>   - `contracts/openapi/openapi.yaml`(1~60행, securitySchemes)
> - 테스트
>   - `OpenApiConfigTest.java`, `AuthControllerTest.java`
>   - `apps/jbank-api/build/test-results/test/` 아래 결과 XML 4개: OpenApiConfigTest, AuthControllerTest, CsrfDoubleSubmitFilterTest, FullFlowIntegrationTest. 실패·오류가 있는 XML은 전체 결과에서 0건이었습니다.
> - grep 확인
>   - `SecurityRequirement` 개별 재정의: OpenApiConfig 한 곳뿐입니다. 작업별로 security를 덮어쓰는 곳이 없어 Authorize 헤더가 모든 작업에 붙습니다.
>   - Idempotency 구현 위치
>
> ### 확인하지 못한 범위
> - **명령 실행:** git·테스트·앱 기동·curl·브라우저를 하나도 실행하지 않았습니다. 판단은 읽기 전용으로 했습니다.
> - **커밋별 diff:** `git show`로 각 커밋을 보지 못했습니다.
>   - 커밋별 파일 구성과 중간 커밋(8071a47, 894eeba, 406a4b9)의 테스트 컴파일·통과는 직접 확인하지 못했습니다.
>   - 중간 커밋에서 확인된 것은 local bootRun 기동(main 코드 컴파일)과 스냅샷 diff뿐입니다. 이것도 메인이 기록한 내용입니다.
> - **SecurityConfig 변경 전 원문:** git으로 보지 못했습니다. 아래 근거로 간접 판단했습니다.
>   - 계획 리뷰가 인용한 이전 줄 번호
>   - changes.txt의 변경 통계(7줄)
> - **현재 파일과 snapshot 해시:** 해시를 직접 계산하지 못해 일치 여부를 확인하지 않았습니다. checks.json의 dirty false와 결과 XML 시각이 verify 실행 시간(06:29~06:31Z) 안이고, pid 88050이 check-5.log와 같다는 점으로 판단했습니다.
> - **로컬 Swagger 실행:** 브라우저 결과는 메인 기록(local-swagger.md)으로만 확인했습니다.
>
> ### REQ별 대조
>
> | REQ | 구현 | 테스트·증거 | 판정 |
> | --- | --- | --- | --- |
> | REQ-01 | OpenApiConfig.java:38 `v1`, 스냅샷 :50 | OpenApiConfigTest `버전은_…v1`, `…남지_않는다`(W1·v0 없음). XML 7/7 통과 | 충족 |
> | REQ-02 | :50-63 쿠키 3종, X-CSRF-TOKEN 규칙, 예외 2개, 401·403 코드. 오래된 문구 삭제(contract-check.txt c1 diff) | `설명은_현재_쿠키_인증과_CSRF_규칙…`, 금지 문구 테스트 | 충족. 문구를 코드와 대조한 결과는 아래 표 |
> | REQ-03 | :80-85 로그인 → `data.csrfToken` → Authorize, 재발급·새로고침 뒤 다시 입력 | `설명은_Swagger에서_직접_호출하는…` | 충족 |
> | REQ-04 | :25-34 apiKey·header·`X-CSRF-TOKEN`, 전역 security. 스냅샷 :53-54, :1454-1459 | `Authorize로_X_CSRF_TOKEN…`. 서버 필터는 바뀌지 않음(아래 SEC-04) | 충족 |
> | REQ-05 | :43-48 응답 봉투, :65-66 Idempotency-Key, :68-70 금액, :72-73 오프셋(Z, +09:00 예시, 단정하지 않음), :75-78 페이지 응답 | `설명은_공통_응답_규칙을_유지한다` | 충족 |
> | REQ-06 | :23 `Server().url("/")`, 스냅샷 :51-52. `http://` 서버 주소 0건 | `요청_주소는_…상대_경로_하나다`(containsExactly) | 충족 |
> | REQ-07 | 스냅샷 재생성. api.ts는 changes.txt 변경 목록에 없음 | contract-check.txt: 커밋 1·2·4는 갱신 뒤 diff 없음, api.ts 재생성 변화 없음. 커밋 3은 간접 판단 | 로컬 증거 충족. PR의 `openapi-drift` 결과는 PR 대기 |
> | REQ-08 | (설정 결과) | local-swagger.md: 201 `eddRequired:false` → 로그인 200 → Authorize 없이 403 `COMMON_007` → Authorize 뒤 201. 요청 URL은 같은 출처(18080). 메인 기록 기준 | 충족. 포트 18080은 같은 출처 조건이 유지되어 타당 |
> | REQ-10 | SecurityConfig.java:82 허용 출처 2개. 메서드·헤더·자격 증명(:83-85)은 그대로 | AuthControllerTest(아래 SEC-04). XML의 `[1] origin=http://localhost:3000`, `[2] origin=https://api.j-bank.site`, 거절 사례 모두 통과 | 충족 |
> | REQ-09 | - | logs/prod-check.md 없음 | 배포 후 대기 |
>
> ### 설명 문구와 실제 코드 대조
>
> | 설명 문구 | 대조한 코드 | 결과 |
> | --- | --- | --- |
> | `Authorization` 헤더는 쓰지 않음 | JwtAuthenticationFilter는 `access_token` 쿠키만 읽음 | 맞음 |
> | POST·PATCH·DELETE에 헤더 필요, 예외는 로그인·고객 등록 | CsrfDoubleSubmitFilter :24, :27 (SAFE_METHODS는 GET·HEAD·OPTIONS, EXEMPT_PATHS는 2개), 불일치 시 :67에서 COMMON_007. 재발급은 공개 경로지만 CSRF 검사 대상 | 맞음 |
> | 같은 키 재요청이면 처음 결과를 돌려줌 | DepositService :43-46 기존 거래 반환, 동시 요청 시 :64-70 재조회. 이체·출금도 같은 구조(grep) | 맞음 |
>
> ### 기준 ID 판정
>
> **BE-02: 충족.**
> - AC-01 검사 문자열이 task.md 125행 목록을 모두 덮습니다.
> - verify-001 check-5에서 exit 0, OpenApiConfigTest 7건이 모두 통과했습니다.
>
> **SEC-04: 배포 전 범위 충족.**
> - 허용 사례 2개: 200 상태와 함께 `Access-Control-Allow-Origin`이 요청 Origin과 같고, `Access-Control-Allow-Credentials`가 true인지 확인합니다. 계획 리뷰의 P3-1을 반영했습니다.
> - 거절 사례(`https://evil.example`): 403과 본문 `Invalid CORS request`를 확인합니다. CSRF 403과 구분됩니다.
> - MockMvc 기본 요청이 `http://localhost:80`이라 세 출처 모두 교차 출처로 판정되어 실제로 허용 목록 검사를 거칩니다. 테스트 71행 주석과 맞습니다.
> - 기존 회귀 테스트도 통과했습니다: CsrfDoubleSubmitFilterTest 3건, AuthControllerTest의 기존 4건.
> - 운영 curl 확인은 배포 후 대기입니다.
>
> **E2E-02: 로컬 부분 충족, 운영은 배포 후 대기.**
> - 로컬은 실제 앱과 로컬 postgres·redis로 확인했습니다(메인 기록).
>
> **E2E-06: 충족.** FullFlowIntegrationTest 2건이 통과했고, 결과 시각이 verify 실행 시간 안입니다.
>
> **OPS-02: 일부 충족, PR 대기.**
> - 로컬 드리프트 diff 근거는 있습니다.
> - PR의 backend-ci `openapi-drift` 결과는 아직 없습니다.
>
> **`.claude/checks.json` 명령:** 실제 앱 검사입니다(gradle test·spotlessCheck, 프론트 lint·tsc·test·build). 성공만 반환하는 명령은 없습니다.
>
> **적용 제외 항목:** BE-01·03~08, SEC-01~03·05~08, FE, OPS-01·03~08, E2E-01·03~05, 성능. 실제 변경이 OpenApiConfig, CORS 출처 값, 테스트, 스냅샷뿐이라 제외는 타당합니다.
> - 프론트 타입 `api.ts`가 바뀌지 않았다는 조건도 changes.txt로 확인했습니다.
>
> ### P3-2 확인: SecurityConfig 변경 범위
> 현재 파일 기준으로 변경은 :77-79 주석과 :82 허용 출처 값뿐인 것으로 판단합니다.
> - 주석에 적은 내용: 로컬은 개발용, 운영 Swagger용으로 출처 추가, 스킴 오인이 남음, 도메인이 바뀌면 이 값도 갱신.
> - 판단 근거
>   - 계획 리뷰 2회차가 인용한 필터 체인 위치(:70-73)가 지금도 같습니다.
>   - 계획 리뷰 3회차가 인용한 메서드(82행)·헤더(83행) 줄이 각각 한 줄씩 밀려 :83, :84에 같은 값으로 있습니다. 주석이 한 줄 늘어난 것과 맞습니다.
>   - changes.txt 통계가 7줄 변경입니다.
> - 메서드·헤더·자격 증명·필터·인가 규칙 변경은 없습니다.
> - 다만 git diff를 직접 보지는 못했습니다(확인하지 못한 범위 참고).
>
> ### 비밀값
> - local-swagger.md: loginId만 있고, 비밀번호·토큰은 기록하지 않았습니다. curl 헤더 값도 `…`로 가렸습니다.
> - contract-check.txt: 비밀값이 없습니다.
> - AuthControllerTest의 `jbank.jwt.secret`은 기존부터 있던 테스트용 값입니다.
>
> ### 지적 사항
>
> **[권고] R1-1. 중간 커밋의 빌드·테스트 가능성 근거가 부족함**
> - **근거**
>   - task.md 96행과 commits.md는 각 커밋이 빌드·테스트 가능한 상태이기를 요구합니다.
>   - 테스트 실행 증거는 최종 HEAD의 verify-001뿐입니다.
>   - 중간 커밋은 bootRun(main 컴파일)과 스냅샷 diff만 기록돼 있습니다.
> - **예상 영향**
>   - 예를 들어 `새로고침`·`Authorize`를 검사하는 테스트가 커밋 1에 들어갔다면, 그 커밋에서는 테스트가 실패합니다.
>   - 최종 상태와 CI(PR 마지막 커밋만 검사)에는 영향이 없고, bisect할 때만 문제가 됩니다.
> - **필요한 조치:** 아래 중 하나면 충분합니다.
>   - `git show --stat` 8071a47 894eeba 406a4b9 결과와 각 커밋의 OpenApiConfigTest 내용을 확인해 progress.md나 review.md에 기록합니다.
>   - 가능하면 중간 커밋에서 `test --tests '*OpenApiConfigTest' --tests '*AuthControllerTest'`를 실행한 기록을 남깁니다.
> - 커밋 메시지와 범위 자체는 계획 1~4와 맞고, fix와 feat가 분리돼 atomic입니다.
>
> **[권고] R1-2. 설명의 401 문구가 상태 변경 요청에서는 다르게 동작할 수 있음**
> - **근거**
>   - 설명 2번(OpenApiConfig.java:57)은 "인증 쿠키가 없거나 만료되면 401"이라고 씁니다.
>   - 그런데 CSRF 필터가 인가보다 먼저 동작합니다(SecurityConfig.java:73, CsrfDoubleSubmitFilter.java:47-51).
>   - 그래서 인증 쿠키와 `XSRF-TOKEN` 쿠키가 모두 없는 POST에는 403 `COMMON_007`이 나옵니다.
> - **예상 영향:** 문구가 틀린 것은 아니고(GET과, CSRF를 통과한 요청은 401), 클라이언트가 401만 기대하면 헷갈릴 수 있는 정도입니다.
> - **필요한 조치:** 선택 사항입니다. 고치면 계약 스냅샷 재생성과 재검증이 필요하므로 후속 작업으로 넘겨도 됩니다.
>
> **[대기, 지적 아님]**
> - REQ-09·AC-05(logs/prod-check.md)는 배포 후 대기입니다.
> - OPS-02와 AC-03의 PR `openapi-drift` 성공은 PR 대기입니다.
> - 이 둘이 없는 상태로는 complete 조건을 채우지 못합니다. task.md 93~94행의 후속 PR 계획대로 처리해야 합니다.
>
> **[범위 밖]** 잘못된 JSON 본문을 보내면 500 `COMMON_006`이 나오는 문제는 후속 작업으로 분리한 것이 타당합니다.
> - 이번 변경으로 생긴 문제가 아닙니다.
> - local-swagger.md에 재현 경위가 남아 있습니다.
>
> ### 이전 지적 처리
>
> | 지적 | 결과 | 근거 |
> | --- | --- | --- |
> | P3-1(계획 3회차) | 해결 | AuthControllerTest :89-91(허용 사례 헤더 확인), :102-103(거절 사례 403과 본문) |
> | P3-2(계획 3회차) | 해결 | 주석이 사실과 맞게 수정됨. 변경이 허용 출처 값과 관련 주석뿐이라는 점은 간접 근거로 확인 |
> | P3-3(계획 3회차) | 미반영 | REQ 표기 순서만의 문제라 그대로 둔 것이 타당 |
> | 계획 1·2회차 F-1~6, P2-1~3 | 유지 | 구현에 반영됨: 스냅샷 커밋, servers `/`, CORS, 커밋 분리, 새로고침 안내, 오프셋 예시 |
>
> 최종 판정: 통과 권고(배포 전 범위)

## 요구사항별 검증

| 요구사항 ID | 구현 위치 | 테스트·실행 증거 | 판정 |
| --- | --- | --- | --- |
| REQ-01 | OpenApiConfig 버전 | OpenApiConfigTest, logs/contract-check.txt | 통과 |
| REQ-02 | OpenApiConfig 설명 2·3번 | OpenApiConfigTest, 코드 대조(verifier) | 통과 |
| REQ-03 | OpenApiConfig 설명 8번 | OpenApiConfigTest, logs/local-swagger.md | 통과 |
| REQ-04 | OpenApiConfig 보안 스킴·전역 security | OpenApiConfigTest, logs/local-swagger.md(헤더 자동 첨부) | 통과 |
| REQ-05 | OpenApiConfig 설명 1·4~7번 | OpenApiConfigTest | 통과 |
| REQ-06 | OpenApiConfig servers | OpenApiConfigTest, 스냅샷 `servers: /` | 통과 |
| REQ-07 | contracts/openapi/openapi.yaml | logs/contract-check.txt(로컬 diff 없음, api.ts 불변). PR `openapi-drift` 대기 | PR 대기 |
| REQ-08 | 설정 결과 | logs/local-swagger.md(403 COMMON_007 → Authorize 뒤 201) | 통과 |
| REQ-09 | 배포 후 | 증거 없음 | 배포 후 대기 |
| REQ-10 | SecurityConfig CORS 허용 출처 | AuthControllerTest CORS 3사례 | 통과 |

## 지적별 처리

- 차단·중요: 없음.
- R1-1: 임시 worktree에서 중간 커밋 3개를 각각 체크아웃해 `gradlew test --tests '*OpenApiConfigTest' --tests '*AuthControllerTest' spotlessCheck` 실행, 모두 exit 0.
  - 8071a47: 두 테스트 클래스 4건·4건 통과
  - 894eeba: 4건·5건 통과(OpenApiConfigTest에 servers 테스트 추가)
  - 406a4b9: 5건·7건 통과(AuthControllerTest에 CORS 3사례 추가)
  - 최종 15900e7은 verify-001.
- R1-2: 선택 사항. "인증 쿠키가 없으면 401"은 GET과 CSRF를 통과한 요청 기준이고, 쿠키가 모두 없는 상태 변경 요청은 CSRF 필터가 먼저 403을 낸다. 문구를 고치면 스냅샷 재생성·재검증이 필요해 이번에는 고치지 않고 남은 한계로 기록한다.
- 범위 밖: 잘못된 JSON 본문 500은 후속 작업 칩으로 분리.

## 완료 기준별 근거

- AC-01: OpenApiConfigTest(verify-001)
- AC-02: AuthControllerTest CORS·기존 테스트, FullFlowIntegrationTest, SecurityConfig 변경은 허용 출처 값과 관련 주석뿐
- AC-03: logs/contract-check.txt. PR `openapi-drift` 대기
- AC-04: logs/local-swagger.md
- AC-05: 배포 후 대기

## 성능 테스트 확인

불필요(task.md 성능 테스트 절). 요청 처리 경로 변경 없음.

## 발견한 문제

- 잘못된 JSON 본문을 보내면 500 `COMMON_006_INTERNAL_ERROR`(기존 결함, 로그도 없음). 후속 작업으로 분리.

## 판정과 이유

review fail(1/3). 배포 전 범위는 차단·중요 지적 없이 통과 권고를 받았지만 REQ-07의 PR CI 결과와 REQ-09 운영 증거가 없어 통과로 등록할 수 없다. PR CI·병합·배포 뒤 결과 리뷰 2회차에서 통과를 등록한다.

## 확인하지 못한 부분

- 운영 배포 뒤 확인(REQ-09·AC-05), PR의 backend-ci `openapi-drift`.
- verifier는 명령을 실행하지 않았고 기록을 읽어 판정했다.
