variable "environment" {
  description = "환경 이름(dev/prod). 리소스 이름과 태그에 쓰인다."
  type        = string
}

variable "oidc_provider_arn" {
  description = "IRSA 신뢰 정책의 Federated principal — EKS 클러스터 OIDC provider ARN."
  type        = string
}

variable "oidc_issuer_url" {
  description = "IRSA 신뢰 정책의 sub 조건에 쓸 OIDC issuer URL(https:// 접두어 포함)."
  type        = string
}

variable "eso_namespace" {
  description = "External Secrets Operator 컨트롤러가 설치될 네임스페이스."
  type        = string
  default     = "external-secrets"
}

variable "eso_service_account" {
  description = "IRSA 역할을 붙일 ESO 컨트롤러 서비스어카운트 이름 — external-secrets Helm 차트 기본값과 맞춘다."
  type        = string
  default     = "external-secrets"
}

variable "eso_chart_version" {
  description = "external-secrets Helm 차트 버전."
  type        = string
  default     = "0.19.2"
}

variable "aws_region" {
  type = string
}

variable "db_address" {
  description = "RDS 엔드포인트(포트 제외) — module.data.rds_address."
  type        = string
}

variable "db_name" {
  type    = string
  default = "jbank"
}

variable "db_master_username" {
  type    = string
  default = "jbank_admin"
}

variable "db_master_password" {
  type      = string
  sensitive = true
}

variable "redis_endpoint" {
  description = "ElastiCache 엔드포인트(포트 제외) — module.data.redis_endpoint."
  type        = string
}

# MSK는 아직 프로비저닝하지 않았다(인프라아키텍처 문서 12절 Phase 2).
# 처음엔 "kafka-not-provisioned:9092" 같은 안 쓰는 호스트명을 넣어뒀는데,
# 2026-08-31 실측 배포에서 컨테이너가 아예 못 뜨는 걸 발견했다 — Kafka
# 컨슈머 컨테이너 시작이 SmartLifecycle 단계라 ApplicationContext refresh를
# 막는데, DNS 자체가 안 풀리는 호스트명은 KafkaConsumer 생성자에서 즉시
# ConfigException을 던져 그 자리에서 컨텍스트 기동이 실패한다(비동기로
# 재시도하며 로그만 남기는 "연결은 되는데 응답이 없는" 상황과 다르다).
# localhost는 항상 DNS가 풀리고 연결 거부(refused)는 비동기로 처리되므로
# 안전하다 — 그래도 컨슈머는 못 뜨니 알림 발송은 안 되지만, 그 외 API는
# 이 값과 무관하게 정상 동작한다(발신함 패턴이 DB 트랜잭션과 발행을
# 분리해두었기 때문. docs/adr/0008 참고).
variable "kafka_bootstrap_servers" {
  type    = string
  default = "localhost:9092"
}

# 짧은 키(dev/prod)로 AWS Secrets Manager 시크릿 이름을, namespace로
# ArgoCD Application이 실제 배포하는 K8s 네임스페이스를 결정한다.
# secret_name은 jbank-api Helm 차트의 values-{dev,prod}.yaml에 있는
# secretName 값과 정확히 일치해야 한다.
variable "namespaces" {
  type = map(object({
    namespace   = string
    secret_name = string
  }))
  default = {
    dev = {
      namespace   = "jbank-dev"
      secret_name = "jbank-api-secrets-dev"
    }
    prod = {
      namespace   = "jbank-prod"
      secret_name = "jbank-api-secrets-prod"
    }
  }
}

variable "tags" {
  type    = map(string)
  default = {}
}
