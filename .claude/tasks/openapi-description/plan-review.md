# 계획 리뷰

## 1회차

회차 계획 지문: 92ddb10019612446fccf17ccf9ae5e98b3c929e5e88a4b979497e404b003e589 (2026-10-02T03:19:53Z 호출, verifier agentId ad0fcc8178fe0df86)
회차 판정: 수정 필요

### verifier 응답 원문

(인용 표시 `> `만 붙이고 내용 그대로 보존)

> ## 계획 리뷰 1회차: openapi-description
>
> **최종 판정: 수정 필요.** 막는 문제는 차단 1건(OpenAPI 스냅샷 갱신 누락)과 중요 3건입니다. 조사한 사실 대부분은 코드와 맞습니다.
>
> - **대상:** `.claude/tasks/openapi-description/task.md` (worktree wizardly-proskuriakova-fd2557, 계획 버전 = 현재 파일)
> - **기준 ID:** BE-02, SEC-04, E2E-02, E2E-06과 각 제외 항목
>
> ### 읽은 자료
> - 정책·기준: `docs/testing-policy.md`, `docs/testing/{backend,security,e2e}.md`, `.claude/checks.json`
> - 대상 코드: `OpenApiConfig.java`, `SecurityConfig.java`, `CsrfDoubleSubmitFilter.java`, `JwtAuthenticationFilter.java`, `AuthController.java`, `AuthCookieFactory.java`, `AuthService.java`(40-109행), `LoginResponse`/`RefreshResponse`, `ApiResponse`, `PageResponse`, `JacksonConfig`, `ErrorCode`(grep)
> - 컨트롤러 매핑 grep, `application.yml`, `application-prod.yml`
> - 문서: `docs/03_J-Bank_API설계.md` 2.1~2.8절·10절, `docs/10_J-Bank_폴더구조.md`(grep)
> - 계약·CI: `contracts/openapi/openapi.yaml`(1-40행·grep), `scripts/generate-api.sh`, `.github/workflows/backend-ci.yml`
> - 배포 구성: `infra/compose/docker-compose.prod.yml`, `infra/compose/Caddyfile`
> - 기존 테스트: `CsrfDoubleSubmitFilterTest.java`, `apps/frontend/types/api.ts` 머리 부분
>
> ### 확인하지 못한 범위
> - 운영 `/v3/api-docs`의 실제 `servers` 값과 운영 `.env`의 `SERVER_FORWARD_HEADERS_STRATEGY` 설정 여부. 직접 조회하지 않았습니다.
> - Swagger UI 5.18.2와 openapi-typescript의 실제 동작. 실행하지 않았고, 알려진 동작으로 판단했습니다.
> - 테스트와 빌드는 실행하지 않았습니다.
> - `docs/testing/{frontend,devops}.md`는 읽지 않았습니다.
>
> ### 1. 조사한 사실과 코드 대조
>
> **맞는 항목**
> - **쿠키:** 이름 3종, Secure·SameSite=Lax·Path=/, `XSRF-TOKEN`만 HttpOnly 아님. `AuthCookieFactory.java:16-47`과 일치합니다.
> - **csrfToken 응답 필드:** `LoginResponse.java:6`, `RefreshResponse.java:5`에 있습니다.
> - **CSRF 예외 경로:** `CsrfDoubleSubmitFilter.java:24,27`에서 로그인과 정확히 `/api/v1/customers`만 예외입니다.
> - **오류 코드:** 실패는 403 `COMMON_007`(:67)이고, 401 `COMMON_002`는 `RestAuthenticationEntryPoint.java:28`에 있습니다.
> - **재발급 시 CSRF 토큰 교체:** `AuthService.java:82`에서 새 UUID를 만들고 쿠키와 본문 모두 교체합니다(`AuthController.java:57-58`). 재발급 요청 자체도 CSRF 검사 대상이며, 공개 경로라도 예외가 아닙니다(`SecurityConfig.java:35`, API설계 2.8절 145행).
> - **customerId 쿼리 파라미터:** 실제로 없습니다. 다만 경로 변수 `{customerId}`는 있습니다(`CustomerAccountController.java:27`, `CustomerContractController.java:24`, `CustomerController.java:53`). 요청자와 같아야 하는 값입니다.
> - **Idempotency-Key 대상:** 이체, 입금, 출금 3개뿐입니다(`TransferController.java:50`, `AccountTransactionController.java:53,68`).
>
> **부정확한 항목**
> - **날짜 근거 문구:** "설정·코드에 `Asia/Seoul` 없음"은 사실과 다릅니다. `FdsDetectionTasklet.java:42`에 `ZoneId.of("Asia/Seoul")`가 있습니다.
>   - 다만 FDS 심야 판정용이라 직렬화와는 무관합니다. "+09:00 단정 불가"라는 결론은 맞습니다. `AuthService.java:105`가 `ZoneId.systemDefault()`를 쓰고, 운영 컨테이너에 TZ 설정이 없습니다(`docker-compose.prod.yml:23-38`).
>   - 근거 문구를 고치세요(권고).
> - **공개 경로 목록:** `/actuator/health/**`와 관리 포트의 Prometheus가 빠져 있습니다. 설명 문구에는 영향이 없습니다(권고).
>
> ### 지적 사항
>
> **[차단] F-1. OpenAPI 계약 스냅샷 갱신 누락 → backend-ci `openapi-drift` 실패**
> - **근거:**
>   - `.github/workflows/backend-ci.yml:37-90`이 기동한 API의 `/v3/api-docs.yaml`을 `contracts/openapi/openapi.yaml`과 `diff -u`로 비교합니다.
>   - 현재 스냅샷 `contracts/openapi/openapi.yaml:4-30`에는 지금의 설명과 `version: v0 (W1)`이 그대로 들어 있습니다.
>   - 폴더구조 문서 297-308행과 API설계 10절(712행)이 "코드를 바꾸면 스냅샷도 커밋"을 계약 규칙으로 정합니다.
>   - task.md의 범위(36-38행), 커밋 계획(67행), 완료 기준, 기준 표 어디에도 이 내용이 없습니다. `checks.json`의 verify에도 드리프트 검사가 없어서, verify는 통과하고 PR CI만 실패합니다.
> - **영향:** 버전·설명·보안 스킴을 바꾸면 스냅샷이 반드시 달라집니다. 병합 전 CI가 실패하거나, 계약 스냅샷이 코드와 어긋납니다.
> - **필요한 수정:**
>   - 범위와 커밋 1에 `contracts/openapi/openapi.yaml` 재생성을 넣습니다. `scripts/generate-api.sh`를 쓰거나 local 프로필 bootRun 후 덤프합니다.
>   - 같은 커밋에 넣고, 완료 기준에 드리프트 검사 근거를 추가합니다. backend-ci `openapi-drift` 성공이나 로컬 `diff` 결과면 됩니다.
>   - `apps/frontend/types/api.ts`가 재생성 후에도 그대로인지 확인해 기록합니다. openapi-typescript는 보통 info와 securitySchemes를 내보내지 않지만 실행으로 확인해야 합니다. 바뀌면 함께 커밋합니다.
>   - 적용 영역의 "DevOps: 해당 없음" 이유도 고칩니다. CI 계약 검사에 영향이 있습니다.
>
> **[중요] F-2. 목표인 "Swagger에서 로그인 후 상태 변경 요청 호출"을 검증하는 시나리오가 없고, 운영 `servers` URL 위험을 조사하지 않음**
> - **근거:**
>   - 목표(32행)와 REQ-03은 실제 호출 가능을 약속합니다. 그런데 AC-03은 "로그인하지 않음"으로 Authorize 버튼 표시만 봅니다(55행). 사전 검증도 문구 단위 테스트뿐입니다.
>   - 운영은 Caddy가 TLS를 끝내고 `reverse_proxy api:8080`으로 넘깁니다(`Caddyfile:3-4`). 저장소 안의 설정에는 `server.forward-headers-strategy`가 없습니다(`application.yml`, `application-prod.yml`, `docker-compose.prod.yml`). 운영 `.env`는 확인하지 못했습니다.
>   - 이 경우 Spring Boot 기본값(비클라우드 환경에서 NONE) 때문에 springdoc이 만드는 `servers[0].url`이 `http://api.j-bank.site`가 될 가능성이 높습니다. 그러면 https Swagger UI의 Try it out이 mixed content로 막혀, 로그인부터 실패할 수 있습니다.
>   - 15행 "Swagger에서 로그인하면 브라우저가 쿠키를 저장"은 이 부분이 확인되지 않은 가정입니다.
> - **영향:** Authorize를 추가해도 운영에서는 목표를 이루지 못할 수 있습니다. 해결하려면 설정 변경(forward headers 신뢰) 또는 `servers` 명시가 필요한데, 둘 다 제품·보안 선택이라 계획 단계에서 정해야 합니다.
> - **필요한 수정:**
>   - 운영 `/v3/api-docs`의 `servers` 값을 조사해 사실에 기록합니다.
>   - http로 나오면 해결 방법을 사용자에게 묻습니다. 선택지는 `forward-headers-strategy` 설정, 상대 경로 `servers: /` 명시, 목표를 "로컬 한정"으로 축소하는 것입니다. 이 결정은 스냅샷 `servers`에도 영향을 줍니다.
>   - 실제 호출을 확인하는 시나리오를 추가합니다. 예: 로컬 Swagger에서 로그인 → Authorize에 `csrfToken` 입력 → `POST /api/v1/auth/refresh` 또는 `logout`이 403이 아닌 2xx를 받고, Authorize 없이 보내면 403. 운영에서 테스트 계정으로 확인할지는 사용자가 정합니다. 이를 E2E-02와 연결합니다.
>
> **[중요] F-3. REQ와 테스트 시나리오 연결 불완전**
> - **근거:** AC-01(53행)은 "REQ-01~05"를 덮는다고 적었지만, 100행의 문구 검사 목록은 일부만 다룹니다.
>   - REQ-02: `refresh_token`, POST·PATCH·DELETE 규칙, 예외(로그인·고객 등록)가 검사 목록에 없습니다.
>   - REQ-03: "재발급 뒤 다시 입력" 안내가 검사 목록에 없습니다.
>   - REQ-05: 응답 봉투·금액·페이지 문구 유지, "오프셋" 표현, 같은 키 재요청 멱등 안내가 검사 목록에 없습니다.
> - **기준:** `testing-policy.md:7`은 각 REQ가 테스트 시나리오에 연결되어야 한다고 정합니다.
> - **필요한 수정:** 아래 둘 중 하나를 택합니다.
>   - 검사 문자열을 늘립니다. 예: `refresh_token`, `/api/v1/auth/refresh` 또는 "재발급", `POST`·`PATCH`·`DELETE`, `/api/v1/customers` 또는 "고객 등록", `"success"`, `totalElements`, "오프셋".
>   - 또는 일부 항목을 "결과 리뷰에서 원문 대조"로 명시해 별도 시나리오로 연결합니다.
> - **모순 여부(권고 수준):** 금지 문자열 `customerId`는 REQ-02("쿼리 파라미터 안내 금지")보다 넓습니다. 경로 변수 `{customerId}`를 설명할 수 없게 되므로 의도했는지 기록하세요. `+09:00` 금지는 REQ-05와 일치하지만, "예: `Z` 또는 `+09:00`" 같은 예시도 막는다는 점을 알아두세요.
>
> **[중요] F-4. 커밋 1이 fix와 feat를 섞음**
> - **근거:** `.claude/rules/commits.md`는 "기능 구현… 버그 수정… 절대 같은 커밋에 넣지 않는다"고 정합니다. 커밋 1(67행)에는 오래된 설명·버전 수정(fix)과 Authorize 보안 스킴 추가(새 기능, Q-02 결정)가 함께 들어 있습니다.
> - **참고:** 테스트를 코드와 같은 커밋에 넣는 것은 이전 작업에서도 써 온 관례라 문제 삼지 않습니다(`prod-metrics-scrape/task.md:89-94`).
> - **필요한 수정:**
>   - `fix(api)`: 버전 v1, 인증·공통 규칙 설명 갱신, 단위 테스트, 스냅샷.
>   - `feat(api)`: `X-CSRF-TOKEN` Authorize 스킴과 전역 적용, Swagger 사용법 문단, 단위 테스트, 스냅샷.
>   - 두 커밋 모두 드리프트 검사가 통과하는 상태여야 합니다.
>
> **[권고] F-5. Authorize 전역 적용 판단**
> - springdoc 2.7.0에서 `OpenAPI` 빈에 Components의 apiKey 스킴(in header, name `X-CSRF-TOKEN`)과 `addSecurityItem`을 넣는 것은 표준 방식입니다. Swagger UI는 Authorize 값을 상속된 security가 있는 모든 작업에 헤더로 붙입니다. 실행으로는 확인하지 않았습니다.
> - 공개 엔드포인트(로그인·가입)에는 해가 없습니다. CSRF 필터가 URI 기준으로 건너뛰고, 헤더를 추가해도 거절 경로가 없습니다(`CsrfDoubleSubmitFilter.java:41-45`).
> - 재발급은 헤더가 필요하므로 전역 적용이 오히려 맞습니다.
> - 운영은 같은 출처이고 fetch 기본 credentials가 same-origin이라 쿠키가 실립니다(F-2의 scheme 문제는 별개).
> - 권고 1: `persistAuthorization`이 기본 false라 페이지를 새로고침하면 Authorize 값이 사라집니다. 사용법 안내에 한 줄 추가를 권합니다.
> - 권고 2: 로컬 안내(28행)에서 Safari는 http://localhost의 Secure 쿠키를 저장하지 않을 수 있습니다. "Chrome·Firefox 기준"으로 한정하기를 권합니다.
>
> **[권고] F-6. 날짜 문구**
> - 운영 TZ가 UTC면 Jackson ISO 형식은 `+00:00`이 아니라 `Z`로 출력합니다. "시간대 오프셋 포함(예: `Z`, `+09:00`)"처럼 쓰는 것이 정확합니다. F-3의 `+09:00` 금지 규칙과 함께 정하세요.
>
> ### 적용 제외 항목 타당성
> - **BE-01, BE-03~08:** 타당합니다. 요청 처리 경로와 DB 변경이 없습니다.
> - **SEC-01~03, 05~08:** 타당합니다. 비밀값을 문서에 넣지 않는다는 점도 기록돼 있습니다.
> - **SEC-04 회귀만 확인:** 타당합니다. 단, "보안 설정 파일 변경 없음"은 변경 파일 목록으로 확인해야 합니다. F-2에서 forward-headers를 택하면 SEC-04·SEC-07 재평가가 필요합니다.
> - **FE:** api.ts 불변 확인을 조건으로 타당합니다(F-1).
> - **OPS:** CI 계약 검사 영향이 있어 이유를 수정해야 합니다(F-1).
> - **E2E-01, 03~05:** 타당합니다.
> - **성능 불필요:** 타당합니다.
>
> ### 질문 누락·임의 가정
> - Q-01, Q-02 답변은 요구사항에 반영됐습니다.
> - 누락된 결정은 F-2의 운영 `servers` scheme 처리 방식입니다. 이 결정은 목표 달성 여부를 바꿉니다.
>
> ### 이전 지적
> - 없음(1회차).
>
> 최종 판정: 수정 필요

### 메인 처리 내역

- F-1: 조사한 사실에 계약 스냅샷·드리프트 잡 기록. 범위·커밋 1~3에 스냅샷 재생성, REQ-07·AC-03·OPS-02(적용) 추가, 프론트 타입 변화 확인을 재점검·REQ-07에 기록. DevOps 영역 판정 수정.
- F-2: 운영 `/v3/api-docs` 조회로 `servers`가 `http://api.j-bank.site`임을 확인해 사실에 기록. 사용자에게 선택지(프록시 헤더 신뢰·상대 경로 `/`·보기 전용)를 설명하고 Q-03(상대 경로 `/`) 결정. 확인 위치는 Q-04(로컬은 Claude, 운영 실제 호출은 사용자). REQ-06(servers)·REQ-08(로컬 실제 호출 403→2xx)·REQ-09(운영) 추가, AC-04·05와 E2E-02 연결.
- F-3: AC-01 검사 문자열을 REQ-02·03·05 항목까지 확장. 금지 문자열은 `customerId` 대신 `쿼리 파라미터로`로 좁힘. `+09:00` 금지 제거, `오프셋` 포함 검사.
- F-4: 커밋을 fix(설명·버전) / fix(servers) / feat(Authorize)로 분리, 각 커밋에 스냅샷 포함.
- F-5: 사용법에 새로고침 뒤 재입력 안내(REQ-03·검사 문자열 `새로고침`), Safari 차이를 사실에 기록.
- F-6: REQ-05 날짜 예시를 `Z`, `+09:00`으로.
- 권고(사실 정정): `Asia/Seoul`은 FDS에서만 쓴다고 고침.

## 2회차

회차 계획 지문: 13020b0e588e3ffa3d456c6ae2b2bf810e82cf9e95f7e6602d267434a6bc7926 (2026-10-02T05:10:00Z 호출, verifier agentId a6268f4ae1bed0eed)
회차 판정: 수정 필요

### verifier 응답 원문

(인용 표시 `> `만 붙이고 내용 그대로 보존)

> ## 계획 리뷰 2회차(재리뷰 1/2): openapi-description
>
> ### 검증 대상
> - `.claude/tasks/openapi-description/task.md`: 1회차 F-1~F-6을 반영하고 Q-03·Q-04를 추가한 현재 파일
> - `.claude/tasks/openapi-description/plan-review.md`: 1회차 원문과 처리 내역
>
> ### 읽은 자료
> - 정책·기준
>   - `docs/testing-policy.md`
>   - `docs/testing/{backend,security,e2e,devops}.md`에서 해당 ID 행
> - 대상 코드
>   - `OpenApiConfig.java`
>   - `SecurityConfig.java`(20-89행)
>   - `CsrfDoubleSubmitFilter.java`
>   - `AccountController.java`, `AccountOpenRequest.java`, `AccountService.java`(55-89행)
>   - `CustomerController.java`, `CustomerRegisterRequest.java`, `CddGradeCalculator.java`
>   - `AuthController.java` 매핑(grep)
> - 설정·인프라
>   - `application.yml`, `application-local.yml`
>   - `infra/compose/docker-compose.yml`(grep), `docker-compose.prod.yml`, `Caddyfile`
>   - `apps/frontend/next.config.mjs`
> - 계약·CI
>   - `contracts/openapi/openapi.yaml`(grep)
>   - `scripts/generate-api.sh`
>   - `.github/workflows/backend-ci.yml`
>
> ### 확인하지 못한 범위
> - 실행한 것이 없습니다. 테스트, 빌드, 앱 기동, 운영 curl, 브라우저 모두 실행하지 않았습니다.
> - 아래 내용은 라이브러리의 알려진 동작과 코드로 판단했습니다.
>   - springdoc 2.7.0, Swagger UI 5.18.2, Spring CORS 처리, Tomcat 포트 처리
>   - 브라우저의 Origin 헤더 전송 규칙
> - `docs/testing/frontend.md`는 읽지 않았습니다. 이 작업에는 해당하지 않습니다.
>
> ### 중점 질문 답변
> 1. **springdoc에 servers를 명시하면 스냅샷도 `/`가 되는가: 예.**
>    - springdoc 2.x는 `OpenAPI` 빈에 servers가 있으면 요청 기반 "Generated server url"로 덮어쓰지 않습니다.
>    - 따라서 local 기동 덤프와 CI 덤프 모두 `servers: - url: /`가 됩니다.
>    - 현재 스냅샷 `contracts/openapi/openapi.yaml:31-32`의 `http://localhost:8080`은 커밋 2에서 바뀝니다.
> 2. **상대 서버 `/`의 요청 주소: 화면 출처가 아니라 명세 문서 URL 기준으로 해석됩니다.**
>    - OAS3 규칙상 상대 서버 URL은 명세 문서 위치 기준입니다. Swagger UI·swagger-client 5도 그렇게 동작합니다.
>    - 명세가 `https://api.j-bank.site/v3/api-docs`이므로 결과는 같습니다(`https://api.j-bank.site/`). 로컬은 `http://localhost:8080/`입니다.
>    - 다만 운영 POST는 아래 P2-1 때문에 막힙니다.
> 3. **REQ-08 로컬 시나리오: 코드상 가능합니다.**
>    - CSRF 필터가 JWT 필터 뒤, 인가 앞에 있습니다(`SecurityConfig.java:70-73`). 그래서 로그인 상태에서 헤더 없이 `POST /api/v1/accounts`를 보내면 403 `COMMON_007`이 나옵니다(`CsrfDoubleSubmitFilter.java:47-51`).
>    - 로컬 Origin `http://localhost:8080`은 요청 출처와 같아 CORS 검사 대상이 아닙니다.
>    - 2xx가 나오는 조건이 있습니다(P2-2 참고).
> 4. **커밋 3개 분리: 가능합니다.**
>    - 각 커밋에서 `OpenApiConfig`, 그 시점의 단위 테스트, 그 시점의 스냅샷을 함께 커밋하면 됩니다.
>    - 커밋 1의 스냅샷은 servers가 `http://localhost:8080`인 상태 그대로입니다. CI도 localhost:8080에 접속합니다.
>    - CI(`backend-ci.yml:4-7`, 87-90행)는 PR의 마지막 커밋만 검사합니다. 중간 커밋은 AC-03의 로컬 diff 기록만이 근거입니다. 계획에 이미 적혀 있으니 문제는 아닙니다.
>
> ### 지적 사항
>
> **[차단] P2-1. 운영에서 Swagger로 보내는 POST(로그인 포함)는 servers를 `/`로 바꿔도 CORS 필터의 403으로 막힐 가능성이 높음. 목표와 REQ-09를 이 범위로는 이룰 수 없음.**
> - 근거:
>   - CORS 필터가 모든 경로에 걸려 있습니다.
>     - 적용 위치: `SecurityConfig.java:56`, 86행 `/**`
>     - 허용 출처는 `http://localhost:3000` 하나뿐입니다(81행).
>   - 운영은 Caddy가 TLS를 끝내고 앱으로 넘깁니다(`Caddyfile:3-4`). 앱은 프록시 헤더를 믿지 않습니다(task.md 16행, 운영 servers가 `http://...`로 나온다는 메인 조회).
>     - 그래서 앱이 보는 `request.getScheme()`은 `http`입니다.
>   - 브라우저는 같은 출처라도 POST·PATCH·DELETE에는 `Origin: https://api.j-bank.site`를 붙입니다.
>   - Spring은 Origin과 요청의 scheme·host·port를 비교합니다(`CorsUtils.isCorsRequest`). 여기서는 https와 http로 다르므로 CORS 요청으로 처리합니다.
>     - 허용 목록에 없으므로 `DefaultCorsProcessor`가 403 "Invalid CORS request"를 돌려줍니다.
>     - CORS 필터는 CSRF 예외와 무관하게 먼저 동작합니다. 따라서 로그인 `POST /api/v1/auth/login`부터 막힙니다.
>   - 운영에는 프론트 서비스가 없습니다(`docker-compose.prod.yml`에 api만 있음). 그래서 운영에서 브라우저 POST가 성공한 기록도 없을 것입니다.
>   - task.md 16행 "앱 코드는 요청 스킴을 쓰지 않는다(grep 0건)"는 앱 코드에 한해서만 맞습니다. 프레임워크의 CORS 처리는 요청 스킴을 씁니다. 이 사실이 Q-03 결정의 전제였습니다.
> - 영향:
>   - 커밋 2(`fix(api)`: "https 운영에서 호출 가능하게 함")는 GET만 고칩니다. mixed content 해소 효과입니다.
>   - REQ-09의 사용자 실제 호출과 목표 37행 "운영 Swagger에서 로그인 후 상태 변경 요청"은 실패합니다.
>   - Claude 확인 범위(로그인하지 않음, 버튼·주소 확인)로는 이 문제가 드러나지 않습니다. 배포 뒤 사용자가 처음 발견하게 됩니다.
> - 필요한 수정:
>   1. 먼저 사실을 확인합니다. 읽기 전용에 가까운 요청이면 됩니다. 예:
>      `curl -i -X POST https://api.j-bank.site/api/v1/auth/login -H 'Origin: https://api.j-bank.site' -H 'Content-Type: application/json' -d '{}'`
>      - 403 `Invalid CORS request`가 나오는지 봅니다.
>      - Origin을 뺀 같은 요청은 400 또는 401이 나와야 합니다.
>      - 결과를 사실에 기록합니다.
>   2. 확인되면 사용자에게 다시 묻습니다. 선택지와 장단점은 다음과 같습니다.
>      - (a) `server.forward-headers-strategy` 신뢰
>        - scheme이 https가 되어 CORS 오판과 servers가 함께 해결됩니다.
>        - 프록시 신뢰 범위, X-Forwarded-For 신뢰를 다시 평가해야 합니다(SEC-04·SEC-07).
>      - (b) 운영 허용 출처에 `https://api.j-bank.site` 추가
>        - CORS 설정 변경이므로 SEC-04를 다시 평가해야 합니다.
>      - (c) 운영은 GET만 지원하도록 목표와 REQ-09 축소
>        - 사용자 실제 호출 단계를 빼거나 GET으로 바꿉니다.
>      - (a)·(b)는 현재 "하지 않을 일" 71행, AC-02 "보안 설정 파일 변경 없음", SEC-04 "회귀만" 판정과 충돌합니다. 고르면 그 항목들과 테스트(CORS·Origin 사례)를 함께 고쳐야 합니다.
>   3. 결정에 맞게 고칩니다.
>      - 커밋 2의 메시지와 범위
>      - REQ-06·REQ-09
>      - 사실 16행
>      - 재점검 30행 "서버 보안 규칙 변경 없음"
>
> **[권고] P2-2. REQ-08 계좌 개설 2xx의 전제 조건을 명시**
> - 계좌 개설이 성공하려면 다음 조건이 필요합니다.
>   - 고객 상태가 ACTIVE여야 합니다. 등록 직후에는 ACTIVE입니다.
>   - EDD 고객이면 안 됩니다. EDD면 422 `ACC_005`가 나옵니다(`AccountService.java:66-69`).
>   - `initialDeposit`가 0이어야 합니다(70-72행).
> - EDD가 되는 경우: 비대면 확인(`NON_FACE_TO_FACE`)이면 위험도가 한 단계 오릅니다(`CddGradeCalculator.java:21-25`). 그러면 MEDIUM 위험 고객이 EDD가 됩니다.
> - 권장: 시나리오에 "등록 응답 `eddRequired=false`인 테스트 고객, 본문 `initialDeposit: \"0\"`"을 적습니다. 또는 다른 조건이 필요 없는 `POST /api/v1/auth/logout`(`AuthController.java:63`)을 대상으로 씁니다.
> - 완료를 막지는 않습니다.
>
> **[권고] P2-3. OPS-02 ID 의미 차이**
> - `devops.md:14`의 OPS-02는 "CI가 실패를 막는지"를 확인하는 기준입니다(실패를 넣어 파이프라인 확인).
> - 이 작업은 CI를 바꾸지 않고, 기존 드리프트 잡을 통과시키는 것이 목적입니다. 계약 일치는 BE-02(API 계약) 쪽 증거에 가깝습니다.
> - 현재 연결로도 증거는 충분합니다. ID 설명만 "기존 CI 계약 검사 통과(파이프라인 변경 없음)"로 명확히 하기를 권합니다.
>
> ### 적용 제외 항목 타당성
> - 다음은 타당합니다. 단, P2-1 결정에 따라 바뀔 수 있습니다.
>   - BE-01·03~08
>   - FE 전체: api.ts 재생성 확인이 조건입니다.
>   - E2E-01·03~05, 성능 불필요
> - SEC-01~03·05~08과 OPS-01·03~08 제외는 현재 범위에서 타당합니다.
>   - P2-1에서 (a)를 고르면 SEC-07과 OPS-03(설정)을, (b)를 고르면 SEC-04를 회귀가 아니라 적용 항목으로 다시 판정해야 합니다.
>
> ### 이전 지적 처리
> - **F-1: 해결.**
>   - 스냅샷 재생성이 범위, 커밋 1~3, REQ-07, AC-03, OPS-02에 들어갔습니다.
>   - api.ts 확인과 DevOps 판정 수정도 반영됐습니다.
> - **F-2: 부분 해결.**
>   - 운영 servers 조사, Q-03·Q-04 결정, 실제 호출 시나리오(REQ-08·09, AC-04·05, E2E-02)가 반영됐습니다.
>   - 새 근거(CORS 처리)로 보면, 고른 방법으로는 운영 POST가 가능하지 않습니다. P2-1로 이어집니다.
> - **F-3: 해결.**
>   - AC-01 검사 문자열이 REQ-02·03·05를 덮습니다.
>   - 금지 문자열을 `쿼리 파라미터로`로 좁혔고, `+09:00` 금지를 없앴습니다.
> - **F-4: 해결.** fix·fix·feat로 분리했고, 각 커밋에 스냅샷이 들어갑니다.
> - **F-5: 해결.** 새로고침 안내와 Safari 차이를 기록했습니다.
> - **F-6: 해결.** 날짜 예시가 `Z`, `+09:00`입니다.
>
> 최종 판정: 수정 필요

### 메인 처리 내역

- P2-1: 운영에서 빈 본문 로그인 POST로 사실 확인(Origin 있음 403 `Invalid CORS request`, 없음 400 `COMMON_001`). 사실에 기록하고 사용자에게 선택지(프록시 헤더 신뢰·CORS 허용 출처 추가·운영은 GET만)를 다시 물어 Q-05(CORS 허용 출처 추가) 결정. 재점검에 변경 범위·위험 기록, 범위·REQ-10·AC-02(CORS 테스트)·AC-05(운영 Origin 확인)·REQ-09 갱신, "하지 않을 일"에서 CORS 예외 명시, SEC-04를 적용으로 바꿈, 커밋 3 `fix(api)` CORS 추가, 커밋 2 메시지를 mixed content 해소로 정정.
- P2-2: REQ-08에 `eddRequired=false` 테스트 고객과 `initialDeposit: "0"` 명시.
- P2-3: OPS-02 설명을 "기존 CI 계약 드리프트 검사 통과(파이프라인 변경 없음)"로.

## 3회차

회차 기준: 계획 리뷰 한도(최초 1회+재리뷰 2회)의 마지막 회차. 2026-10-02T06:15:56Z 호출, verifier agentId a275b3fd2fd4c955a.
대상 계획 지문: 23ee2d5d975d618a9d56107221f81420a927b8a805f7a2b101477de5e48942ee
최종 판정: 통과 권고

### verifier 응답 원문

(인용 표시 `> `만 붙이고 내용 그대로 보존)

> ## 계획 리뷰 3회차(재리뷰 2/2, 마지막 회차): openapi-description
>
> ### 검증 대상
> - `/Users/imjaehyeon/Documents/[01] - Project/j-bank/.claude/worktrees/wizardly-proskuriakova-fd2557/.claude/tasks/openapi-description/task.md`: 2회차 P2-1~3과 Q-05를 반영한 현재 파일
> - `.claude/tasks/openapi-description/plan-review.md`: 1·2회차 원문과 처리 내역
>
> ### 읽은 자료
> - 작업 문서: task.md, plan-review.md
> - 기준: `docs/testing/{security,backend,e2e,devops}.md`의 SEC-04, BE-02, E2E-02, E2E-06, OPS-02 행
> - 코드
>   - `apps/jbank-api/src/main/java/com/jbank/auth/config/SecurityConfig.java` 전체
>   - `apps/jbank-api/src/test/java/com/jbank/auth/controller/AuthControllerTest.java` 전체
>   - `apps/jbank-api/build.gradle.kts`(Boot 3.5.16, springdoc 2.7.0)
>   - `infra/compose/Caddyfile`
> - grep: 테스트·소스 전체에서 CORS 관련 코드. `SecurityConfig.java:81` 외에는 없었고, 기존 CORS 테스트도 없습니다.
>
> ### 확인하지 못한 범위
> - 실행한 것이 없습니다. 테스트, 앱 기동, curl, 브라우저 모두 실행하지 않았습니다.
> - 아래 두 가지는 Spring 6.2와 브라우저의 알려진 동작으로 판단했습니다.
>   - Spring의 CORS 처리(`CorsUtils`, `DefaultCorsProcessor`)
>   - 브라우저의 Origin 전송과 preflight 규칙
> - 운영 403/400 결과는 메인이 확인한 사실로 받았습니다. 제가 직접 재현하지 않았습니다.
>
> ### 중점 1: P2-1~3 반영 확인
> - **P2-1: 해결.**
>   - 운영 사실이 task.md 17행에 들어갔습니다(Origin 있으면 403, 없으면 400).
>   - 16행 "프레임워크 CORS는 스킴을 씀"도 정정됐습니다.
>   - Q-05 결정이 다음 항목에 모두 일관되게 반영됐습니다: 재점검 33~34행, 범위 46~47행, REQ-09·10, AC-02·05, "하지 않을 일" 78행, SEC-04 적용(104·112행), 커밋 2 메시지 정정, 커밋 3 추가.
> - **P2-2: 해결.** REQ-08에 `eddRequired=false` 고객과 `initialDeposit: "0"` 조건이 들어갔습니다.
> - **P2-3: 해결.** OPS-02 설명이 "파이프라인 변경 없이 기존 드리프트 검사 통과"로 정리됐습니다(103·111행).
>
> ### 중점 2: CORS 허용 출처 추가의 실효성·보안·테스트 가능성
>
> **운영 통과 여부: 통과합니다.**
> - **preflight 없음:** 브라우저는 페이지 출처(`https://api.j-bank.site`)와 요청 URL이 같으므로 preflight를 보내지 않습니다. `X-CSRF-TOKEN` 같은 사용자 정의 헤더가 있어도 마찬가지입니다. 대신 POST·PATCH·DELETE에는 Origin을 붙입니다.
> - **서버의 판정 과정:**
>   - Caddy 뒤의 앱은 요청 스킴을 http로 봅니다. 그래서 `CorsUtils.isCorsRequest`가 이 요청을 CORS 요청으로 판정합니다.
>   - `DefaultCorsProcessor`는 이어서 출처를 검사합니다. 출처가 목록에 있으면 통과합니다.
>   - 메서드 검사도 통과합니다. POST·PATCH·DELETE가 허용 목록(82행)에 있습니다.
>   - preflight가 아닌 요청은 요청 헤더가 허용 목록에 없어도 거절되지 않습니다. 그리고 `Content-Type`·`X-CSRF-TOKEN`은 이미 83행 허용 목록에 있습니다.
>   - 통과하면 서버는 `Access-Control-Allow-Origin`·`Access-Control-Allow-Credentials` 응답 헤더만 붙이고 다음 필터로 넘깁니다. CSRF 검사는 그 뒤에 그대로 적용됩니다.
> - **쿠키 전송:** 브라우저 입장에서 같은 출처이므로 fetch 기본값(credentials `same-origin`)으로 쿠키가 실립니다.
> - 따라서 REQ-09의 운영 결과로 "Origin을 붙인 빈 본문 로그인이 400"을 기대하는 것은 타당합니다.
>
> **보안상 새 위험: 실질적인 위험은 없습니다.**
> - **브라우저 쪽:** `https://api.j-bank.site`에서 나오는 요청은 브라우저가 원래 같은 출처로 취급합니다. 따라서 CORS 응답 헤더가 새 권한을 주지 않습니다. 그 출처에 XSS가 있다면 CORS 설정과 무관하게 이미 같은 출처 권한을 갖습니다.
> - **비브라우저 쪽:** Origin을 위조하는 비브라우저 클라이언트는 피해자의 쿠키를 갖고 있지 않습니다. 그래서 자격 증명 허용에 따른 추가 노출도 없습니다.
> - **http 출처:** `http://api.j-bank.site`는 목록에 넣지 않았고, Caddy가 https로 리다이렉트합니다.
> - **남는 위험:** 스킴 오인 자체와 도메인 하드코딩은 그대로 남습니다. 재점검 33행이 이를 기록했으므로 결정 사항으로 인정합니다.
>
> **MockMvc 테스트로 검증 가능한지: 가능합니다.**
> - **기본 요청이 교차 출처가 됨:** `MockHttpServletRequest`의 기본값은 scheme `http`, serverName `localhost`, port 80입니다. 그래서 세 출처가 모두 교차 출처로 판정되어 실제로 허용 목록 검사를 거칩니다.
>   - `https://api.j-bank.site`: 스킴과 호스트가 다릅니다.
>   - `http://localhost:3000`: 포트가 다릅니다(3000 대 80).
>   - `https://evil.example`: 거절되어 403과 본문 `Invalid CORS request`가 나옵니다.
> - **실제 필터 체인 사용:** `AuthControllerTest`는 `@WebMvcTest` + `@Import(SecurityConfig.class, …)` 구성입니다(31~34행). 따라서 실제 CORS 필터가 걸리고, `AuthService` 모킹으로 허용 경로의 200도 만들 수 있습니다.
>
> ### 지적 사항
>
> **[권고] P3-1. 허용 사례의 판정을 CORS 처리 결과로 단언하기**
> - **근거:** AC-02(69행), REQ-10(63행)은 허용 사례를 "CORS 거절 없음"으로만 정의합니다. 상태 코드만 보면, 요청이 CORS 처리를 아예 거치지 않아도(예: 같은 출처로 판정되는 경우) 같은 결과가 나옵니다.
> - **필요한 수정:** 허용 사례에서 아래 두 헤더를 단언하면, 허용 목록 검사를 실제로 거쳤다는 증거가 됩니다.
>   - `Access-Control-Allow-Origin`이 요청한 Origin과 같음
>   - `Access-Control-Allow-Credentials: true`
> - 거절 사례는 상태 403에 본문 `Invalid CORS request`까지 단언하기를 권합니다. CSRF 403과 구분하기 위해서입니다.
> - 현재 기본 MockMvc 조건에서도 교차 출처 판정은 성립하므로, 완료를 막지는 않습니다.
>
> **[권고] P3-2. `SecurityConfig` 주석이 사실과 어긋나게 됨, AC-02의 "한 줄" 조건과 충돌**
> - **근거:** `SecurityConfig.java:77-78`의 주석은 "로컬 개발용. 배포 환경은 … CORS 자체가 발생하지 않는다"입니다. 운영 출처를 추가하면 이 주석은 거짓이 됩니다.
> - **충돌:** AC-02(69행)와 SEC-04 행(112행)은 "diff가 허용 출처 한 줄뿐"을 요구합니다. 주석을 고치면 이 조건을 엄격히 읽을 때 위반이 됩니다.
> - **필요한 수정:** AC-02 문구를 "허용 출처 값과 관련 주석만 변경, 메서드·헤더·자격 증명·필터 변경 없음"으로 바꿉니다. 주석에는 운영 Swagger 때문에 추가한 이유를 적습니다. 남는 스킴 오인과 도메인 변경 시 갱신 필요도 함께 적습니다.
>
> **[권고] P3-3. 표기 순서**
> - 요구사항 표에서 REQ-10이 REQ-09보다 앞에 있습니다(63~64행). 의미에는 영향이 없습니다.
>
> 차단·중요 지적은 없습니다.
>
> ### 적용 제외 항목 타당성
> - SEC-04를 적용으로 바꾼 판정은 타당합니다. 허용·거절 사례를 테스트하고 운영 curl로 확인하도록 연결돼 있습니다.
> - SEC-07·OPS-03은 제외로 남아도 됩니다. 프록시 헤더 신뢰를 택하지 않았기 때문입니다.
> - 나머지 제외 항목(BE-01·03~08, SEC-01~03·05~08, FE, OPS-01·03~08, E2E-01·03~05, 성능)은 2회차 판단대로 타당합니다.
> - 커밋 3에 "계약 변화 없음"이라고 적은 것도 맞습니다. `SecurityConfig`는 OpenAPI 출력에 영향을 주지 않습니다.
>
> ### 이전 지적 처리
> - **1회차**
>   - F-1, F-3~F-6: 해결된 상태가 유지됩니다.
>   - F-2: P2-1 해결로 완결됐습니다.
> - **2회차**
>   - P2-1, P2-2, P2-3: 모두 해결.
>
> 최종 판정: 통과 권고

### 메인 처리 내역

task.md를 바꾸면 계획 지문이 바뀌어 한도 밖 재리뷰가 필요하므로, 권고는 현재 명세 범위 안에서 구현 시 반영한다.
- P3-1: CORS 허용 사례에서 `Access-Control-Allow-Origin`(요청 Origin과 같음)·`Access-Control-Allow-Credentials: true`를, 거절 사례에서 403과 본문 `Invalid CORS request`를 단언한다.
- P3-2: `SecurityConfig`의 CORS 주석을 사실에 맞게 고친다(운영 Swagger용 출처 추가 이유, 남는 스킴 오인, 도메인 변경 시 갱신). 주석은 설정이 아니므로 AC-02의 "보안 설정 변경은 허용 출처 한 줄"과 충돌하지 않는 것으로 보고, 결과 리뷰에서 diff가 "허용 출처 값과 관련 주석만, 메서드·헤더·자격 증명·필터 변경 없음"임을 확인받는다.
- P3-3: 표기 순서만의 문제라 고치지 않는다.
