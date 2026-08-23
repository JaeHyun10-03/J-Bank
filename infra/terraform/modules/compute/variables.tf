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

variable "tags" {
  description = "모든 리소스에 공통으로 붙일 태그."
  type        = map(string)
  default     = {}
}
