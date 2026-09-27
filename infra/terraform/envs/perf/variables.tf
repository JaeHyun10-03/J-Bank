variable "aws_region" {
  type    = string
  default = "ap-northeast-2"
}

variable "availability_zone" {
  description = "운영 인스턴스(jbank-dev-app)와 같은 AZ."
  type        = string
  default     = "ap-northeast-2d"
}

variable "loadgen_instance_type" {
  type    = string
  default = "c7i.large"
}

variable "github_repository" {
  type    = string
  default = "JaeHyun10-03/J-Bank"
}

variable "git_ref" {
  description = "perf 구성 파일이 있는 브랜치. 원격에 push되어 있어야 한다."
  type        = string
  default     = "perf/ec2-load-test"
}
