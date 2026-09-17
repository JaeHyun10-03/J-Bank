variable "aws_region" {
  type    = string
  default = "ap-northeast-2"
}

variable "github_repository" {
  description = "GitHub OIDC 신뢰 정책에 쓸 \"owner/repo\"."
  type        = string
  default     = "JaeHyun10-03/J-Bank"
}

variable "github_owner_id" {
  description = "GitHub 소유자 숫자 ID (OIDC sub 클레임용). https://api.github.com/repos/JaeHyun10-03/J-Bank 의 owner.id."
  type        = number
  default     = 174772263
}

variable "github_repository_id" {
  description = "GitHub 저장소 숫자 ID (OIDC sub 클레임용). 같은 응답의 id."
  type        = number
  default     = 1314857440
}
