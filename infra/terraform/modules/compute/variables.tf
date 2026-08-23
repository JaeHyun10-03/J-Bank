variable "environment" {
  description = "환경 이름(dev/prod). 리소스 이름과 태그에 쓰인다."
  type        = string
}

variable "github_repository" {
  description = "GitHub OIDC 신뢰 정책에 쓸 \"owner/repo\" 형식 저장소 이름. backend-cd.yml의 AWS_ECR_PUSH_ROLE_ARN이 이 역할을 가리키게 된다."
  type        = string
}

variable "create_github_oidc_provider" {
  description = "GitHub Actions OIDC provider를 이 스택에서 새로 만들지 여부. AWS 계정에 이미 다른 프로젝트가 만들어둔 provider가 있으면 false로 두고 github_oidc_provider_arn을 넘긴다(계정당 provider는 하나만 있어야 함)."
  type        = bool
  default     = true
}

variable "github_oidc_provider_arn" {
  description = "create_github_oidc_provider=false일 때 기존 provider ARN."
  type        = string
  default     = null
}

variable "ecr_untagged_image_expiry_days" {
  description = "ECR untagged 이미지 보존 일수."
  type        = number
  default     = 14
}

variable "ecr_max_image_count" {
  description = "ECR 리포지토리에 보관할 최대 이미지 수(태그 여부 무관, 오래된 것부터 정리)."
  type        = number
  default     = 20
}

variable "vpc_id" {
  description = "EKS를 배치할 VPC id."
  type        = string
}

variable "was_subnet_ids" {
  description = "EKS 워커노드를 배치할 WAS(private) 서브넷 id 목록."
  type        = list(string)
}

variable "was_security_group_id" {
  description = "EKS 워커노드에 붙일 보안그룹 id(security 모듈의 was_sg_id)."
  type        = string
}

variable "eks_cluster_version" {
  description = "EKS 쿠버네티스 버전."
  type        = string
  default     = "1.30"
}

variable "eks_endpoint_public_access" {
  description = "EKS API 서버 퍼블릭 엔드포인트 허용 여부. 시연 목적상 기본 true — 실제 운영이면 false로 두고 관리 서브넷의 SSM 경유나 VPN으로 좁힌다(인프라아키텍처 문서 5.2절 관리존 취지)."
  type        = bool
  default     = true
}

variable "node_instance_types" {
  description = "EKS 관리형 노드그룹 인스턴스 타입."
  type        = list(string)
  default     = ["t3.medium"]
}

variable "node_group_min_size" {
  type    = number
  default = 2
}

variable "node_group_max_size" {
  type    = number
  default = 4
}

variable "node_group_desired_size" {
  type    = number
  default = 2
}

variable "tags" {
  description = "모든 리소스에 공통으로 붙일 태그."
  type        = map(string)
  default     = {}
}
