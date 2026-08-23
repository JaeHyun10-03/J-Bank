variable "aws_region" {
  type    = string
  default = "ap-northeast-2"
}

variable "github_repository" {
  description = "GitHub OIDC 신뢰 정책에 쓸 \"owner/repo\"."
  type        = string
  default     = "JaeHyun10-03/J-Bank"
}
