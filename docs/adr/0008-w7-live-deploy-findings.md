# ADR 0008: W7 실제 클러스터 배포에서 발견한 인프라 연결 결함

## 상태

**폐기됨(2026-09-17, ADR 0010).** EKS·RDS 구성을 제거해 이 결함이 있던 리소스 자체가 사라졌다. "클러스터 보안그룹 ≠ 노드 보안그룹"이라는 교훈은 유효하므로 기록으로 유지한다.

원문 상태: 승인됨. 2026-08-31, W7 실측 검증 중 발견 즉시 수정.

## 배경

W7 완료 기준(무중단 배포 k6 검증)을 실제로 만족시키려면
클러스터에 애플리케이션이 떠 있어야 한다. EKS를 기동하고 처음으로 `jbank-api` 파드를
실행해보니 연결 결함이 드러났다 — "helm template/terraform plan
같은 정적 검증으로는 못 잡고 실제로 띄워봐야만 드러나는" 문제다.

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

## 근거

정적 검증만으로 실제 노드의 네트워크 경로를 확인할 수 없어, 클러스터에서 DB 연결과 readiness/liveness 응답을 검증했다.
