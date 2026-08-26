terraform {
  required_version = ">= 1.9.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    helm = {
      source  = "hashicorp/helm"
      version = "~> 3.2"
    }
    kubectl = {
      source  = "alekc/kubectl"
      version = "~> 2.4"
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

# helm/kubectl provider 인증은 EKS 클러스터 리소스를 참조한다 — 데이터소스
# 대신 exec 플러그인(aws eks get-token)을 쓰는 이유는, 클러스터를 처음
# 만드는 apply에서 데이터소스가 plan 시점에 아직 없는 클러스터를 읽으려다
# 실패하는 문제를 피하기 위해서다(terraform-aws-modules/eks 공식 예제
# 패턴). aws CLI가 apply를 실행하는 환경에 설치돼 있어야 한다.
provider "helm" {
  kubernetes = {
    host                   = module.compute.eks_cluster_endpoint
    cluster_ca_certificate = base64decode(module.compute.eks_cluster_ca_certificate)

    exec = {
      api_version = "client.authentication.k8s.io/v1beta1"
      command     = "aws"
      args        = ["eks", "get-token", "--cluster-name", module.compute.eks_cluster_name]
    }
  }
}

provider "kubectl" {
  host                   = module.compute.eks_cluster_endpoint
  cluster_ca_certificate = base64decode(module.compute.eks_cluster_ca_certificate)
  load_config_file       = false

  exec {
    api_version = "client.authentication.k8s.io/v1beta1"
    command     = "aws"
    args        = ["eks", "get-token", "--cluster-name", module.compute.eks_cluster_name]
  }
}
