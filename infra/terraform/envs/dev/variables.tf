variable "aws_region" {
  description = "AWS 리전. 인프라아키텍처 문서 3절 — 서울(ap-northeast-2)."
  type        = string
  default     = "ap-northeast-2"
}

variable "vpc_cidr" {
  type    = string
  default = "10.0.0.0/16"
}

variable "azs" {
  description = "가용영역 목록. 최소 2개."
  type        = list(string)
  default     = ["ap-northeast-2a", "ap-northeast-2c"]
}

variable "db_master_password" {
  description = "RDS 마스터 비밀번호. terraform.tfvars(gitignore 대상)나 -var로 넘긴다."
  type        = string
  sensitive   = true
}

variable "github_repository" {
  description = "GitHub Actions OIDC 신뢰 정책에 쓸 \"owner/repo\"."
  type        = string
  default     = "JaeHyun10-03/J-Bank"
}
