variable "aws_region" {
  description = "AWS 리전. 서울(ap-northeast-2)."
  type        = string
  default     = "ap-northeast-2"
}

variable "instance_type" {
  type    = string
  default = "t3.small"
}

variable "github_repository" {
  description = "GitHub Actions OIDC 신뢰 정책과 user_data git clone에 쓸 \"owner/repo\"."
  type        = string
  default     = "JaeHyun10-03/J-Bank"
}

variable "github_oidc_provider_arn" {
  description = "bootstrap 스택의 github_oidc_provider_arn 출력값. terraform.tfvars나 -var로 넘긴다(bootstrap을 먼저 apply해야 나온다)."
  type        = string
}
