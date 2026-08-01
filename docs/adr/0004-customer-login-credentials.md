# ADR 0004: 고객 로그인 자격증명을 `customers` 테이블에 추가

## 상태

승인됨. 2026-08-01 W3 인증 구현 착수 시점.

## 배경

ERD 문서(`02_J-Bank_ERD.md`) 2.1절 Customer 스키마 어디에도 로그인 식별자나 비밀번호
컬럼이 없다. 그런데 API설계 문서 2.2·4절의 API-011 로그인은 `loginId`/`password` 요청
본문을 전제하고, FR-AUTH-001은 "bcrypt 해시 검증"을 요구한다. API-001 고객 등록(W1
구현분)의 `CustomerRegisterRequest`에도 이 필드들이 없다 — 설계 문서 사이의 공백이었다.

## 결정

`customers` 테이블에 `login_id VARCHAR(50) UNIQUE NOT NULL`, `password_hash VARCHAR(255)
NOT NULL` 두 컬럼을 추가한다(V6 마이그레이션). `CustomerRegisterRequest`(API-001)에
`loginId`, `password`(평문, 요청 시점만) 필드를 추가하고, `CustomerService.register()`가
`PasswordEncoder`(BCrypt)로 해시한 값만 엔티티에 저장한다.

## 근거

로그인 자격증명은 실명번호처럼 실명확인 절차에 매인 값이 아니라 순수히 "이 CIF로 로그인할
권한을 증명하는 값"이라서, 고객이 새로 생기는 시점(API-001)에 함께 받는 것이 자연스럽다.
계좌 개설(API-002)은 이미 CDD를 마친 고객만 할 수 있는 후속 행위라 로그인 자격증명을
넣기에 어색하고, 별도의 "자격증명 설정" 엔드포인트를 새로 만드는 것은 이번 주 범위 밖의
화면 흐름을 하나 더 만드는 것이라 배보다 배꼽이 크다.

`login_id`는 암호화하지 않는다. 실명번호·연락처·주소와 달리 실명(PII) 자체가 아니라
사용자가 스스로 고른 공개적 성격의 식별자이고, 로그인 조회 시 매번 복호화하면 조회
경로에서 이 값 하나만 다른 방식으로 다뤄야 해 코드가 복잡해진다. `password_hash`는 BCrypt
단방향 해시라 애초에 복호화가 불가능하므로 암호화 컬럼 컨버터(AES-GCM) 대상이 아니다.

Phase 1이 아직 태그도 안 된 시점이라 `CustomerRegisterRequest`를 지금 확장하는 데 대가가
없다. 이미 배포된 클라이언트가 없어 필드를 추가해도 되돌릴 것이 없다(구현계획 7.1절 기준).

## 영향

- `Customer` 엔티티 생성자에 `loginId`, `passwordHash` 파라미터 추가 — 테스트 픽스처
  9곳이 함께 수정됨.
- `CustomerRepository.findByLoginId()` 추가, 로그인(API-011)과 중복 가입 검증에서 사용.
- 로그인ID 중복 시 `ACC_011_DUPLICATE_LOGIN_ID`(409)를 반환한다. 문서에는 이 케이스가
  명시되어 있지 않았지만, 실명번호 중복 검사와 같은 이유로 필요해 같은 패턴으로 추가했다.
