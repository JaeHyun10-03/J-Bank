# 2026-08-31 W7 나머지 전부 — product 모듈 분리, 사가, FDS, 실측 검증, v1.0.0

`todo/W7.md` 남은 전부(목~일요일분: Secrets Manager+ESO 배선 마무리, product
모듈 독립 배포 분리+사가, FDS, 실제 클러스터 무중단 배포 검증, 문서 정리,
`v1.0.0` 태그)를 한 세션에 몰아서 진행했다. 실제 EKS 클러스터를 띄워서
전부 실측 검증까지 했다 — 정적 검증(`helm template`, `terraform plan`)만
으로는 못 잡는 결함 두 건이 실측에서만 드러났다(아래).

## 인프라 기동

`envs/dev` `demo` 워크스페이스로 EKS·RDS·ElastiCache·ALB 88개 리소스를
2단계로 기동했다 — `kubectl`/`helm` provider가 클러스터 존재를 전제하는
chicken-and-egg 문제 때문에 `module.compute`(EKS)까지 먼저 apply하고,
그 다음 `module.gitops`(ArgoCD)·`module.secrets`(Secrets Manager+ESO)를
apply했다.

## 실측 배포 결함 2건 (ADR 0008)

파드를 처음 띄워보니 RDS/ElastiCache에 연결을 못 했다 — `was_sg`가
`cluster_additional_security_group_ids`로 클러스터 ENI에는 붙지만
워커노드 EC2 자체의 ENI에는 안 붙는다는 걸 실측으로 발견했다. 노드
전용 보안그룹을 새로 output으로 빼서 루트에서 DB/Redis 인그레스에
직접 연결해 고쳤다.

그다음엔 존재하지 않는 Kafka 호스트명(`kafka-not-provisioned:9092`)이
`KafkaConsumer` 생성자에서 DNS 해석 실패로 즉시 예외를 던져
`ApplicationContext` 기동 자체를 막는 걸 발견했다 — "Kafka 없으면
재시도만 하고 API는 멀쩡할 것"이라던 원래 가정이 틀렸다. `localhost:9092`
(항상 DNS는 풀리고 연결 거부는 비동기 처리)로 바꿔서 고쳤다.

## Secrets Manager + ESO 마무리

목요일 몫이었던 이 항목이 사실 지난 세션엔 코드까지만 있고 실제
apply·검증이 안 돼 있었다 — 이번에 실제로 apply해서 `ExternalSecret`이
Secrets Manager 값을 K8s Secret으로 정확히 동기화하는 것, ESO IRSA
권한이 실제로 동작하는 것까지 확인했다.

## product 모듈 독립 배포 분리 (ADR 0007)

가장 큰 작업. `apps/jbank-product`를 `apps/jbank-api`와 완전히 별개인
Gradle 프로젝트로 새로 만들고 상품·계약 도메인을 이관했다. 두 프로젝트가
서로에 대한 `project()` 의존이 전혀 없다 — ArchUnit보다 강한 경계
강제다.

상품가입은 원래 출금을 하지도 않는 상태였다(!). 모듈을 쪼개면서 이
갭을 그대로 둘 수 없어서 "계약 생성(PENDING) → jbank-api에 출금 요청
(내부 API) → 계약 확정(ACTIVE)" 오케스트레이션 사가를 새로 짰다.
확정이 실패하면 보상 거래(출금 롤백)를 호출하고 계약을 FAILED로
남긴다. self-invocation 때문에 `@Transactional`이 무력화되는 문제를
별도 빈(`ProductContractSagaSteps`)으로 분리해 피했고,
`ProductSubscriptionSagaIntegrationTest`(WireMock으로 jbank-api 대역)로
정상/출금실패/확정실패(보상) 세 경로를 전부 검증했다.

이자 지급 배치 잡이 `ProductContractRepository`를 직접 참조하던
역방향 의존도 이 과정에서 실제로 발견했다 — ArchUnit 규칙 대상이
아니었던 사각지대다. 조회·이자계산은 product로, 계좌 입금은 api에
남기는 식으로 나눴고, 원자성이 깨진 대가로 idempotencyKey를
계약당 1개로 바꿔 재시도 안전성을 다시 확보했다.

## 실측 배포 중 CSRF 버그 발견

두 서비스를 다 띄우고 jbank-product 파드 안에서 jbank-api 내부 API로
직접 curl을 날려봤더니 403이 났다 — `CsrfDoubleSubmitFilter`가
`/internal/**` 경로를 예외 처리하지 않아서 서비스 간 POST/PATCH 호출을
전부 막고 있었다. 브라우저 쿠키가 없는 서비스 간 호출엔 이중제출 개념
자체가 성립하지 않는다. 두 서비스 다 고치고 회귀 테스트를 추가했다.

## FDS 룰 기반 이상거래 탐지 (ADR 0009)

CTR 배치와 같은 패턴으로 `fdsDetectionJob`을 만들었다. 세 룰(단일
거래 임계금액, 짧은 시간 내 반복 이체, 심야 고액 이체) 다 자바에서
계산한다 — "짧은 시간 내 반복"은 계좌별 슬라이딩 윈도우가 필요해 SQL
집계만으론 안 된다. 심야 판정은 `ZoneId.of("Asia/Seoul")`로 고정했다 —
컨테이너 기본 타임존에 맡기면 배포 환경마다 "심야"의 의미가 달라진다.
API-022는 `AuditLogController`와 같은 선례(운영자 역할 모델 없어 인증된
고객이면 누구나 호출 가능, `ponytail:` 표시)를 따랐다.

## 무중단 배포 실측 검증

`kubectl port-forward`가 특정 파드에 고정돼 롤링 업데이트 도중 엔드포인트
전환을 못 따라간다는 걸 이 과정에서 알았다 — 클러스터 내부 k6 Job으로
바꿔서 실제 kube-proxy 로드밸런싱을 거치게 했다. 이미지 태그를 갱신하고
ArgoCD 강제 동기화로 롤아웃을 트리거한 뒤, 새 파드 생성→Ready→이전 파드
Terminating까지 전체 수명주기를 5분짜리 k6 실행 창 안에 담아 실패
요청 0건(총 4502건)을 확인했다 — 이번 주 완료 기준의 절반. 나머지
절반(사가 보상 트랜잭션 원장 기록)은 `ProductSubscriptionSagaIntegrationTest`가
검증한다.

이 과정에서 dev 노드(1대, max-pods=17)가 ArgoCD·ESO·기존 워크로드만으로
이미 꽉 차 롤링 업데이트에 필요한 서지 여유가 없다는 것도 발견했다 —
CronJob 파드 정리와 CoreDNS 레플리카 임시 조정(검증 후 원복)으로
우회했다. 상세 절차와 수치는 `perf/README.md` W7 절 참고.

## 문서 정리

ADR 9개 전체를 훑어 이번 세션 결정(분산락, GitOps 이미지 태그, 모듈
분리, 실측 결함, FDS)이 다 기록됐는지 확인했다. README를 W7 기준으로
갱신했다 — 시스템 구성도에 jbank-product·ArgoCD·Secrets Manager 추가,
7주 로드맵을 완료 상태로, 성능 추이(W2/W5/W6 xychart)와 W7 무중단
배포 결과를 새 절로 추가했다.

## 커밋

목요일 몫 이후 이 세션에서 만든 주요 커밋들(시간순, 총 20여 개 중
논리적 단위):

1. `fix(infra)`: 노드 보안그룹·Kafka 호스트명 결함 수정 (ADR 0008)
2. `feat(backend)`: product 모듈 독립 배포 분리 + 상품가입 사가 (ADR 0007)
3. `fix(backend)`: 내부 API 호출 HTTP/1.1 고정
4. `fix(backend)`: CSRF 이중제출 필터 내부 API 예외 처리
5. `feat(backend)`: FDS 룰 기반 이상거래 탐지 (ADR 0009)
6. `feat(infra)`: jbank-product 배포 인프라 배선(Docker·Helm·ECR·ArgoCD·CI)
7. `docs(perf)`, `docs(readme)`, `docs(todo)`: 실측 검증 결과·문서 정리

## v1.0.0

이 devlog 커밋 직후 `v1.0.0` 태그를 찍는다 — 구현계획 문서가 정의한
7주 로드맵(Phase 1~3)을 계획대로 마감한다.
