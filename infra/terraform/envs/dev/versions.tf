terraform {
  required_version = ">= 1.9.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }

  # 원격 상태(S3+DynamoDB 잠금, 인프라아키텍처 문서 10절). bucket은 계정 ID가 들어가
  # 하드코딩하지 않는다 — backend 블록은 변수 보간이 안 되므로, bootstrap 스택을 먼저
  # apply한 뒤 그 출력값으로 init 시점에 채운다:
  #   terraform init -backend-config="bucket=$(terraform -chdir=../../bootstrap output -raw state_bucket)"
  backend "s3" {
    key            = "envs/dev/terraform.tfstate"
    region         = "ap-northeast-2"
    dynamodb_table = "jbank-terraform-locks"
    encrypt        = true
  }
}

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = local.common_tags
  }
}
