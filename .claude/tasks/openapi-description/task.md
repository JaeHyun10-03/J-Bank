# 작업 명세

## 요구사항 구체화

명세 상태: 확정
원문 요청: 스웨거 설명 고쳐줘
(직전 대화: 운영 Swagger의 API 설명이 "인증은 아직 없습니다(W3에서 추가 예정)", "`customerId`를 쿼리 파라미터로 넘기세요", 버전 `v0 (W1)`으로 오래됐다고 보고함)
요청 해석: Swagger(OpenAPI) 첫 화면의 API 설명과 버전을 현재 서버 동작에 맞게 고친다. 사용자 결정으로 Swagger에서 상태 변경 요청을 직접 호출할 수 있게 Authorize 입력을 더하고, 운영 Swagger의 요청 주소 문제도 고친다. 서버의 인증·CSRF 동작 자체는 바꾸지 않는다.
조사한 사실:
- 설명 위치: `apps/jbank-api/src/main/java/com/jbank/global/config/OpenApiConfig.java` 한 곳(텍스트 블록). springdoc-openapi 2.7.0(Swagger UI 5.18.2), openapi 3.0.1. 이 설정을 검사하는 단위 테스트는 없다.
- 계약 스냅샷: `contracts/openapi/openapi.yaml`에 지금 설명·`version: v0 (W1)`·`servers: http://localhost:8080`이 그대로 들어 있다. backend-ci `openapi-drift` 잡이 local 프로필로 기동한 API의 `/v3/api-docs.yaml`과 이 파일을 `diff -u`로 비교하고, 폴더구조 문서·API설계 10절이 "코드를 바꾸면 스냅샷도 커밋"을 규칙으로 정한다. 재생성은 `scripts/generate-api.sh`(localhost:8080에서 덤프 후 `apps/frontend/types/api.ts` 재생성). 하네스 verify에는 드리프트 검사가 없다(계획 리뷰 1회차 F-1).
- 오래된 문구: 2번 항목 "인증은 아직 없습니다(W3에서 추가 예정)"와 `customerId` 쿼리 파라미터 안내. 실제로 `customerId` 쿼리 파라미터는 없고(경로 변수 `{customerId}`는 일부 고객 API에 있음, 요청자와 같아야 함), 인증 주체는 JWT 쿠키에서 꺼낸다.
- 현재 인증(API설계 2.2·2.8절, `AuthController`·`AuthService`·`AuthCookieFactory`·`CsrfDoubleSubmitFilter`): 로그인(`POST /api/v1/auth/login`)이 `access_token`·`refresh_token`(HttpOnly)·`XSRF-TOKEN`(스크립트로 읽을 수 있음) 쿠키를 내려준다. 모두 Secure, SameSite=Lax, Path=/. 로그인·재발급 응답 본문에 같은 값의 `csrfToken`이 있다. POST·PATCH·DELETE는 `X-CSRF-TOKEN` 헤더 값이 `XSRF-TOKEN` 쿠키와 같아야 하고, 예외는 로그인과 고객 등록(`/api/v1/customers`)뿐이다. 재발급(`POST /api/v1/auth/refresh`)도 CSRF 검사 대상이며 CSRF 토큰을 새로 준다. 미인증 401 `COMMON_002_UNAUTHORIZED`, CSRF 불일치 403 `COMMON_007_CSRF_TOKEN_INVALID`.
- 이체·입금·출금 3개만 `Idempotency-Key` 헤더가 필수다(각 작업 설명과 파라미터에 이미 있음).
- 맞는 문구: 응답 봉투(`ApiResponse`: success·data·error), 금액 문자열(`JacksonConfig` BigDecimal 문자열), 페이지 응답(`PageResponse`: content·page·size·totalElements·totalPages). 날짜는 `OffsetDateTime`이라 ISO 8601에 오프셋이 붙지만, 응답 시각은 JVM 기본 시간대(`ZoneId.systemDefault()` 등)를 따르고 운영 컨테이너에 TZ 설정이 없어 항상 `+09:00`이라고 단정할 수 없다(UTC면 `Z`로 나옴). `Asia/Seoul`은 FDS 심야 판정에서만 쓴다.
- 운영 `https://api.j-bank.site/v3/api-docs`의 `servers`가 `http://api.j-bank.site`다(2026-10-02 조회). Caddy가 TLS를 끝내고 앱은 `X-Forwarded-Proto`를 믿지 않아 springdoc이 http 주소를 만든다. 그래서 https Swagger UI의 Try it out이 브라우저 mixed content 차단으로 지금도 호출되지 않는다(계획 리뷰 1회차 F-2). 앱 코드는 요청 스킴·클라이언트 IP를 쓰지 않는다(grep 0건). 하지만 프레임워크의 CORS 처리는 요청 스킴을 쓴다(계획 리뷰 2회차 P2-1).
- 운영 CORS 차단(2026-10-02 확인): `POST https://api.j-bank.site/api/v1/auth/login`에 빈 본문과 `Origin: https://api.j-bank.site`를 보내면 403 `Invalid CORS request`, Origin 없이 보내면 400 `COMMON_001_VALIDATION_FAILED`. 브라우저는 같은 출처라도 POST·PATCH·DELETE에 Origin을 붙이고, 앱은 요청을 http로 보므로 https Origin을 다른 출처로 판단한다. CORS 허용 출처는 `http://localhost:3000` 하나(`SecurityConfig` corsConfigurationSource, 주석상 로컬 개발용)라 거절한다. 그래서 `servers`만 고치면 운영 Swagger는 GET만 되고 로그인부터 막힌다. 운영 프론트는 Vercel 서버에서 프록시하므로 브라우저 Origin 문제가 없다. CORS 허용 출처를 검사하는 테스트는 없다.
- 같은 출처에서 Swagger로 로그인하면 Chrome·Firefox는 쿠키를 저장해 이후 요청에 붙인다(로컬 `http://localhost`도 Secure 쿠키 허용. Safari는 다를 수 있음). Swagger UI는 기본으로 Authorize 값을 새로고침 때 잊는다(`persistAuthorization` 기본 false).
질문하지 않은 이유: 아래 표의 네 가지를 물었다. 응답 봉투·금액·페이지 문구 유지, 날짜 문구를 "오프셋 포함"으로 정확히 하는 것, `Idempotency-Key` 안내 추가, 계약 스냅샷 재생성은 현재 동작·저장소 규칙을 그대로 따르는 것이라 묻지 않았다.

원문 요청의 첫 줄은 콜론 뒤에 적고, 길면 이어지는 줄에 계속 적는다. 개인정보·비밀값은 문서에 복사하지 않고 위치와 필요한 의미만 남긴다.

| 질문 ID | 결정 사항과 영향 | 사용자 답변·근거 | 상태 |
| --- | --- | --- | --- |
| Q-01 | Swagger 표시 버전: `v1` / 빌드 버전 연동 | 2026-10-02 사용자: `v1`(경로 접두사 `/api/v1`과 맞춤) | 해결 |
| Q-02 | Swagger에서 상태 변경 요청 호출 지원: Authorize로 `X-CSRF-TOKEN` 입력 / 설명 문구만 | 2026-10-02 사용자: Authorize 버튼 추가, 설명에 사용법 안내 | 해결 |
| Q-03 | 운영 Swagger 요청 주소가 http라 호출이 막힘: 프록시 헤더 신뢰 설정 / Swagger 요청 주소를 상대 경로 `/`로 고정 / 운영은 보기 전용 | 2026-10-02 사용자: 상대 경로 `/` 고정(문서 설정만, 서버 보안 규칙 변경 없음) | 해결 |
| Q-04 | Swagger 로그인→Authorize→POST 실제 확인 위치 | 2026-10-02 사용자: 로컬은 Claude가 테스트 계정으로, 운영은 배포 뒤 사용자가 본인 계정으로 확인 | 해결 |
| Q-05 | 운영 같은 출처 POST가 CORS 403으로 막힘(계획 리뷰 2회차 P2-1): 프록시 헤더 신뢰 / CORS 허용 출처에 `https://api.j-bank.site` 추가 / 운영은 GET만 | 2026-10-02 사용자: CORS 허용 출처 추가 | 해결 |

답변 후 재점검:
- Authorize 입력과 `servers: /`는 문서(OpenAPI) 쪽 선언이다. 서버 인증·CSRF 검증·프록시 설정은 바뀌지 않는다. 헤더를 모든 요청에 붙여도 GET·로그인·고객 등록은 검사하지 않으므로 해가 없다.
- Q-05로 바뀌는 보안 설정은 CORS 허용 출처 한 줄뿐이다. 더하는 출처 `https://api.j-bank.site`는 API 자신이고 그 출처의 페이지는 Swagger UI뿐이라, 다른 사이트에 쿠키 요청을 허용하는 것이 아니다. 앱이 요청을 http로 보는 원인(스킴 오인)은 그대로 남고, 운영 도메인이 바뀌면 이 값도 바꿔야 한다. 허용 메서드·헤더·자격 증명 설정은 그대로다. 그 밖의 출처(예: `https://evil.example`)는 계속 403이어야 한다(SEC-04 적용).
- 같은 출처 요청은 브라우저가 사전 요청(preflight)을 보내지 않으므로 허용 출처 추가만으로 Swagger의 POST가 통과한다. CSRF 검사는 그 뒤에 그대로 적용된다.
- `servers: /`로 고정하면 계약 스냅샷의 `servers`가 환경과 무관하게 `/`가 된다. 프론트 타입(`apps/frontend/types/api.ts`)은 openapi-typescript가 servers·info·securitySchemes를 보통 내보내지 않지만 재생성 결과로 확인하고, 바뀌면 같은 커밋에 넣는다.
- 재발급하면 CSRF 토큰이 바뀌므로 Authorize 값을 다시 넣어야 하고, 새로고침하면 Authorize 값이 사라진다고 안내한다.
- 로컬 확인용 테스트 계정은 로컬 DB에만 만들고 값은 기록에 남기지 않는다(비밀번호 등).

## 목표

Swagger 첫 화면의 설명·버전이 현재 서버 동작과 일치하고, 로컬·운영 Swagger에서 로그인 후 상태 변경 요청을 직접 호출할 수 있다.

## 범위

- `OpenApiConfig`: 버전 `v1`, 설명 문구 갱신, `servers` 상대 경로 `/`, `X-CSRF-TOKEN` 헤더 보안 스킴과 전역 적용, Swagger 사용법 문단
- `SecurityConfig` CORS 허용 출처에 `https://api.j-bank.site` 추가
- 단위 테스트, CORS 허용·거절 테스트
- 계약 스냅샷 `contracts/openapi/openapi.yaml` 재생성(프론트 타입 변화 확인)
- 로컬 Swagger 실제 호출 확인, 운영 반영 확인(문서 값·버튼은 Claude, 실제 호출은 사용자)

## 요구사항

| ID | 조건·입력 | 기대 동작·실패 조건 |
| --- | --- | --- |
| REQ-01 | `/v3/api-docs` 조회 | `info.version`이 `v1`. `W1`·`v0`가 남으면 실패 |
| REQ-02 | API 설명(인증) | "인증은 아직 없습니다"·"W3"·`customerId` 쿼리 파라미터 안내가 없다. 쿠키 인증(로그인이 `access_token`·`refresh_token`·`XSRF-TOKEN` 발급), 상태 변경 요청(POST·PATCH·DELETE)의 `X-CSRF-TOKEN` 헤더 규칙과 예외(로그인·고객 등록), 401 `COMMON_002_UNAUTHORIZED`·403 `COMMON_007_CSRF_TOKEN_INVALID`를 설명한다 |
| REQ-03 | API 설명(Swagger 사용법) | 로그인 → 응답의 `csrfToken`을 Authorize에 입력 → 호출 순서, 재발급 뒤와 새로고침 뒤 다시 입력해야 함을 설명한다 |
| REQ-04 | OpenAPI 보안 선언 | `components.securitySchemes`에 header `X-CSRF-TOKEN` apiKey 스킴이 있고 전역 `security`로 적용된다(Swagger UI에 Authorize 버튼 표시). 서버 인증·CSRF 동작은 변경 없음 |
| REQ-05 | API 설명(그 밖의 공통 규칙) | 응답 봉투(`success`·`data`·`error`), 금액 문자열, 페이지 응답(`totalElements` 등) 문구 유지. 날짜는 "ISO 8601, 시간대 오프셋 포함"(예: `Z`, `+09:00`)으로 쓰고 특정 오프셋을 항상 쓴다고 단정하지 않는다. 이체·입금·출금의 `Idempotency-Key` 필수와 같은 키 재요청 멱등 처리를 안내한다 |
| REQ-06 | OpenAPI `servers` | `servers`가 상대 경로 `/` 하나다. `http://`로 시작하는 서버 주소가 남으면 실패 |
| REQ-07 | 계약 스냅샷 | `contracts/openapi/openapi.yaml`이 local 프로필로 기동한 API의 `/v3/api-docs.yaml`과 `diff` 없이 같다. `apps/frontend/types/api.ts`는 재생성 결과와 같다(바뀌면 함께 커밋) |
| REQ-08 | 로컬 Swagger 실제 호출(브라우저) | `http://localhost:8080/swagger-ui.html`에서 테스트 고객 등록(대면 확인 등 `eddRequired=false`가 되는 값)·로그인 → Authorize 없이 상태 변경 요청(`POST /api/v1/accounts` 계좌 개설, `initialDeposit: "0"`) 403 `COMMON_007` → 응답의 `csrfToken`을 Authorize에 넣고 같은 요청 2xx. 요청이 `http://localhost:8080` 같은 출처로 나간다 |
| REQ-10 | `Origin` 헤더가 붙은 상태 변경 요청 | `Origin: https://api.j-bank.site`·`http://localhost:3000`은 CORS로 거절되지 않는다(403 `Invalid CORS request` 아님). 그 밖의 출처(예: `https://evil.example`)는 403 `Invalid CORS request`. 허용 메서드·헤더·자격 증명 설정은 그대로 |
| REQ-09 | 운영 배포 뒤 | 운영 `https://api.j-bank.site/v3/api-docs`가 REQ-01·02·04·06을 만족하고, Swagger UI 화면에 Authorize 버튼이 보이며 Try it out 요청 주소가 `https://api.j-bank.site`다(Claude 확인, 로그인 없음). 빈 본문 로그인 요청에 `Origin: https://api.j-bank.site`를 붙이면 403 `Invalid CORS request`가 아니라 400 `COMMON_001_VALIDATION_FAILED`(Claude 확인, 인증 시도 아님). 사용자가 본인 계정으로 로그인→Authorize→상태 변경 요청을 확인한다 |

## 완료 기준

- AC-01(REQ-01~06): `OpenApiConfig` 단위 테스트가 버전, 설명의 필수 문구 포함·오래된 문구 미포함, 보안 스킴(타입 apiKey·위치 header·헤더명)과 전역 적용, `servers` 상대 경로를 확인
- AC-02(REQ-04·10): 기존 CSRF·인증 테스트(`CsrfDoubleSubmitFilterTest`, `FullFlowIntegrationTest` 등) 포함 백엔드 전체 테스트 통과. 보안 설정 변경은 `SecurityConfig`의 CORS 허용 출처 한 줄뿐이고 필터·쿠키·인가 규칙 변경 없음을 diff로 확인. CORS 테스트(MockMvc, 실제 `SecurityConfig`): 로그인 POST에 `Origin: https://api.j-bank.site`·`http://localhost:3000`이면 CORS 거절 없음, `https://evil.example`이면 403
- AC-02 판정 보조: 로컬 Swagger(REQ-08)는 같은 출처 `http://localhost:8080`이라 CORS 변경과 무관하게 통과하므로, 운영 CORS 경로는 AC-02 테스트와 AC-05 운영 확인으로 본다
- AC-03(REQ-07): 각 API 커밋에서 local 프로필로 API를 기동해 `/v3/api-docs.yaml`과 스냅샷 `diff` 결과(빈 출력), `api.ts` 재생성 diff를 logs/contract-check.txt에 저장. PR의 backend-ci `openapi-drift` 성공
- AC-04(REQ-08): 로컬 앱(local 프로필, 로컬 compose의 postgres·redis)을 띄우고 내장 브라우저로 Swagger에서 REQ-08 절차 수행. 단계별 응답 코드·오류 코드·요청 URL을 logs/local-swagger.md에 기록(비밀번호·토큰 값은 기록하지 않음)
- AC-05(REQ-09): 배포 뒤 공개 `/v3/api-docs` curl 결과, 빈 본문 로그인 요청의 Origin 유무별 응답, 내장 브라우저로 본 운영 Swagger UI(로그인하지 않음)의 Authorize 버튼·요청 주소, 사용자의 실제 호출 확인 응답을 logs/prod-check.md에 기록
- 하네스 verify 통과

## 하지 않을 일

- 서버 인증·CSRF·쿠키 규칙, 프록시 헤더 신뢰 설정(`server.forward-headers-strategy`) 변경(CORS 허용 출처 추가 외)
- 각 API 작업(operation)별 설명 전면 정비
- 운영 Swagger 노출 여부 변경(현재 공개 유지)
- API설계 문서(docs/03) 수정(이미 쿠키 인증 기준으로 맞음)
- `persistAuthorization` 등 Swagger UI 동작 설정 변경(안내 문구로 대신함)

## 커밋 계획

1. `fix(api)`: Swagger 설명·버전을 현재 인증·공통 규칙에 맞게 수정 (OpenApiConfig, 단위 테스트, 계약 스냅샷)
2. `fix(api)`: Swagger 요청 주소를 상대 경로로 고정해 https 운영의 mixed content 차단 해소 (OpenApiConfig, 단위 테스트, 계약 스냅샷)
3. `fix(api)`: 운영 Swagger 출처를 CORS 허용 목록에 추가 (SecurityConfig, CORS 테스트. 계약 변화 없음)
4. `feat(api)`: Swagger Authorize로 X-CSRF-TOKEN 헤더 입력 지원과 사용법 안내 (OpenApiConfig, 단위 테스트, 계약 스냅샷)
5. `docs(devlog)`: 개발일지
6. `chore(harness)`: 작업 기록

병합 뒤 후속 PR(운영 확인):
7. `chore(harness)`: 운영 반영 확인·완료 기록

각 API 커밋은 빌드·테스트·드리프트 검사가 통과하는 상태다(스냅샷을 같은 커밋에 넣음). 프론트 타입이 바뀌면 해당 커밋에 함께 넣는다.

## 적용 영역과 상세 기준

- 프론트: 해당 없음(앱 화면 변경 없음). 프론트 타입은 계약 재생성 결과로만 확인.
- 백엔드·데이터: 적용(API 문서 계약).
- AI: 해당 없음.
- DevOps: 적용(OPS-02: 파이프라인은 바꾸지 않고 기존 CI 계약 드리프트 검사를 통과). 배포 방식 변경은 없음.
- 보안: SEC-04 적용(CORS 허용 출처 추가. 허용·거절 사례 테스트, 쿠키·CSRF·프록시 설정은 변경 없음). 비밀값을 문서·기록에 넣지 않음.
- 성능: 해당 없음.
- 전체 흐름: E2E-02(로컬·운영 실제 연결)·E2E-06(회귀) 적용.

| 기준 ID | 완료 기준·시나리오 | 기대 결과·수치 목표 | 실행 명령 | 시점 | 증거 위치 |
| --- | --- | --- | --- | --- | --- |
| BE-02 | AC-01: API 문서 계약(버전·설명·보안 스킴·servers) | 단위 테스트 통과 | `apps/jbank-api/gradlew -p apps/jbank-api test spotlessCheck` (verify) | 구현 후 | verify 증거 |
| OPS-02 | AC-03: 기존 CI 계약 드리프트 검사 통과(파이프라인 변경 없음) | 로컬 diff 빈 출력, PR의 backend-ci `openapi-drift` 성공 | local 기동 후 `curl localhost:8080/v3/api-docs.yaml` + `diff -u`, GitHub Actions | 구현 후·PR | logs/contract-check.txt, PR 체크 |
| SEC-04 | AC-02·05: CORS 허용 출처 추가와 쿠키·CSRF 회귀 | 허용 출처 2개는 CORS 거절 없음, 그 밖의 출처 403, 기존 CSRF·인증 테스트 통과, `SecurityConfig` diff가 허용 출처 한 줄뿐. 운영에서 Origin 붙은 빈 본문 로그인 400 | verify(백엔드), `git diff`, 배포 후 curl | 구현 후·배포 후 | verify 증거, logs/prod-check.md |
| E2E-02 | AC-04·05: 로컬 Swagger 실제 호출(Authorize 없이 403, 있으면 2xx), 운영 api-docs·Swagger UI 확인과 사용자 실제 호출 | REQ-08·09 기대값 | 내장 브라우저(localhost·운영), `curl https://api.j-bank.site/v3/api-docs` | 구현 후·배포 후 | logs/local-swagger.md, logs/prod-check.md |
| E2E-06 | AC-02: 회귀 | FullFlowIntegrationTest 통과 | verify(백엔드) | 구현 후 | verify 증거 |
| BE-01·03~08 | 해당 없음: 업무 규칙·DB·동시성·마이그레이션·외부 연동·비동기·권한 변경 없음 | - | - | - | - |
| SEC-01~03·05~08 | 해당 없음: 인증·권한·입력·비밀·의존성·전송·남용 동작 변경 없음 | - | - | - | - |
| FE-01~07 | 해당 없음: 프론트 화면 변경 없음(기존 검사는 verify로 회귀만) | - | - | - | - |
| OPS-01·03~08 | 해당 없음: 빌드·설정·기동·배포·리소스·관측·백업 변경 없음 | - | - | - | - |
| E2E-01·03~05 | 해당 없음: 사용자 기능 흐름 변경 없음 | - | - | - | - |

## 일반 테스트 방법

| 완료 기준 | 시나리오·예상 결과 | 테스트 명령·근거 위치 | 실행 시점 |
| --- | --- | --- | --- |
| AC-01 | `OpenApiConfigTest`: version `v1`. 설명에 `access_token`·`refresh_token`·`XSRF-TOKEN`·`X-CSRF-TOKEN`·`POST`·`PATCH`·`DELETE`·`/api/v1/customers`·`/api/v1/auth/refresh`·`csrfToken`·`Authorize`·`새로고침`·`COMMON_002_UNAUTHORIZED`·`COMMON_007_CSRF_TOKEN_INVALID`·`Idempotency-Key`·`"success"`·`totalElements`·`오프셋` 포함, `인증은 아직 없습니다`·`W3`·`쿼리 파라미터로` 미포함. 보안 스킴 apiKey·in header·name `X-CSRF-TOKEN`, 전역 security에 해당 스킴. servers는 url `/` 하나 | verify(백엔드) | 구현 후 |
| AC-02 | CORS 테스트(로그인 POST: `https://api.j-bank.site`·`http://localhost:3000` 허용, `https://evil.example` 403), 백엔드 전체 테스트 통과, `SecurityConfig` diff 확인 | verify(백엔드), `git diff` | 구현 후 |
| AC-03 | 커밋별 스냅샷 diff 빈 출력, api.ts 재생성 결과 기록 | logs/contract-check.txt, PR `openapi-drift` | 구현 후·PR |
| AC-04 | 로컬 Swagger: 403 COMMON_007 → Authorize 후 2xx, 요청 URL이 localhost | logs/local-swagger.md | 구현 후 |
| AC-05 | 운영 api-docs·Swagger UI 확인, 사용자 실제 호출 확인 | logs/prod-check.md | 배포 후 |
| 회귀 | 하네스 전체 검사 | `python3 .claude/hooks/workflow.py verify` | 구현 후 |

## 성능 테스트

- 필요 여부와 이유: 불필요. API 문서 설정만 바뀌고 요청 처리 경로 변경이 없다.

## 추가 맥락

- 관련 문서: docs/03_J-Bank_API설계.md 2.2절(쿠키 인증)·2.6절(날짜·금액)·2.8절(CSRF)·10절(계약 스냅샷), docs/10_J-Bank_폴더구조.md(contracts).
