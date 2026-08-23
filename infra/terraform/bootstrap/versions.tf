terraform {
  required_version = ">= 1.9.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    tls = {
      source  = "hashicorp/tls"
      version = "~> 4.0"
    }
  }

  # 이 스택 자체는 로컬 상태로 관리한다 — envs/*가 쓸 원격 상태 저장소(S3+DynamoDB)를
  # 만드는 부트스트랩이라, 만들 대상을 자기 자신의 backend로 쓸 수 없는 닭-달걀 문제가
  # 있다. 한 번 apply한 뒤로는 거의 안 바뀌는 스택이라 로컬 상태로도 충분하다고 판단.
}

provider "aws" {
  region = var.aws_region
}
