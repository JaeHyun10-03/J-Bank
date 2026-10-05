# AC-04 / REQ-08 / E2E-02 로컬 Swagger 실제 호출 (2026-10-02 06:25~06:29Z)

- 대상: 커밋 4(15900e7) 코드를 local 프로필로 기동. 로컬 compose의 postgres·redis 사용.
- 포트: 8080은 다른 로컬 프로젝트가 쓰고 있어 `--server.port=18080`으로 띄웠다. Swagger UI와 API가 같은 출처(`http://localhost:18080`)라는 조건은 REQ-08과 같다.
- 도구: Claude 내장 브라우저로 `http://localhost:18080/swagger-ui.html`을 열고 Swagger UI의 Try it out·Authorize로 실행. 본문 입력은 텍스트 영역에 입력 이벤트로 값을 넣었다(키 입력으로 기존 예시가 지워지지 않아서).
- 테스트 계정: 로컬 DB에만 만든 테스트 고객(loginId `swaggertest01`). 비밀번호·토큰 값은 기록하지 않음.

| 단계 | Swagger 요청 | 요청 URL | 응답 |
| --- | --- | --- | --- |
| 화면 | `/swagger-ui.html` | - | 설명 1~8번, 버전 v1, Servers `/`, Authorize 버튼 표시 |
| 1 | `POST /api/v1/customers`(대면 확인 `FACE_TO_FACE`) | `http://localhost:18080/api/v1/customers` | 201, `eddRequired: false`, status ACTIVE |
| 2 | `POST /api/v1/auth/login` | `http://localhost:18080/api/v1/auth/login` | 200, 응답에 `csrfToken`, 브라우저에 쿠키 저장(스크립트로 보이는 `XSRF-TOKEN` 확인, HttpOnly 쿠키는 스크립트로 안 보임) |
| 3 | Authorize 없이 `POST /api/v1/accounts` `{"productType":"CHECKING","initialDeposit":"0"}` | `http://localhost:18080/api/v1/accounts` | 403 `COMMON_007_CSRF_TOKEN_INVALID`. curl에 `X-CSRF-TOKEN` 헤더 없음 |
| 4 | Authorize의 `X-CSRF-TOKEN`에 2단계 `csrfToken` 입력 → Authorize(자물쇠 잠김 표시) | - | - |
| 5 | 같은 `POST /api/v1/accounts` | `http://localhost:18080/api/v1/accounts` | 201, 계좌 개설(`status: ACTIVE`). curl에 `-H 'X-CSRF-TOKEN: …'`가 자동으로 붙음 |

참고: 5단계 응답의 `openedAt`은 `+09:00`으로 나왔다(로컬 JVM 시간대가 KST). 운영 컨테이너는 TZ 설정이 없어 다를 수 있다는 task.md 판단과 맞는다.

## 작업 중 발견(범위 밖)

- 잘못된 JSON 본문을 보내면 500 `COMMON_006_INTERNAL_ERROR`가 난다(1·2단계에서 본문 입력이 깨졌을 때 재현). `GlobalExceptionHandler`가 `HttpMessageNotReadableException`을 따로 처리하지 않아 `Exception` 처리기로 떨어지는 것으로 보이며, 서버 로그에도 남지 않는다. 이번 작업 범위가 아니라 후속 작업으로 남긴다.
