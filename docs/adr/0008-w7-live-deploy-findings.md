# ADR 0008: W7 실제 클러스터 배포에서 발견한 인프라 결함 2건

## 상태

승인됨. 2026-08-31, W7 실측 검증 중 발견 즉시 수정.

## 배경

W7 완료 기준(무중단 배포 k6 검증, 사가 보상 트랜잭션 확인)을 실제로 만족시키려면
클러스터에 애플리케이션이 떠 있어야 한다. EKS를 기동하고 처음으로 `jbank-api` 파드를
실행해보니 두 가지 결함이 연달아 드러났다 — 둘 다 "helm template/terraform plan
같은 정적 검증으로는 못 잡고 실제로 띄워봐야만 드러나는" 종류다.

## 결함 1 — 파드가 RDS/ElastiCache에 연결하지 못함

### 증상

`jbank-api` 파드가 `FlywaySqlException: Unable to obtain connection from database`로
기동에 실패했다. `SocketTimeoutException`이라 자격증명 문제가 아니라 네트워크
경로 자체가 막힌 것이었다.

### 원인

`modules/compute/eks.tf`가 `cluster_additional_security_group_ids =
[var.was_security_group_id]`로 was_sg를 클러스터에 붙였는데, 이건 EKS 컨트롤
플레인용 ENI에만 적용되고 워커노드 EC2 인스턴스 자체의 ENI에는 안 붙는다.
`aws ec2 describe-instances`로 실제 노드를 확인해보니 노드는
`terraform-aws-modules/eks`가 자동 생성한 노드 전용 보안그룹
(`jbank-dev-node-...`)만 갖고 있었다. `modules/security`의
`db_ingress_from_was`/`redis_ingress_from_was` 규칙은 was_sg 기준으로만
인그레스를 열어뒀으니, 파드가 실제로 내는 트래픽(노드 보안그룹을 달고 나감)은
RDS/ElastiCache 보안그룹의 어떤 규칙과도 매치되지 않았다.

### 결정

`modules/compute`에 `eks_node_security_group_id` output을 추가하고,
`envs/dev/main.tf`(루트)에서 `aws_security_group_rule`로 db_sg/redis_sg에
그 노드 보안그룹으로부터의 인그레스를 직접 연결했다. security 모듈과 compute
모듈 둘 다 서로를 모르는 채로 설계돼 있어(순환참조 회피) 이 연결은 두 모듈을
아는 루트에서만 가능하다 — WAF↔ALB 연결과 같은 패턴이다.

기존 `db_ingress_from_was`/`redis_ingress_from_was` 규칙은 지우지 않았다.
당장은 무의미하지만, 나중에 노드가 아닌 다른 워크로드(예: 관리용 EC2, Lambda)가
was_sg를 달고 DB에 접근해야 하는 경우를 위해 남겨둔다.

## 결함 2 — 존재하지 않는 Kafka 호스트명이 컨텍스트 기동 자체를 막음

### 증상

결함 1을 고치고 나서도 파드가 뜨지 않았다 — 이번엔
`ApplicationContextException: Failed to start bean
'internalKafkaListenerEndpointRegistry'`, 근본 원인은
`ConfigException: No resolvable bootstrap urls given in bootstrap.servers`.

### 원인

`modules/secrets`의 `kafka_bootstrap_servers` 기본값을 원래
`"kafka-not-provisioned:9092"`(MSK가 아직 없다는 걸 명시하는 자리표시자)로
뒀는데, 이게 실제로는 잘못된 가정이었다. "Kafka가 안 떠 있으면 컨슈머가 연결을
계속 재시도하며 로그만 남기고 API 자체는 멀쩡할 것"이라고 예상했지만, 실제
동작은 그게 아니었다 — `@KafkaListener` 컨슈머 컨테이너 시작은 Spring의
`SmartLifecycle` 단계라 `ApplicationContext.refresh()`를 막고, DNS 자체가
안 풀리는 호스트명은 `KafkaConsumer` 생성자에서 `ClientUtils.
parseAndValidateAddresses()`가 즉시 예외를 던진다 — "연결은 시도했지만 응답이
없다"와 "애초에 주소를 못 만든다"는 Kafka 클라이언트 안에서 완전히 다른
실패 경로였다.

### 결정

기본값을 `"localhost:9092"`로 바꿨다. `localhost`는 항상 DNS가 풀리고, 그
자리에 아무것도 안 떠 있으면 연결 거부(connection refused)가 나는데 이건
Kafka 클라이언트가 비동기로 재시도하며 넘어가는 정상 실패 경로다 — 컨텍스트
기동을 막지 않는다. 컨슈머는 계속 못 뜨니 발신함 기반 알림 발송(outbox
publisher)은 동작하지 않지만, 그 외 API는 이 값과 무관하게 정상 동작한다
(발신함 패턴이 DB 트랜잭션과 발행을 분리해두었기 때문 — 트랜잭션 이벤트는
DB에 쌓이고, Kafka가 붙으면 그때 발행된다).

## 근거

두 결함 모두 "말로는 그럴듯했지만 실제로 안 맞았던" 가정이었다는 공통점이
있다 — cluster_additional_security_group_ids가 노드까지 커버할 거라는 가정,
Kafka 미연결이 API를 막지 않을 거라는 가정. `terraform plan`/`helm
template`/`terraform validate` 어느 것도 이 둘을 잡지 못했다 — 실제 파드를
띄우고 로그를 본 게 유일한 검증 방법이었다. 이게 이번 주 "무중단 배포
k6 검증"을 스킵하지 않고 실제로 클러스터를 띄워서 확인해야 했던 이유다.

## 영향

- `modules/compute/outputs.tf`: `eks_node_security_group_id` output 추가.
- `envs/dev/main.tf`: `db_ingress_from_eks_nodes`/`redis_ingress_from_eks_nodes`
  보안그룹 규칙 추가.
- `modules/secrets/variables.tf`: `kafka_bootstrap_servers` 기본값을
  `localhost:9092`로 변경.
- 두 수정 모두 실제 클러스터에 즉시 apply해 파드가 `Running 1/1`로 뜨는 것,
  `/actuator/health/readiness`·`/liveness`가 200을 반환하는 것까지 확인했다.
