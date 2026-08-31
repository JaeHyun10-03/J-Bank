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
# 값을 비워두면 애플리케이션이 부팅 시 Kafka 컨슈머/프로듀서 연결을
# 계속 재시도하며 로그만 남긴다 — 이체·조회 API 자체는 이 값과 무관하게
# 정상 동작한다(발신함 패턴이 DB 트랜잭션과 발행을 분리해두었기 때문).
variable "kafka_bootstrap_servers" {
  type    = string
  default = "kafka-not-provisioned:9092"
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
