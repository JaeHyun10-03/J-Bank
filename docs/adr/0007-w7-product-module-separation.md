# ADR 0007: product 모듈을 독립 배포 단위로 분리

## 상태

승인됨. 2026-08-31 W7 시점.

## 배경

`todo/W7.md`는 "전면 MSA 전환이 아니라 분리가 정당화되는 경계 하나만 실제로
떼어낸다"는 방식으로 상품·계약 도메인 분리를 명시했다 — 거래 코어와
트랜잭션 경계를 공유할 필요가 없고 배포 주기도 다르다는 이유였다. 분리
방식은 두 갈래였다: (a) Gradle 서브모듈로만 나누고 같은 JAR·같은
Deployment로 묶는 "가상 분리", (b) 별도 JVM·Docker 이미지·K8s Deployment로
띄우는 "진짜 독립 배포". 사용자가 (b)를 선택했다 — 진짜 네트워크 경계를
넘겨야 사가(saga)가 왜 필요한지도 실제로 증명된다는 이유였다.

## 결정

### 모듈 구조

`apps/jbank-product`를 `apps/jbank-api`와 완전히 독립된 Gradle 프로젝트로
새로 만들었다(각자 `settings.gradle.kts`·`gradlew`를 갖는 별개 빌드 —
루트에 멀티모듈 `settings.gradle.kts`가 없다는 이 저장소의 기존 구조를
그대로 따랐다). `com.jbank.product.*` 패키지(도메인·리포지토리·서비스·
컨트롤러)를 이 프로젝트로 옮기고, jbank-api에는 남기지 않았다.

두 프로젝트가 서로에 대한 Gradle `project()` 의존성을 전혀 갖지 않는다 —
이게 ArchUnit 규칙보다 강한 경계 강제 방법이다. ArchUnit은 같은 컴파일
단위 안에서만 "이 패키지가 저 패키지를 참조하면 안 된다"를 검사할 수
있는데, 지금은 애초에 다른 컴파일 단위라 물리적으로 참조가 불가능하다.

### 데이터베이스는 계속 공유

`products`/`product_contracts` 테이블은 여전히 jbank-api의 Flyway 마이그레이션
이력이 소유한다(V7, 이번에 V14 추가). jbank-product는 Flyway를 아예 안 쓰고
`ddl-auto: validate`로 그 스키마를 읽기만 한다. 데이터 소유권 분리(서비스별
전용 DB)는 이번 스코프 밖으로 명시적으로 뺐다 — "경계 하나만 정당화되는
만큼만 떼어낸다"는 원래 방침과 같은 결이다.

### 상품가입 = 오케스트레이션 사가

기존 `ProductService.subscribe()`는 Account 엔티티를 직접 참조해 검증만
하고 실제로 돈을 옮기지 않았다(놀랍게도 W7 이전까지 상품가입은 출금을
하지 않았다). 모듈을 분리하면서 이 갭을 그대로 방치할 수 없었다 — product가
더는 AccountRepository를 못 쓰니, "계약 생성 → 초기 납입금 출금 → 계약
확정" 사가를 이번에 새로 구현했다:

1. **계약 생성(PENDING)** — jbank-product가 로컬 트랜잭션으로 먼저 커밋한다.
   이 지점 이후 서비스가 죽어도 사가가 중간에 멈췄다는 흔적이 DB에 남는다.
2. **출금** — jbank-api의 내부 API(`POST
   /internal/v1/accounts/withdraw-by-number`)를 호출한다. 계좌 소유주 검증·
   잔액 확인·행 락은 기존 `WithdrawalService`가 그대로 담당한다(중복
   구현 없음). 실패하면 아직 아무 돈도 안 움직였으니 PENDING 행을 그냥
   지운다 — 보상이 필요 없다.
3. **확정(ACTIVE)** — 로컬 트랜잭션으로 계약 상태를 전환한다. 이 단계가
   실패하면(예: DB 오류) **보상 트랜잭션**으로 `POST
   /internal/v1/accounts/{id}/deposit`을 호출해 방금 나간 돈을 되돌리고,
   계약을 `FAILED`로 남긴다(감사 기록, 삭제하지 않음).

`ProductSubscriptionSagaIntegrationTest`(WireMock으로 jbank-api를 대역)가
세 경로(정상 확정/출금 실패/확정 실패→보상)를 전부 검증한다 — 세 번째가
이번 주 완료 기준이 요구한 "보상 거래가 원장에 기록되는지" 확인이다(실제
원장은 jbank-api 쪽 `DepositService`가 기록하므로, 그 호출이 정확한 금액으로
나가는지를 WireMock 요청 검증으로 확인했다).

self-invocation 문제(같은 빈 안에서 `this.method()`로 `@Transactional`
메서드를 부르면 Spring AOP 프록시가 안 걸림)를 피하려고 로컬 DB 단계는
`ProductContractSagaSteps`라는 별도 빈으로 뺐다.

### 서비스 간 인증 — 공유 비밀키

`InternalApiKeyFilter`가 `/internal/v1/**` 경로만 가로채 `X-Internal-Api-Key`
헤더를 검사한다. 고객 JWT 인증(`JwtAuthenticationFilter`)과는 별개 경로다 —
`SecurityConfig`의 PUBLIC_PATHS에 이 프리픽스를 permitAll로 두되, 필터가
그 자리에서 별도로 인증한다(무인증과는 다르다).

### 인증·공통 응답 포맷은 중복 구현

jbank-product는 jbank-api의 `SecurityConfig`, `JwtTokenProvider`(검증만,
발급 없음), `CurrentCustomerId`/`CurrentCustomerIdArgumentResolver`,
`CsrfDoubleSubmitFilter`, `ApiResponse`/`PageResponse`, `ErrorCode`(부분집합)를
그대로 복제했다. 공유 라이브러리 모듈로 뽑는 방법도 있었지만, 그러면 두
서비스가 다시 하나의 Gradle 의존관계로 묶여 "독립 배포"라는 이번 결정의
목적과 충돌한다 — 코드 중복이 배포 결합보다 싼 비용이라고 판단했다.

### 이자 지급 배치 — 발견된 역방향 의존과 그 해소

`InterestMaturityJobConfig`(jbank-api, K8s CronJob)가 `ProductContractRepository`를
직접 읽고 `ProductContract.markMatured()`를 직접 호출하고 있었다 — 이게
바로 "W1부터 ArchUnit이 막아온 역방향 의존이 실제로 없었는지 검증"이
찾아낸 결과물이다. ArchUnit 규칙 자체는 이 의존을 막지 못했다 — batch
패키지는 애초에 그 규칙의 검사 대상이 아니었기 때문이다(경계가 실제로
값을 했는지 확인해보니, 절반만 값을 한 것으로 드러났다).

해소 방식(둘로 나눔, 서비스 소유권 기준):
- **읽기(만기 계약 조회 + 이자 계산)** → jbank-product로 이관.
  `GET /internal/v1/contracts/matured?asOf=`가 이자 금액까지 계산해서
  돌려준다(이자율 데이터가 product 소유이므로).
- **쓰기(계좌 입금)** → jbank-api에 그대로 둔다. 계좌·거래·원장은
  jbank-api 소유라 옮길 이유가 없다. 입금이 끝나면
  `PATCH /internal/v1/contracts/{id}/mature`로 확정만 알린다.

배치 잡 자체(Spring Batch, CronJob)는 jbank-api에 남았다 — 돈이 움직이는
쪽에 잡을 두는 게 "잡이 크레딧을 확정 짓는 트랜잭션 경계 안에 있어야
한다"는 원칙에 더 맞는다고 판단했다.

**대가: 원자성이 깨졌다.** 분리 전에는 이자 입금과 계약 만기 전환이 같은
로컬 트랜잭션(청크 하나) 안에서 원자적으로 묶여 있었다. 분리 이후엔 만기
확정이 네트워크 호출이라 그 원자성이 사라진다 — 이자는 입금됐는데
`markMatured` 호출만 실패할 수 있다. 이걸 idempotencyKey를 실행일
(`runDate`) 기준에서 계약 하나당 하나(`INTEREST-{contractId}`)로 바꿔서
막았다 — 확정 호출이 실패해서 다음 배치 실행에 같은 계약이 다시 잡혀도,
이미 발급된 거래를 찾으면 입금은 건너뛰고 확정 호출만 재시도한다.
(`InterestMaturityJobIntegrationTest`의 재시도 테스트가 이걸 검증한다.)

## 근거

- Gradle 프로젝트 경계가 ArchUnit보다 강한 이유는 위에 적은 대로다 —
  컴파일이 안 되니 어길 수가 없다.
- 사가를 "미리 계약을 만들고 나중에 확정"하는 순서로 짠 이유: 크래시가
  나도 사가가 어디까지 갔는지 DB에 남아야 나중에 재조정(reconciliation)이
  가능하다. 반대로 "출금까지 성공한 뒤에야 계약을 만드는" 순서는 크래시
  시점에 돈은 나갔는데 그 사실이 어느 서비스에도 안 남는 더 나쁜 실패
  모드를 만든다.
- 인증·공통 응답 포맷을 공유 라이브러리로 안 뽑은 이유: 지금 이 프로젝트
  규모에서 공유 라이브러리 모듈은 "독립 배포"라는 목적 자체와 모순된다
  — 두 서비스가 그 라이브러리의 버전 하나를 두고 다시 묶이게 된다.
- 배치 잡의 읽기/쓰기 분리 기준(product는 계산만, api는 입금만)은
  "돈이 움직이는 트랜잭션은 그 트랜잭션의 로컬 커밋이 가능한 서비스가
  가진다"는 원칙을 그대로 적용한 것이다.

## 영향

- `apps/jbank-product`: 새 독립 Gradle 프로젝트, 상품·계약 도메인·API·
  사가·이자 계산 로직 소유.
- `apps/jbank-api`: `com.jbank.product` 패키지 제거. 새 `com.jbank.internal`
  패키지(출금·입금 내부 API), `MaturedContractApiClient`(product 호출용).
- `SeedDataRunner`가 두 서비스로 나뉜다 — 고객·계좌 시드는 jbank-api,
  상품 시드는 jbank-product.
- `docs/adr/0005`(배치 잡 분산 락)의 대상이던 세 배치 잡 중
  `interestMaturityJob`만 이번에 외부 API 의존이 생겼다 —
  `ctrDetectionJob`/`ledgerReconciliationJob`은 영향 없음.
- 아직 안 한 것: jbank-product의 Docker·Helm·ArgoCD·CI 배선(뒤이은 커밋),
  프론트엔드 프록시가 두 백엔드를 구분해서 라우팅하는 것(ALB/Ingress
  자체가 아직 자리표시자 단계라 이번 스코프 밖).
