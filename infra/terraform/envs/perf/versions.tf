terraform {
  required_version = ">= 1.9.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }

  # 측정할 때만 만들고 지우는 일회성 환경이라 원격 상태를 두지 않는다(로컬 state, gitignore 대상).
}

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = local.common_tags
  }
}
