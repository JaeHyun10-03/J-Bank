# j-bank 폴더 구조

문서 버전: v2.0
작성일: 2026-07-26

## 버전 이력

| 버전 | 일자 | 변경 내용 |
|---|---|---|
| v1.0 | 2026-07-26 | 최초 작성 - 모노레포, 백엔드 Gradle 멀티모듈, packages/api-client 분리 |
| v2.0 | 2026-07-26 | 설계 문서 정합성 보정. 백엔드를 단일 모듈 + 패키지 경계로 전환, auth·support 도메인과 batch 진입점 추가, perf 트랙 신설, api-client 패키지와 turbo/pnpm 워크스페이스 제거, openapi.yaml의 성격을 원본에서 스냅샷으로 재정의 |

## 관련 문서

- J-Bank_요구사항명세서.md
- J-Bank_ERD.md
- J-Bank_API설계.md
- J-Bank_인프라아키텍처.md
- J-Bank_화면플로우차트.md
- J-Bank_프론트엔드기술스택.md
- J-Bank_구현계획.md

---

## 1. 전체 구조

```text
j-bank/
├── .github/
│   └── workflows/
│       ├── backend-ci.yml                  # 컴파일·단위·통합·ArchUnit·Spotless·커버리지·OpenAPI 드리프트
│       ├── backend-cd.yml                  # main 병합 시 이미지 빌드 후 레지스트리 푸시
│       ├── frontend-ci.yml                 # 타입체크·린트·테스트·생성 타입 드리프트
│       ├── infra-plan.yml                  # terraform plan 자동, apply는 수동 승인
│       └── perf.yml                        # k6 수동 트리거, 결과만 커밋
│
├── apps/
│   ├── jbank-api/                           # Spring Boot 단일 모듈, 모듈러 모놀리식
│   │   ├── build.gradle.kts
│   │   ├── settings.gradle.kts
│   │   ├── gradle/
│   │   ├── gradlew
│   │   ├── gradlew.bat
│   │   ├── README.md
│   │   └── src/
│   │       ├── main/
│   │       │   ├── java/com/jbank/
│   │       │   │   ├── JbankApiApplication.java
│   │       │   │   │
│   │       │   │   ├── global/                 # 프레임워크 계층, 도메인을 모른다
│   │       │   │   │   ├── config/             # Jackson, OpenAPI, 트랜잭션, 비동기
│   │       │   │   │   ├── filter/             # 요청추적ID 생성 후 진단 컨텍스트 주입
│   │       │   │   │   ├── exception/          # @RestControllerAdvice 전역 예외 처리기
│   │       │   │   │   └── response/           # ApiResponse<T>, ErrorCode 열거형
│   │       │   │   │
│   │       │   │   ├── common/                 # 도메인 무관 기반층
│   │       │   │   │   ├── crypto/             # AES-256-GCM AttributeConverter, HMAC 해시
│   │       │   │   │   ├── money/              # 금액 문자열 직렬화, BigDecimal 규칙
│   │       │   │   │   ├── event/              # 도메인 이벤트 기반 타입
│   │       │   │   │   ├── constants/
│   │       │   │   │   └── util/
│   │       │   │   │
│   │       │   │   ├── customer/               # 고객, CDD·EDD, 위험도 이력
│   │       │   │   │   ├── controller/
│   │       │   │   │   ├── service/
│   │       │   │   │   ├── domain/
│   │       │   │   │   ├── repository/
│   │       │   │   │   └── dto/
│   │       │   │   │
│   │       │   │   ├── account/                # 계좌 마스터, 상태 기계, 채번, 잔액 캐시
│   │       │   │   │   └── (controller/service/domain/repository/dto)
│   │       │   │   │
│   │       │   │   ├── ledger/                 # 복식부기 원장, 추가 전용, 합산 검증
│   │       │   │   │   └── (controller/service/domain/repository/dto)
│   │       │   │   │
│   │       │   │   ├── transfer/                # 금전 이동 조율: 입금·출금·이체, 멱등성
│   │       │   │   │   └── (controller/service/domain/repository/dto)
│   │       │   │   │
│   │       │   │   ├── product/                 # 예적금 상품, 계약
│   │       │   │   │   └── (controller/service/domain/repository/dto)
│   │       │   │   │
│   │       │   │   ├── auth/                    # 로그인, 토큰, 2차 인증
│   │       │   │   │   ├── controller/
│   │       │   │   │   ├── service/
│   │       │   │   │   ├── config/              # SecurityFilterChain, CORS, 위조 방지
│   │       │   │   │   ├── jwt/
│   │       │   │   │   ├── otp/
│   │       │   │   │   └── dto/
│   │       │   │   │
│   │       │   │   ├── support/                 # 지원 기능, 도메인 이벤트 구독
│   │       │   │   │   ├── audit/               # 감사 로그
│   │       │   │   │   ├── outbox/              # 발신함 테이블과 폴링 발행기
│   │       │   │   │   ├── notification/        # Kafka 소비 후 알림, 로그 출력 대체
│   │       │   │   │   ├── ctr/                 # 고액현금거래 보고대상 큐
│   │       │   │   │   └── fds/                 # 간이 이상거래 규칙
│   │       │   │   │
│   │       │   │   └── batch/                   # Spring Batch 잡 정의, 도메인 조율
│   │       │   │       ├── interest/            # 이자 계산과 만기 처리
│   │       │   │       ├── reconciliation/      # 원장 정합성 대사
│   │       │   │       └── ctr/                 # 일자별 현금거래 집계
│   │       │   │
│   │       │   └── resources/
│   │       │       ├── application.yml
│   │       │       ├── application-local.yml
│   │       │       ├── application-dev.yml
│   │       │       ├── application-prod.yml
│   │       │       └── db/
│   │       │           ├── migration/           # Flyway, 단일 버전 계열
│   │       │           └── seed/                # 시연용 시드, 별도 프로파일에서만 주입
│   │       │
│   │       └── test/
│   │           ├── java/com/jbank/
│   │           │   ├── architecture/            # ArchUnit 의존 방향 규칙
│   │           │   ├── concurrency/             # 동시성·멱등성 검증 5종
│   │           │   ├── integration/             # Testcontainers 통합 시나리오
│   │           │   └── (도메인별 단위 테스트)
│   │           └── resources/
│   │
│   └── frontend/                                # Next.js 14 App Router
│       ├── package.json
│       ├── next.config.js
│       ├── tsconfig.json
│       ├── tailwind.config.ts
│       ├── middleware.ts                        # 쿠키 존재·만료 확인 후 접근 제어
│       ├── README.md
│       ├── app/
│       │   ├── layout.tsx
│       │   ├── page.tsx                         # 홈, 계좌 목록
│       │   ├── not-found.tsx
│       │   ├── login/
│       │   ├── signup/
│       │   │   └── edd/
│       │   ├── accounts/
│       │   │   ├── new/
│       │   │   └── [accountId]/
│       │   │       ├── transactions/
│       │   │       ├── deposit/
│       │   │       ├── withdraw/
│       │   │       └── transfer/
│       │   ├── transfers/
│       │   │   └── [transactionId]/otp/
│       │   ├── products/
│       │   │   └── [productCode]/subscribe/
│       │   ├── contracts/
│       │   └── api/
│       │       └── proxy/[...path]/route.ts     # same-site 유지용 중계
│       ├── components/
│       │   ├── ui/                              # shadcn/ui 복사본
│       │   └── domain/
│       ├── hooks/
│       ├── lib/
│       │   ├── axios.ts                         # 인스턴스와 인터셉터
│       │   ├── idempotency.ts                   # 제출 시점 키 생성
│       │   ├── error-map.ts                     # 도메인 에러코드를 폼 에러로 변환
│       │   └── format.ts                        # 금액·계좌번호·날짜 표시
│       ├── types/
│       │   └── api.ts                           # openapi-typescript 생성물
│       ├── styles/
│       └── e2e/                                 # Playwright, 화면플로우 7개 흐름
│
├── contracts/
│   ├── openapi/
│   │   └── openapi.yaml                         # 백엔드에서 덤프한 스냅샷, 원본 아님
│   └── bruno/                                   # 수동 호출 컬렉션
│
├── docs/
│   ├── design/                                  # 설계 문서 7종
│   ├── adr/                                     # 아키텍처 결정 기록
│   ├── architecture/                            # 구성도와 구조 설명
│   ├── sequence/                                # 이체·2차인증·사가 흐름
│   ├── runbook/                                 # 장애 대응과 운영 절차
│   └── work-notes/                              # 작업 노트, 디자인 노트
│
├── infra/
│   ├── terraform/
│   │   ├── modules/
│   │   │   ├── network/
│   │   │   ├── compute/
│   │   │   ├── data/
│   │   │   └── security/
│   │   └── envs/
│   │       ├── dev/
│   │       └── prod/
│   ├── helm/
│   │   └── jbank-api/                            # Deployment, CronJob, HPA, PDB
│   ├── docker/
│   │   └── jbank-api/
│   │       └── Dockerfile
│   └── compose/
│       ├── docker-compose.yml                   # profiles: core / messaging / observability
│       └── observability/
│           ├── prometheus.yml
│           ├── loki-config.yml
│           └── grafana/
│
├── perf/
│   ├── k6/
│   │   └── transfer.js
│   ├── results/                                 # W2부터 주차별 측정치
│   └── README.md                                # 측정 조건 고정 규약
│
├── scripts/
│   ├── bootstrap.sh                             # 최초 세팅
│   ├── dev.sh                                   # 프로파일 지정 기동
│   ├── generate-api.sh                          # OpenAPI 덤프 후 프론트 타입 생성
│   ├── seed.sh
│   ├── perf.sh                                  # k6 실행과 결과 저장
│   └── clean.sh
│
├── .gitignore
├── .editorconfig
└── README.md
```

## 2. v1.0에서 바뀐 것과 이유

| 항목 | v1.0 | v2.0 | 근거 |
|---|---|---|---|
| 백엔드 모듈 | Gradle 멀티모듈 6개 | 단일 모듈, 패키지 경계 + ArchUnit | 구현계획 W1 |
| 도메인 범위 | 5개 | 7개, auth·support 추가 | 구현계획 W1 패키지 목록 |
| 배치 진입점 | 없음 | batch 패키지, 같은 이미지에 다른 실행 인자 | 구현계획 W5, 인프라 6절 |
| Flyway 위치 | 미정 | resources/db/migration 단일 계열 | 구현계획 W1 |
| 성능 측정 | 없음 | perf 디렉터리 신설 | 구현계획 7.5절 |
| openapi.yaml | API 계약의 단일 원본 | 코드에서 덤프한 스냅샷 | API설계 10절, 구현계획 W3 |
| api-client | packages 별도 패키지 | frontend/types/api.ts | 프론트엔드기술스택 13절 |
| turbo, pnpm 워크스페이스 | 있음 | 제거 | JS 패키지가 하나뿐 |
| 프론트 Dockerfile, Helm | 있음 | 제거 | 프론트엔드기술스택 10절, Vercel 배포 |
| frontend-cd.yml | 있음 | perf.yml로 교체 | Vercel Git 연동이 배포 담당 |
| compose | 파일 4개 | 단일 파일 profiles | 구현계획 W1, 리스크 5번 |
| 프론트 라우트 | 4개 | 표 전체 반영 | 프론트엔드기술스택 8절 |
| docs/api | 있음 | 제거 | Swagger UI와 중복 |

## 3. 백엔드를 단일 모듈로 두는 이유

### 3.1 모듈 경계는 W2 이후에 드러난다

멀티모듈의 이점은 컴파일 시점에 의존을 막아준다는 것과 W7 서비스 분리가 쉬워진다는 것이다. 둘 다 실재하는 이점이지만, 그 대가로 W1에 모듈 경계를 확정해야 한다.

문제는 진짜 경계가 W2에서 이체를 구현해본 뒤에 드러난다는 점이다. v1.0이 제시한 "도메인 간 의존 최소화" 원칙은 FR-TXN-003을 코드로 옮기는 첫 순간에 깨진다. 이체는 계좌 락 획득과 상태 검증, 원장 두 건 기록을 하나의 트랜잭션 안에서 해야 하므로 계좌와 원장을 동시에 필요로 한다.

경계가 틀렸을 때 되돌리는 비용이 두 방식에서 크게 다르다. 패키지 이동은 개발도구의 리팩터링 기능 한 번이고, 모듈 이동은 거기에 빌드 스크립트와 의존 선언 수정이 더해진다. 구현계획 7.1절의 판단 기준을 그대로 적용하면, 되돌리는 비용이 낮은 쪽을 먼저 택하고 경계가 검증된 뒤에 굳히는 것이 맞다.

### 3.2 W2에 부담을 얹지 않는다

구현계획 12절은 W2를 프로젝트 최대 리스크로 지목한다. v1.0에서 난이도가 가장 높다고 표시한 두 주를 한 주로 접은 구간이고, 코드를 치는 시간보다 문제를 이해하고 검증 시나리오를 설계하는 시간이 지배적이라 시간 투입에 선형으로 반응하지 않는다.

원장과 이체, 계좌는 단일 트랜잭션 경계를 공유한다. 이 세 개를 모듈로 갈라두면 가장 어려운 작업을 하는 주에 모듈 간 의존 협상이 추가로 얹힌다. 같은 모듈 안에서 먼저 정확하게 동작시키고, 경계는 ArchUnit으로 지키는 편이 안전하다.

### 3.3 강제 수단은 여전히 있다

컴파일 강제를 잃는 대신 ArchUnit으로 같은 규칙을 검증 시점에 강제한다. 구현계획 W1이 이미 이 방식을 지정해두었다. 규칙 위반이 빌드 실패로 이어지므로 실효성은 확보되고, 규칙 자체가 테스트 코드로 남아 어떤 경계를 의도했는지가 문서보다 명확하게 드러난다.

### 3.4 W7 분리가 어려워지지 않는다

W7에서 상품과 계약 도메인을 떼어낼 때 어려운 부분은 빌드 구성이 아니라 트랜잭션 경계를 쪼개고 사가 보상을 설계하는 일이다. 이 작업량은 출발점이 단일 모듈이든 멀티모듈이든 같다.

패키지 구조를 나중에 모듈이 될 모양 그대로 만들고 의존 방향을 ArchUnit으로 지켜두면, W7의 분리는 패키지를 모듈로 승격하는 작업이 된다. 그리고 그 시점에는 경계가 실제로 검증된 상태다.

### 3.5 설명하기에도 유리하다

처음부터 여섯 개 모듈로 쪼갠 구조와, 모듈러 모놀리식으로 시작해 경계를 검증 수단으로 강제하고 필요가 확인된 도메인 하나만 분리한 구조는 받는 인상이 다르다. 뒤의 것이 판단 근거를 설명할 재료가 더 많다. 왜 그때 분리했는지, 왜 나머지는 분리하지 않았는지가 아키텍처 결정 기록에 남는다.

## 4. 의존 방향 규칙

ArchUnit으로 강제하고 아키텍처 결정 기록에 근거를 남긴다. W7에서 모듈로 승격할 때 이 표가 그대로 의존 선언이 된다.

### 4.1 계층 규칙

- `global`은 도메인 패키지를 모른다. `common`만 의존한다.
- 모든 도메인 패키지는 `common`과 `global.response`를 의존할 수 있다.
- `controller`는 자기 도메인의 `service`만 호출하고 `repository`를 직접 호출하지 않는다.
- `domain`은 `service`와 `controller`를 모른다.

### 4.2 도메인 간 허용 방향

| 출발 | 도착 | 이유 |
|---|---|---|
| transfer | account, ledger | 금전 이동은 계좌 잠금과 원장 기록을 함께 조율한다 |
| product | account, ledger | 상품 가입 시 초기 납입금을 출금한다 |
| auth | customer | 인증 주체가 고객이다 |
| batch | product, ledger, support, account | 잡이 도메인 서비스를 호출한다 |
| support | common만 | 도메인 이벤트를 구독하는 방향으로만 연결된다 |

명시되지 않은 조합은 전부 금지한다. 특히 다음 세 가지를 규칙으로 못박는다.

`account`와 `ledger`는 서로를 의존하지 않는다. 계좌 상세 조회에 필요한 잔액은 계좌 테이블의 캐시 컬럼에서 읽고, 원장 합산은 `ledger`가 소유한다. 두 값의 대사는 `batch.reconciliation`이 담당한다. 이렇게 하면 잔액의 진실은 원장에 두면서도 조회 경로에서 두 도메인이 얽히지 않는다.

역방향 의존과 순환은 전면 금지한다. `ledger`가 `transfer`를 알거나 `customer`가 `account`를 아는 구조가 생기면 W7에 분리선이 사라진다.

`support`는 어떤 도메인도 직접 호출하지 않는다. 감사 로그와 발신함은 `common.event`에 정의된 도메인 이벤트를 구독해서만 동작한다. 이 방향을 지키면 감사 기록을 붙이거나 떼는 것이 도메인 코드에 영향을 주지 않는다.

### 4.3 transfer 패키지의 범위

`transfer`는 이체만이 아니라 입금과 출금까지 담당한다. 세 유스케이스 모두 계좌와 원장을 함께 건드리는 금전 이동이고 멱등성 처리 경로가 같아서, 조율 지점을 한곳에 두는 편이 낫다. 이름이 범위보다 좁으므로 아키텍처 결정 기록에 이 판단을 남긴다.

## 5. 배치 실행 방식

배치를 별도 실행 모듈로 만들지 않고 같은 애플리케이션 안의 `batch` 패키지에 두되, 실행만 분리한다.

로컬에서는 프로파일과 잡 이름을 인자로 지정해 실행한다. 배포 환경에서는 인프라 문서 6절이 요구하는 자원 분리를 쿠버네티스 CronJob으로 달성한다. API 배포와 같은 이미지를 쓰고 실행 인자와 노드 선택자만 다르게 준다.

이 방식의 이점은 이미지가 하나라는 것이다. 배치 전용 이미지를 따로 만들면 빌드 파이프라인이 둘로 늘고, 도메인 코드 변경 시 두 이미지의 버전을 맞추는 문제가 생긴다. 잡은 전부 재실행 안전하게 만들고 기준일을 잡 파라미터로 받아 중복 실행을 막는다.

## 6. 계약 흐름

원본은 백엔드 코드다. `openapi.yaml`은 그 코드에서 뽑아낸 스냅샷이며, 계약 변경을 커밋 이력에 드러내고 프론트 타입 생성의 입력이 되는 역할만 한다.

```
Springdoc 어노테이션
  → /v3/api-docs
  → contracts/openapi/openapi.yaml   (덤프 후 커밋)
  → apps/frontend/types/api.ts       (openapi-typescript 생성)
```

`scripts/generate-api.sh`가 이 두 단계를 한 번에 수행한다.

두 지점에서 드리프트를 검사한다. 백엔드 검증에서는 현재 코드로 덤프한 결과가 커밋된 `openapi.yaml`과 다르면 실패시킨다. 명세를 커밋하지 않고 코드를 바꾸는 것을 막는 장치다. 프론트 검증에서는 커밋된 명세로 재생성한 타입이 커밋된 `api.ts`와 다르면 실패시킨다. 결과적으로 백엔드 계약이 바뀌면 프론트 빌드가 깨진다.

생성물인 `api.ts`를 커밋하는 이유는 프론트 빌드가 백엔드 실행에 의존하지 않게 하기 위해서다. 원본이 코드라는 원칙과 충돌하지 않는다. 커밋된 것은 원본이 아니라 재생성 가능한 산출물이고, 드리프트 검사가 그 동일성을 보장한다.

## 7. 로컬 실행 프로파일

단일 compose 파일에 프로파일 셋을 정의한다. 파일을 나누지 않는 이유는 서비스 정의가 중복되면 어느 쪽이 진실인지 흐려지기 때문이다.

| 프로파일 | 구성 | 사용 시점 |
|---|---|---|
| core | PostgreSQL 16, Redis 7 | 상시 |
| messaging | Kafka | W4 이후 |
| observability | Prometheus, Grafana, Loki | W6 이후 |

구현계획 리스크 5번이 지적한 대로 전부 동시에 띄우면 개발 머신이 버겁다. `scripts/dev.sh`가 프로파일을 인자로 받아 필요한 것만 올린다.

전부 올리는 단축 명령을 두지 않는다. 편의를 위해 만들어두면 습관적으로 쓰게 되고, 그러면 k6 측정 조건이 주차마다 달라진다.

## 8. 성능 측정 규약

`perf/README.md`에 측정 조건을 고정해두고 매주 같은 조건으로 실행한다. 조건이 흔들리면 주차 간 비교가 성립하지 않으므로 다음 네 가지를 기록에 함께 남긴다.

데이터 건수, 애플리케이션 인스턴스 수, 컨테이너 자원 제한, 그리고 측정 시점에 떠 있던 compose 프로파일이다. 마지막 항목은 관측 스택이 떠 있는지 여부만으로도 수치가 흔들리기 때문에 필요하다.

결과는 `perf/results/W2/` 형태로 주차별 디렉터리에 적재한다. 실행은 검증 파이프라인에서 수동 트리거로만 돌린다. 병합 요청마다 자동 실행하면 파이프라인 시간이 감당되지 않는다.

## 9. 설계 문서에 반영할 사항

이 구조로 가려면 구현계획 3절을 고쳐야 한다. 현재는 백엔드와 프론트엔드를 분리한 두 개의 저장소로 두기로 되어 있고, 근거는 배포 대상이 갈라져 있어 검증 파이프라인에 조건 분기가 늘어난다는 것이었다.

단일 저장소로 바꾸는 편이 낫다고 판단한다. 조건 분기는 워크플로의 경로 필터로 해결되는 반면, 설계 문서와 아키텍처 결정 기록, 계약 스냅샷, 인프라 코드가 같은 커밋 이력을 공유하는 이점이 크다. 특히 계약 드리프트 검사는 백엔드와 프론트가 같은 저장소에 있을 때 훨씬 단순해진다. Vercel은 루트 디렉터리를 지정하면 하위 경로 배포를 지원하므로 배포 대상 분리와도 충돌하지 않는다.

착수 전 문서 보정 목록에 이 항목을 네 번째로 추가한다. 기존 세 건은 다음과 같다.

첫째, API설계 문서의 API-011과 API-014 응답을 쿠키 발급 방식으로 수정하고 상태 변경 요청의 위조 방지 토큰 규칙을 2절에 추가한다. 둘째, 지급정지 금액 개념과 출금 가능 금액 정의를 ERD와 요구사항명세서 FR-TXN-002에 추가한다. 셋째, 발신함 테이블을 ERD에 추가한다.

네 건 모두 구현 착수 전에 끝낸다.
