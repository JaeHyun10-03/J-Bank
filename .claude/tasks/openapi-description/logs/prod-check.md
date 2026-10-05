# 운영 반영 확인 (AC-05·REQ-09·REQ-07 PR CI·SEC-04·E2E-02)

## PR CI와 배포

- PR #14 체크(`gh pr checks 14`): `build` pass, `openapi-drift` pass(1m37s, Actions run 36974450740). 계약 스냅샷이 CI 기동 API와 같다(REQ-07·OPS-02).
- 병합: PR #13(d57a9f4, 06:39:09Z), PR #14(79d5ca1, 06:46:19Z). backend-cd(79d5ca1) 06:46:22Z 시작, 성공. 인스턴스가 켜진 시간이라 배포 실행.

## 문서 값 (로컬 curl, 미인증 GET, 06:50Z)

`curl -s https://api.j-bank.site/v3/api-docs`

| 항목 | 결과 |
| --- | --- |
| `info.version` | `v1` |
| `servers` | `[{"url": "/"}]` (`http://` 서버 주소 없음) |
| `security` | `[{"X-CSRF-TOKEN": []}]` |
| `components.securitySchemes.X-CSRF-TOKEN` | type apiKey, in header, name `X-CSRF-TOKEN` |
| 오래된 문구(`인증은 아직 없습니다`·`W3`·`쿼리 파라미터로`) | 없음 |
| 필수 문구(`access_token`·`X-CSRF-TOKEN`·`Authorize`·`csrfToken`) | 모두 있음 |

## CORS (로컬 curl, 빈 본문 로그인 POST, 인증 시도 아님, 06:50Z)

`curl -X POST https://api.j-bank.site/api/v1/auth/login -H '<Origin>' -H 'Content-Type: application/json' -d '{}'`

| Origin | 결과 | 변경 전(2026-10-02 계획 시) |
| --- | --- | --- |
| `https://api.j-bank.site` | 400 `COMMON_001_VALIDATION_FAILED` | 403 `Invalid CORS request` |
| `https://evil.example` | 403 `Invalid CORS request` | (같음) |
| 없음 | 400 `COMMON_001_VALIDATION_FAILED` | 400 |

## Swagger UI 화면 (Claude 내장 브라우저, 로그인하지 않음, 06:51Z)

- `https://api.j-bank.site/swagger-ui.html` → `https://api.j-bank.site/swagger-ui/index.html`. 버전 v1, 설명 1~8번, Servers `/`, Authorize 버튼 있음.
- `/`는 명세 기준으로 `https://api.j-bank.site/`로 해석된다.
- Try it out으로 `GET /api/v1/products`를 실행했다. Request URL이 `https://api.j-bank.site/api/v1/products?page=0&size=1&sort=%5B%22string%22%5D`로 https 같은 출처로 나갔고, 브라우저 차단 없이 응답을 받았다(mixed content 해소 확인).
- 그 응답은 500 `COMMON_006_INTERNAL_ERROR`였다. Swagger가 기본으로 채운 `sort=["string"]`(없는 정렬 필드)를 서버가 400이 아니라 500으로 처리하는 기존 결함이다. 이번 변경과 무관하며, 잘못된 JSON 본문 500(logs/local-swagger.md)과 같은 종류라 후속 작업으로 함께 분리했다.

## 사용자 실제 호출 확인

2026-10-02 08:08~08:13Z, 사용자가 본인 브라우저로 운영 Swagger(`https://api.j-bank.site/swagger-ui.html`)에서 실행하고 결과를 대화로 전달했다.

| 단계 | 요청 | 결과(사용자 전달) |
| --- | --- | --- |
| 가입 | `POST /api/v1/customers` | 201, `customerId: 1`, `status: ACTIVE`. Swagger 예시 본문이 그대로 실행돼 아이디가 예시값인 테스트 고객이 생김(SSM 읽기 전용 조회로 확인). 운영 Swagger에서 POST가 실제로 통과한 첫 기록 |
| 로그인(잘못된 아이디) | `POST /api/v1/auth/login` 사용자가 가입하려던 아이디(가입되지 않음) | 401 `AUTH_001_INVALID_CREDENTIALS`(해당 아이디 없음. 실패 카운터 미생성으로 확인) |
| 로그인 | `POST /api/v1/auth/login` 위 가입에 쓰인 Swagger 예시 본문의 아이디·비밀번호(값은 기록하지 않음) | 200, `csrfToken` 발급, `accessTokenExpiresAt`이 `...Z`(운영 JVM UTC) |
| Authorize 뒤 로그아웃 | `POST /api/v1/auth/logout` | 204. Swagger curl에 `-H 'X-CSRF-TOKEN: …'` 자동 첨부, Request URL `https://api.j-bank.site/api/v1/auth/logout`, 응답 헤더 `access-control-allow-origin: https://api.j-bank.site`·`access-control-allow-credentials: true`, `via: 1.1 Caddy` |

- Authorize 전 로그아웃 403 비교 단계는 사용자가 결과를 따로 전달하지 않았다(로컬 확인 logs/local-swagger.md 3단계로 대신함).
- 토큰·비밀번호 값은 기록하지 않음(결과 리뷰 2회차 C2-1로 처음 기록의 비밀번호 표기를 지움).
- 이 테스트 고객은 비밀번호가 Swagger 기본 예시값이라 추측 가능하다. 처리(비활성화·삭제·유지)는 사용자 결정 사항으로 남김.

## 확인 중 발견(범위 밖, 후속 작업)

- 운영 프론트(www.j-bank.site)의 같은 출처 프록시(`apps/frontend/app/api/proxy/[...path]/route.ts`)가 브라우저의 `Origin: https://www.j-bank.site` 헤더를 백엔드로 그대로 넘긴다. 백엔드 CORS 허용 목록에 없어 프론트의 모든 POST·PATCH·DELETE(가입·로그인·이체 등)가 403 `Invalid CORS request`. 재현: `POST https://www.j-bank.site/api/proxy/api/v1/auth/login`에 Origin 있으면 403, 없으면 400. 운영 고객이 0명이었던 원인. 사용자 결정(프록시에서 Origin 제거)으로 다음 작업에서 고친다.
