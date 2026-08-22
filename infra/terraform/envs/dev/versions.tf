terraform {
  required_version = ">= 1.9.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }

  # 상태 파일은 지금은 로컬. 원격 상태(S3+DynamoDB 잠금)는 W6 금요일분에서
  # 이 backend 블록을 "s3"로 교체한다(인프라아키텍처 문서 10절).
  # backend "local" {}
}

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = local.common_tags
  }
}
