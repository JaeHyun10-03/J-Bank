# J-Bank 코어시스템 API 상세 설계

## 버전 이력

| 버전 | 일자 | 변경 내용 |
|---|---|---|
| v1.0 | 2026-07-21 | 최초 작성 - 요구사항명세서 6절 API 개요를 전체 엔드포인트 단위로 상세화, 인증/상품/운영자 조회 엔드포인트 신규 추가 |
| v1.1 | 2026-07-26 | 토큰 발급 방식을 응답 본문에서 Set-Cookie 헤더로 전환(API-011, API-014, API-015). 2.2절 인증 규칙 재작성, 2.8절 상태 변경 요청 위조 방지 규칙 신설, 공통 에러코드에 COMMON_007 추가 |

## 관련 문서

- J-Bank_요구사항명세서.md
- J-Bank_ERD.md
- J-Bank_인프라아키텍처.md
- J-Bank_프론트엔드기술스택.md
- J-Bank_구현계획.md

---

## 1. 문서 개요

요구사항명세서 6절은 기능과 엔드포인트를 1대1로 매핑한 개요 수준의 표였다. 이 문서는 그 표를 실제 구현 가능한 수준까지 내려서, 각 엔드포인트의 요청/응답 스키마, HTTP 상태코드, 도메인 에러코드, 인증 요건을 정의한다.

상세 설계 과정에서 개요 단계에 없던 엔드포인트 몇 개가 추가로 필요하다는 것이 드러났다. 토큰 재발급과 로그아웃, 2차 인증 검증, 계좌 목록 조회, 상품 목록 조회, 운영자용 조회 API가 그것이다. 이 엔드포인트들은 API-014부터 새 번호를 부여했고, 요구사항명세서 6절에도 동일하게 반영해 두 문서가 어긋나지 않도록 했다.

v1.1에서는 인증 방식을 보정했다. v1.0은 토큰을 응답 본문에 JSON으로 내려주고 클라이언트가 저장 위치를 결정하도록 설계했는데, 프론트엔드기술스택 문서 7절이 두 토큰 모두 httpOnly 쿠키로 관리하기로 결론을 내렸다. 두 문서가 어긋난 상태로 구현에 들어가면 프론트엔드가 존재하지 않는 응답 필드를 읽게 되므로, 이 문서를 쿠키 발급 방식으로 맞추고 그에 따라 새로 필요해진 요청 위조 방지 규칙을 2.8절에 추가했다.

## 2. 공통 설계 규칙

### 2.1 기본 URL과 버전 관리

Base URL은 `https://api.j-bank.internal/api/v1` 이다. 버전은 URI 경로에 명시하는 방식을 택했다. 헤더 기반 버전 관리보다 캐시나 로그에서 버전을 구분하기 쉽고, API 게이트웨이 라우팅 규칙을 단순하게 유지할 수 있기 때문이다.

### 2.2 인증

인증은 쿠키 기반이다. 고객용 엔드포인트는 API-011 로그인 시점에 서버가 발급한 쿠키를 브라우저가 자동으로 실어 보내는 것으로 인증을 통과한다. 클라이언트가 `Authorization` 헤더를 직접 구성하지 않는다.

발급되는 쿠키는 세 종류다.

| 쿠키 | 수명 | 속성 | 용도 |
|---|---|---|---|
| `access_token` | 15분 | HttpOnly, Secure, SameSite=Lax, Path=/ | 요청 인증 |
| `refresh_token` | 14일 | HttpOnly, Secure, SameSite=Lax, Path=/ | 만료된 인증 쿠키 재발급 |
| `XSRF-TOKEN` | 14일 | Secure, SameSite=Lax, Path=/ | 상태 변경 요청 위조 방지, 2.8절 |

앞의 두 개는 HttpOnly이므로 자바스크립트가 값을 읽을 수 없다. 세 번째는 클라이언트가 읽어 요청 헤더에 복사해야 하므로 의도적으로 HttpOnly를 붙이지 않는다.

토큰 자체를 클라이언트에 노출하지 않는 방식을 택한 이유는 두 가지다. 첫째, 스크립트 주입 공격이 성공했을 때 메모리나 로컬 저장소에 있는 값은 그대로 읽히지만 HttpOnly 쿠키는 브라우저가 접근 자체를 차단한다. 둘째, 인증 쿠키가 브라우저에 있으면 프론트엔드의 서버 사이드 렌더링과 미들웨어가 요청 단계에서 인증 상태를 직접 확인할 수 있다. 값을 클라이언트 메모리에만 두면 새로고침 시점에 서버가 그 값에 접근할 방법이 없다.

세 쿠키 모두 `Path=/`로 발급한다. 갱신 쿠키의 경로를 인증 엔드포인트로 좁히는 것이 더 안전하지만, 배포 구조상 브라우저가 실제로 호출하는 경로는 프론트엔드 도메인의 프록시 경로이고 백엔드의 엔드포인트 경로와 일치하지 않는다. 경로를 좁히면 프록시가 그 값을 매번 재작성해야 하고, 재작성 규칙이 어긋나면 갱신이 조용히 실패한다. 경로 제한을 포기하는 대신 갱신 쿠키에 로테이션과 재사용 탐지를 적용해 위험을 낮춘다.

프론트엔드 프록시는 백엔드가 내려준 `Set-Cookie` 헤더를 브라우저로 중계할 때 `Domain` 속성을 제거하고 `Path`를 `/`로 정규화한다. 백엔드 도메인이 그대로 남으면 브라우저가 다른 사이트의 쿠키로 판단해 저장하지 않는다.

운영자용 엔드포인트(`/api/v1/admin/**`)는 운영자 역할(`ROLE_OPERATOR`)이 부여된 계정으로만 접근 가능하며, Spring Security의 `@PreAuthorize`로 역할 검증을 강제한다.

### 2.3 Idempotency-Key

금전 이동을 유발하는 API, 즉 입금, 출금, 이체는 `Idempotency-Key` 헤더를 필수로 요구한다. 클라이언트가 생성한 UUID를 헤더에 담아 전달하면, 서버는 동일 키로 재요청이 들어왔을 때 원래 처리 결과를 그대로 반환하고 중복 처리를 하지 않는다. 키는 24시간 동안 유효한 것으로 관리하며, 만료 후 동일 키가 재사용되면 신규 요청으로 처리한다.

키는 화면 진입 시점이 아니라 제출 버튼을 누르는 시점에 생성한다. 미리 만들어두면 사용자가 금액을 고쳐 입력한 뒤 제출해도 같은 키가 재사용되어 최초 입력값 기준으로 멱등 처리가 꼬인다.

### 2.4 공통 응답 포맷

성공 응답과 실패 응답 모두 동일한 봉투 구조를 사용한다.

성공 시:
```json
{
  "success": true,
  "data": { },
  "error": null
}
```

실패 시:
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "TXN_001_INSUFFICIENT_BALANCE",
    "message": "출금 가능 금액을 초과했습니다."
  }
}
```

`data` 필드의 JSON 키는 카멜케이스를 사용한다. 데이터베이스 컬럼은 ERD 문서에 정의된 스네이크케이스를 그대로 쓰지만, JPA/Jackson 계층에서 API 응답 시점에 카멜케이스로 자동 변환되는 것을 전제로 한다.

### 2.5 페이지네이션

목록 조회 API는 `page`(0부터 시작), `size`(기본값 20, 최대 100), `sort` 쿼리 파라미터를 공통으로 받는다. 응답은 다음 구조로 감싼다.

```json
{
  "success": true,
  "data": {
    "content": [ ],
    "page": 0,
    "size": 20,
    "totalElements": 137,
    "totalPages": 7
  },
  "error": null
}
```

### 2.6 날짜와 금액 표현

날짜와 시간은 ISO 8601(`2026-07-21T09:30:00+09:00`)로 표현한다. 금액은 JSON에서 부동소수점 오차를 피하기 위해 문자열로 표현하며, 서버 내부에서는 `NUMERIC(19,2)`로 정확히 계산한 값을 문자열로 직렬화한다.

### 2.7 공통 에러코드

| 코드 | HTTP 상태 | 설명 |
|---|---|---|
| COMMON_001_VALIDATION_FAILED | 400 | 요청 필드 형식 또는 필수값 검증 실패 |
| COMMON_002_UNAUTHORIZED | 401 | 인증 쿠키 누락 또는 만료 |
| COMMON_003_FORBIDDEN | 403 | 인증은 되었으나 해당 자원에 대한 권한 없음 |
| COMMON_004_NOT_FOUND | 404 | 요청한 자원이 존재하지 않음 |
| COMMON_005_CONFLICT | 409 | 상태 충돌(중복 요청, 동시성 충돌 등) |
| COMMON_006_INTERNAL_ERROR | 500 | 서버 내부 오류 |
| COMMON_007_CSRF_TOKEN_INVALID | 403 | 위조 방지 토큰 누락 또는 쿠키 값과 헤더 값 불일치 |

도메인별 에러코드는 각 절에서 개별적으로 정의한다.

### 2.8 상태 변경 요청의 위조 방지

쿠키 기반 인증으로 전환하면서 새로 관리해야 하는 위험이 요청 위조다. 브라우저는 쿠키를 요청마다 자동으로 실어 보내므로, 다른 사이트가 사용자 브라우저를 통해 이체 요청을 몰래 발생시킬 수 있다. 토큰을 헤더로 직접 실어 보내던 v1.0 설계에는 없던 문제다.

방어는 두 겹이다.

첫째는 `SameSite=Lax`다. 이 속성이 붙은 쿠키는 다른 사이트에서 시작된 POST 요청에 실리지 않는다. 상태를 바꾸는 모든 엔드포인트가 POST, PATCH, DELETE이므로 이 설정만으로도 대부분의 교차 사이트 요청이 차단된다.

둘째는 쿠키와 헤더를 동시에 제출하는 방식이다. 로그인 시 서버가 무작위 값을 생성해 `XSRF-TOKEN` 쿠키로 내려주고, 클라이언트는 그 값을 읽어 `X-CSRF-TOKEN` 헤더에 담아 보낸다. 서버는 두 값의 일치를 검증한다.

이 방식이 유효한 이유는 공격자가 쿠키를 요청에 실리게 만들 수는 있어도 그 값을 읽을 수는 없다는 점에 있다. 다른 사이트의 스크립트는 브라우저의 출처 정책 때문에 우리 도메인의 쿠키를 읽지 못하므로, 헤더에 올바른 값을 채워 넣을 수 없다. 서버가 세션별 토큰을 따로 저장하지 않아도 되는 것이 이 방식의 실무적 이점이다.

적용 대상은 다음과 같다.

| 구분 | 대상 | 검증 |
|---|---|---|
| 검증 생략 | GET 요청 전체 | 상태를 바꾸지 않음 |
| 검증 생략 | API-011 로그인 | 발급 시점이므로 아직 토큰이 없음 |
| 검증 수행 | 그 외 POST, PATCH, DELETE 전체 | 쿠키와 헤더 값 일치 |

갱신 요청(API-014)도 검증 대상에 포함한다. 공격자가 갱신을 강제로 유발해서 얻을 정보는 없지만, 갱신마다 토큰이 교체되므로 반복 유발로 사용자를 로그아웃시킬 수 있다. `XSRF-TOKEN`의 수명을 갱신 쿠키와 같게 두어 인증 쿠키가 만료된 상태에서도 검증이 성립하게 한다.

검증에 실패하면 `COMMON_007_CSRF_TOKEN_INVALID`를 403으로 반환한다. 이 검증은 전역 필터에서 처리하므로 개별 엔드포인트 명세에는 반복해서 표기하지 않는다.

## 3. API 목록 요약표

| API ID | Method | Endpoint | 설명 | 관련 FR | 인증 |
|---|---|---|---|---|---|
| API-001 | POST | /customers | 고객 등록 및 CDD | FR-ACC-001 | 불필요 |
| API-002 | POST | /accounts | 계좌 개설 | FR-ACC-002 | 필요 |
| API-003 | GET | /accounts/{accountId} | 계좌 상세 조회 | FR-ACC-005 | 필요 |
| API-004 | PATCH | /accounts/{accountId}/status | 계좌 상태 변경 | FR-ACC-003 | 필요, 운영자 |
| API-005 | DELETE | /accounts/{accountId} | 계좌 해지 | FR-ACC-004 | 필요 |
| API-006 | POST | /accounts/{accountId}/deposit | 입금 처리 | FR-TXN-001 | 필요 |
| API-007 | POST | /accounts/{accountId}/withdraw | 출금 처리 | FR-TXN-002 | 필요 |
| API-008 | POST | /transfers | 계좌이체 | FR-TXN-003 | 필요 |
| API-009 | GET | /accounts/{accountId}/balance | 잔액 조회 | FR-TXN-004 | 필요 |
| API-010 | GET | /accounts/{accountId}/transactions | 거래내역 조회 | FR-TXN-005 | 필요 |
| API-011 | POST | /auth/login | 로그인 | FR-AUTH-001 | 불필요 |
| API-012 | POST | /products/{productCode}/subscriptions | 상품 가입 | FR-PRD-001 | 필요 |
| API-013 | POST | /customers/{customerId}/edd | 강화된 고객확인 등록 | FR-ACC-006 | 필요, 운영자 |
| API-014 | POST | /auth/refresh | 토큰 재발급 | FR-AUTH-001 | 갱신 쿠키 |
| API-015 | POST | /auth/logout | 로그아웃 | FR-AUTH-001 | 필요 |
| API-016 | POST | /transfers/{transactionId}/otp-verifications | 고액이체 2차 인증 검증 | FR-AUTH-003 | 필요 |
| API-017 | GET | /customers/{customerId}/accounts | 고객별 계좌 목록 조회 | FR-ACC-005 | 필요 |
| API-018 | GET | /products | 상품 목록 조회 | FR-PRD-001 | 불필요 |
| API-019 | GET | /customers/{customerId}/contracts | 고객별 가입 계약 조회 | FR-PRD-001 | 필요 |
| API-020 | GET | /admin/audit-logs | 감사 로그 조회 | FR-SUP-001 | 필요, 운영자 |
| API-021 | GET | /admin/ctr-reports | 고액현금거래 보고대상 조회 | FR-SUP-004 | 필요, 운영자 |
| API-022 | GET | /admin/suspicious-transactions | 이상거래 탐지 결과 조회 | FR-SUP-003 | 필요, 운영자 |

이하 상세 명세에서 Endpoint는 `/api/v1` 접두어를 생략하고 표기한다.

## 4. 인증 API

### API-011 POST /auth/login

**설명**: 로그인ID와 비밀번호로 인증하고 인증 쿠키를 발급한다.

요청:
```json
{
  "loginId": "jungminsung01",
  "password": "plaintext-password"
}
```

응답(200) 헤더:
```
Set-Cookie: access_token=eyJhbGciOi...; HttpOnly; Secure; SameSite=Lax; Path=/; Max-Age=900
Set-Cookie: refresh_token=eyJhbGciOi...; HttpOnly; Secure; SameSite=Lax; Path=/; Max-Age=1209600
Set-Cookie: XSRF-TOKEN=8f2c4a91...; Secure; SameSite=Lax; Path=/; Max-Age=1209600
```

응답(200) 본문:
```json
{
  "success": true,
  "data": {
    "customerId": "CUST-000123",
    "name": "정민성",
    "accessTokenExpiresAt": "2026-07-21T09:45:00+09:00",
    "csrfToken": "8f2c4a91..."
  },
  "error": null
}
```

본문에는 토큰 값을 담지 않는다. 클라이언트가 필요한 것은 화면에 표시할 사용자 정보와, 인증이 언제 만료되는지 미리 알기 위한 만료 시각뿐이다. `csrfToken`은 쿠키를 읽을 수 없는 서버 사이드 렌더링 경로에서 첫 요청을 구성할 때 쓰라고 함께 내려주는 값이며, 쿠키에 실린 값과 동일하다.

| 코드 | HTTP 상태 | 설명 |
|---|---|---|
| AUTH_001_INVALID_CREDENTIALS | 401 | 로그인ID 또는 비밀번호 불일치 |
| AUTH_002_ACCOUNT_LOCKED | 423 | 비밀번호 5회 연속 실패로 잠긴 계정 |

비밀번호는 bcrypt로 해시 검증하며, 연속 실패 횟수는 Redis에 키 단위로 카운트하고 일정 시간 후 자동 만료시킨다.

### API-014 POST /auth/refresh

**설명**: 만료된 인증 쿠키를 갱신 쿠키로 재발급한다.

요청 본문은 없다. 갱신 쿠키가 요청에 자동으로 실리므로 클라이언트가 토큰 값을 다룰 필요가 없다. 요청 헤더에 `X-CSRF-TOKEN`은 필요하다(2.8절).

응답(200)은 API-011과 동일하게 세 쿠키를 모두 새 값으로 교체하고, 본문에는 갱신된 만료 시각과 새 위조 방지 토큰을 담는다.

```json
{
  "success": true,
  "data": {
    "accessTokenExpiresAt": "2026-07-21T10:00:00+09:00",
    "csrfToken": "b71d05e3..."
  },
  "error": null
}
```

갱신 쿠키는 재발급 시마다 새 값으로 교체하는 로테이션 방식을 적용한다. 발급된 토큰의 식별자는 Redis에 화이트리스트로 관리하고, 이미 사용된 토큰이 다시 들어오면 탈취를 의심할 근거로 보아 해당 사용자의 토큰 계열 전체를 폐기하고 재로그인을 요구한다.

| 코드 | HTTP 상태 | 설명 |
|---|---|---|
| AUTH_003_REFRESH_TOKEN_INVALID | 401 | 갱신 쿠키 만료, 위조 또는 이미 사용되어 폐기된 토큰 |

### API-015 POST /auth/logout

**설명**: 현재 세션의 갱신 토큰을 서버 측 화이트리스트에서 제거하고, 세 쿠키를 모두 만료시킨다.

응답(204) 헤더:
```
Set-Cookie: access_token=; HttpOnly; Secure; SameSite=Lax; Path=/; Max-Age=0
Set-Cookie: refresh_token=; HttpOnly; Secure; SameSite=Lax; Path=/; Max-Age=0
Set-Cookie: XSRF-TOKEN=; Secure; SameSite=Lax; Path=/; Max-Age=0
```

본문은 없다. 인증 쿠키는 자체 만료 시간이 남아 있어도 화이트리스트에서 제거된 계열이므로 갱신이 불가능하고, 남은 15분이 지나면 완전히 무효가 된다.

### API-016 POST /transfers/{transactionId}/otp-verifications

**설명**: FR-AUTH-003. 임계금액을 초과하는 이체 요청 시 API-008이 즉시 완료되지 않고 `PENDING_OTP` 상태로 대기하는데, 이 엔드포인트로 OTP를 검증해야 이체가 최종 실행된다.

요청:
```json
{
  "otpCode": "482910"
}
```

응답(200):
```json
{
  "success": true,
  "data": {
    "transactionId": "TXN-2026-0009983",
    "status": "COMPLETED",
    "processedAt": "2026-07-21T09:31:20+09:00"
  },
  "error": null
}
```

| 코드 | HTTP 상태 | 설명 |
|---|---|---|
| AUTH_004_OTP_MISMATCH | 400 | 입력한 OTP가 발급된 값과 다름 |
| AUTH_005_OTP_EXPIRED | 410 | OTP 유효시간(3분) 경과 |
| TXN_005_TRANSACTION_NOT_PENDING | 409 | 이미 완료되었거나 만료되어 취소된 거래 |

검증에 성공하면 대기 중이던 지급정지 금액이 실제 출금으로 확정된다. 실패 횟수가 한도를 넘거나 유효시간이 지나면 거래가 취소되고 지급정지가 해제된다. 지급정지 금액의 정의는 ERD 문서 2.2절에 있다.

## 5. 고객/계좌 API

### API-001 POST /customers

**설명**: FR-ACC-001. 신규 고객을 등록하고 CDD를 수행한다.

요청:
```json
{
  "name": "정민성",
  "residentRegNo": "900101-1******",
  "birthDate": "1990-01-01",
  "phone": "010-1234-5678",
  "address": "서울특별시 강남구 ...",
  "occupation": "회사원",
  "identityVerificationMethod": "NON_FACE_TO_FACE",
  "transactionPurpose": "생활자금 관리",
  "fundSource": "근로소득"
}
```

응답(201):
```json
{
  "success": true,
  "data": {
    "customerId": "CUST-000123",
    "kycGrade": "GENERAL",
    "amlRiskLevel": "LOW",
    "status": "ACTIVE"
  },
  "error": null
}
```

| 코드 | HTTP 상태 | 설명 |
|---|---|---|
| ACC_001_DUPLICATE_RESIDENT_REG_NO | 409 | 이미 등록된 실명번호로 재등록 시도(기존 CIF로 안내) |
| ACC_002_IDENTITY_VERIFICATION_FAILED | 422 | 실명확인 대조 실패 |
| COMMON_001_VALIDATION_FAILED | 400 | 필수 필드 누락 또는 실명번호 형식 오류 |

`amlRiskLevel`이 `HIGH`로 산정되면 `data.eddRequired`가 `true`로 함께 반환되며, 클라이언트는 API-013으로 EDD 절차를 이어가야 한다.

### API-013 POST /customers/{customerId}/edd

**설명**: FR-ACC-006. 고위험 고객의 거래목적과 자금원천을 추가로 확인한다.

요청:
```json
{
  "transactionPurpose": "해외거주 가족 생활비 송금",
  "fundSource": "부동산 임대소득",
  "supportingDocumentRef": "DOC-2026-004521"
}
```

응답(200):
```json
{
  "success": true,
  "data": {
    "customerId": "CUST-000123",
    "amlRiskLevel": "MEDIUM",
    "eddCompletedAt": "2026-07-21T10:00:00+09:00"
  },
  "error": null
}
```

| 코드 | HTTP 상태 | 설명 |
|---|---|---|
| ACC_003_EDD_EVIDENCE_INSUFFICIENT | 422 | 소명 자료 불충분으로 위험도 재평가 불가 |
| ACC_004_CUSTOMER_NOT_HIGH_RISK | 409 | 고위험으로 분류되지 않은 고객에 대한 EDD 요청 |

처리 결과는 CustomerRiskAssessmentHistory 테이블에 이력으로 적재되며, 이 API는 판정 이력을 갱신할 뿐 이전 이력을 덮어쓰지 않는다.

### API-002 POST /accounts

**설명**: FR-ACC-002. CDD를 완료한 고객이 신규 계좌를 개설한다.

요청:
```json
{
  "customerId": "CUST-000123",
  "productType": "DEMAND_DEPOSIT",
  "initialDeposit": "50000.00"
}
```

응답(201):
```json
{
  "success": true,
  "data": {
    "accountId": "ACCT-2026-000045",
    "accountNumber": "110-123-456789",
    "status": "ACTIVE",
    "openedAt": "2026-07-21T09:00:00+09:00"
  },
  "error": null
}
```

| 코드 | HTTP 상태 | 설명 |
|---|---|---|
| ACC_005_CDD_NOT_COMPLETED | 403 | CDD 미완료 고객의 계좌 개설 시도 |
| ACC_006_CUSTOMER_STATUS_INVALID | 409 | 정지 또는 해지 상태 고객 |

### API-003 GET /accounts/{accountId}

응답(200):
```json
{
  "success": true,
  "data": {
    "accountId": "ACCT-2026-000045",
    "accountNumber": "110-123-456789",
    "customerId": "CUST-000123",
    "productType": "DEMAND_DEPOSIT",
    "status": "ACTIVE",
    "openedAt": "2026-07-21T09:00:00+09:00",
    "closedAt": null
  },
  "error": null
}
```

요청자가 계좌 소유주 본인이 아니면 `COMMON_003_FORBIDDEN`을 반환한다. 이 검증은 FR-AUTH-002에서 공통으로 정의한 권한 검증 로직을 재사용한다.

### API-017 GET /customers/{customerId}/accounts

목록 조회 공통 규칙(2.5절)을 따르며, `status` 쿼리 파라미터로 활성 계좌만 필터링할 수 있다. 응답의 `content` 배열 원소는 API-003 응답과 동일한 형태이되 잔액 관련 필드를 함께 포함한다. 필드 구성은 API-009와 같다.

### API-004 PATCH /accounts/{accountId}/status

**설명**: FR-ACC-003. 운영자가 계좌 상태를 변경한다.

요청:
```json
{
  "targetStatus": "SUSPENDED",
  "reason": "이상거래 의심으로 임시 정지"
}
```

응답(200):
```json
{
  "success": true,
  "data": {
    "accountId": "ACCT-2026-000045",
    "previousStatus": "ACTIVE",
    "status": "SUSPENDED"
  },
  "error": null
}
```

| 코드 | HTTP 상태 | 설명 |
|---|---|---|
| ACC_007_INVALID_STATUS_TRANSITION | 409 | 상태 머신 규칙상 허용되지 않는 전이(예: 해지 계좌를 다시 활성화) |

상태 변경 사유는 AuditLog 테이블에 자동 적재된다.

### API-005 DELETE /accounts/{accountId}

**설명**: FR-ACC-004. 잔액이 0원인 계좌만 해지 가능하다.

응답(200):
```json
{
  "success": true,
  "data": {
    "accountId": "ACCT-2026-000045",
    "status": "CLOSED",
    "closedAt": "2026-07-21T11:00:00+09:00"
  },
  "error": null
}
```

| 코드 | HTTP 상태 | 설명 |
|---|---|---|
| ACC_008_BALANCE_NOT_ZERO | 409 | 잔액이 0원이 아닌 계좌의 해지 시도 |
| ACC_010_HOLD_AMOUNT_REMAINS | 409 | 인증 대기 중인 지급정지 금액이 남아 있는 계좌의 해지 시도 |

잔액이 0원이어도 지급정지 금액이 남아 있으면 인증 대기 중인 거래가 존재한다는 뜻이므로 해지를 거부한다.

## 6. 거래 API

### API-006 POST /accounts/{accountId}/deposit

요청 헤더: `Idempotency-Key: {UUID}`, `X-CSRF-TOKEN: {token}`

요청:
```json
{
  "amount": "100000.00",
  "channel": "INTERNET_BANKING"
}
```

응답(201):
```json
{
  "success": true,
  "data": {
    "transactionId": "TXN-2026-0009981",
    "accountId": "ACCT-2026-000045",
    "type": "DEPOSIT",
    "amount": "100000.00",
    "balanceAfter": "150000.00",
    "processedAt": "2026-07-21T09:10:00+09:00"
  },
  "error": null
}
```

### API-007 POST /accounts/{accountId}/withdraw

요청 헤더: `Idempotency-Key: {UUID}`, `X-CSRF-TOKEN: {token}`

요청과 응답은 API-006과 동일한 구조이며 `type`이 `WITHDRAWAL`이다.

| 코드 | HTTP 상태 | 설명 |
|---|---|---|
| TXN_001_INSUFFICIENT_BALANCE | 409 | 출금 가능 금액 초과 |
| ACC_009_ACCOUNT_STATUS_INVALID | 409 | 정지 또는 해지 계좌에 대한 거래 시도 |

출금은 비관적 락(`SELECT ... FOR UPDATE`)으로 계좌 로우를 잠근 뒤 출금 가능 금액을 검증하고, 검증과 원장 기록을 하나의 DB 트랜잭션으로 묶어 커밋한다. 검증 기준은 잔액이 아니라 출금 가능 금액이다. 요구사항명세서 FR-TXN-002의 정의에 따라 잔액에서 지급정지 금액을 뺀 값이다.

### API-008 POST /transfers

요청 헤더: `Idempotency-Key: {UUID}` (필수), `X-CSRF-TOKEN: {token}` (필수)

요청:
```json
{
  "fromAccountNumber": "110-123-456789",
  "toAccountNumber": "110-987-654321",
  "amount": "3000000.00",
  "memo": "생활비"
}
```

응답(201), 임계금액 이하인 경우:
```json
{
  "success": true,
  "data": {
    "transactionId": "TXN-2026-0009982",
    "status": "COMPLETED",
    "fromAccountBalanceAfter": "1200000.00",
    "processedAt": "2026-07-21T09:15:00+09:00"
  },
  "error": null
}
```

응답(202), 임계금액을 초과해 2차 인증이 필요한 경우:
```json
{
  "success": true,
  "data": {
    "transactionId": "TXN-2026-0009983",
    "status": "PENDING_OTP",
    "otpSentTo": "010-****-5678",
    "holdAmount": "3000000.00",
    "otpExpiresAt": "2026-07-21T09:18:00+09:00"
  },
  "error": null
}
```

202 응답 시점에 이체 금액이 출금 계좌의 지급정지 금액으로 잡힌다. 같은 잔액으로 여러 건의 대기 거래를 만드는 것을 막기 위한 처리이며, 인증 실패나 만료 시 해제된다.

| 코드 | HTTP 상태 | 설명 |
|---|---|---|
| TXN_001_INSUFFICIENT_BALANCE | 409 | 출금계좌의 출금 가능 금액 부족 |
| TXN_002_SAME_ACCOUNT_TRANSFER | 400 | 출금계좌와 입금계좌가 동일 |
| TXN_003_COUNTERPARTY_ACCOUNT_NOT_FOUND | 404 | 입금계좌 번호 불일치 |
| TXN_004_COUNTERPARTY_ACCOUNT_INVALID | 409 | 입금계좌가 정지 또는 해지 상태 |

이체는 두 계좌번호를 오름차순 정렬한 뒤 그 순서대로 락을 획득해 교착상태를 방지하고, 출금계좌 차변과 입금계좌 대변 원장 엔트리를 단일 트랜잭션으로 커밋한다. Phase 2부터는 같은 트랜잭션 안에서 발신함 테이블에 이체완료 이벤트를 적재하고, 별도 발행기가 이를 읽어 Kafka로 발행한다. 커밋과 발행 사이의 원자성을 확보하기 위한 구조이며 상세는 ERD 문서 2.9절에 있다.

### API-009 GET /accounts/{accountId}/balance

응답(200):
```json
{
  "success": true,
  "data": {
    "accountId": "ACCT-2026-000045",
    "balance": "1200000.00",
    "holdAmount": "0.00",
    "availableBalance": "1200000.00",
    "asOf": "2026-07-21T09:15:05+09:00"
  },
  "error": null
}
```

`balance`는 계좌의 잔액이고, `availableBalance`는 여기서 `holdAmount`를 뺀 출금 가능 금액이다. 출금과 이체의 검증 기준이 `availableBalance`이므로 화면에서도 이 값을 함께 보여줘야 사용자가 거절 사유를 이해할 수 있다.

잔액의 진실은 원장 엔트리 합산에 두고, 조회 응답은 계좌 테이블의 캐시 컬럼에서 읽는다. 두 값의 일치는 FR-TXN-006 정합성 검증 배치가 확인한다.

### API-010 GET /accounts/{accountId}/transactions

쿼리 파라미터: `page`, `size`, `type`(선택, DEPOSIT/WITHDRAWAL/TRANSFER_IN/TRANSFER_OUT), `from`, `to`(기간 필터)

목록 조회 공통 응답 포맷(2.5절)을 따른다.

## 7. 상품 API

### API-018 GET /products

응답(200):
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "productCode": "SAV-12M-001",
        "productName": "정기적금 12개월",
        "interestRate": "3.20",
        "minSubscriptionAmount": "100000.00",
        "contractPeriodMonths": 12
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 4,
    "totalPages": 1
  },
  "error": null
}
```

### API-012 POST /products/{productCode}/subscriptions

요청 헤더: `X-CSRF-TOKEN: {token}`

요청:
```json
{
  "accountNumber": "110-123-456789",
  "subscriptionAmount": "500000.00"
}
```

응답(201):
```json
{
  "success": true,
  "data": {
    "contractNumber": "CTR-2026-000381",
    "productCode": "SAV-12M-001",
    "subscribedAt": "2026-07-21T09:20:00+09:00",
    "maturityAt": "2027-07-21T09:20:00+09:00"
  },
  "error": null
}
```

| 코드 | HTTP 상태 | 설명 |
|---|---|---|
| PRD_001_MIN_AMOUNT_NOT_MET | 400 | 최소가입금액 미달 |
| PRD_002_PRODUCT_NOT_AVAILABLE | 409 | 판매 중지된 상품 |

### API-019 GET /customers/{customerId}/contracts

목록 조회 공통 규칙을 따르며, 각 원소는 계약번호, 상품코드, 가입금액, 가입일, 만기일, 계약상태를 포함한다.

## 8. 운영자 API

이 절의 엔드포인트는 모두 `ROLE_OPERATOR` 권한이 있는 내부 사용자만 호출할 수 있다.

### API-020 GET /admin/audit-logs

쿼리 파라미터: `page`, `size`, `eventType`, `actorId`, `from`, `to`

응답 원소 예시:
```json
{
  "logId": "LOG-2026-0088213",
  "eventType": "ACCOUNT_STATUS_CHANGED",
  "actorType": "OPERATOR",
  "actorId": "OP-0007",
  "targetType": "ACCOUNT",
  "targetId": "ACCT-2026-000045",
  "detail": { "previousStatus": "ACTIVE", "targetStatus": "SUSPENDED" },
  "occurredAt": "2026-07-21T11:00:00+09:00"
}
```

### API-021 GET /admin/ctr-reports

쿼리 파라미터: `page`, `size`, `status`(PENDING/LOGGED), `transactionDate`

응답 원소는 CtrReportQueue 테이블의 컬럼을 그대로 노출한다. 요구사항명세서 7.1절에 명시했듯, 실제 전송 구간은 로그 출력으로 대체하므로 이 API는 판별 결과 조회 용도로만 사용한다.

### API-022 GET /admin/suspicious-transactions

FR-SUP-003의 간이 룰 기반 탐지 결과를 조회한다. Phase 3 구현 범위이며, 초기에는 단일 거래 임계금액 초과, 짧은 시간 내 반복 이체 같은 단순 규칙만 적용한다.

## 9. 요청/응답 스키마와 ERD의 매핑

이 절의 모든 JSON 필드명은 ERD 문서의 컬럼과 1대1로 대응하되 표기 규칙만 카멜케이스로 바뀐다. 예를 들어 ERD의 `resident_reg_no_encrypted`, `resident_reg_no_hash`는 API 응답에 그대로 노출하지 않고, 응답 시점에는 마스킹된 `residentRegNo`(뒷자리 마스킹)만 반환한다. 원문 암호화 컬럼과 해시 컬럼은 서버 내부 조회 및 중복 확인 용도로만 쓰이고 API 계약에는 등장하지 않는다.

예외가 두 개 있다. `availableBalance`는 컬럼이 아니라 `current_balance_cache`에서 `hold_amount`를 뺀 파생값이다. 발신함 테이블은 내부 발행 메커니즘이므로 어떤 응답에도 노출하지 않는다.

## 10. Springdoc OpenAPI 연동 메모

이 문서의 명세는 실제 구현 시 Springdoc OpenAPI 어노테이션(`@Operation`, `@ApiResponse`, `@Schema`)으로 코드에 반영하고, `/v3/api-docs`와 Swagger UI로 자동 노출한다. 이 문서는 코드 작성 전 설계 단계의 명세이며, 구현 이후에는 Swagger UI가 실행 가능한 API 문서로서 이 문서를 대체하거나 보완하는 역할을 한다.

코드에서 생성된 명세는 `contracts/openapi/openapi.yaml`로 덤프해 저장소에 커밋한다. 원본은 코드이고 이 파일은 스냅샷이며, 검증 파이프라인이 둘의 동일성을 확인해 명세를 커밋하지 않고 계약을 바꾸는 것을 막는다. 상세 흐름은 폴더구조 문서 6절에 있다.
