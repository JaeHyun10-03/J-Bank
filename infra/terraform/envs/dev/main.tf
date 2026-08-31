# 비용 통제용 워크스페이스 분리(인프라아키텍처 문서 12절).
#   terraform workspace new always-on   # 상시구동 — Multi-AZ RDS, EKS 노드 2대
#   terraform workspace new demo        # 시연 — 단일 AZ RDS, EKS 노드 1대로 축소
#   terraform workspace select demo
# default 워크스페이스는 always-on과 동일하게 취급한다.
locals {
  environment = "dev"
  is_demo     = terraform.workspace == "demo"

  common_tags = {
    Project     = "j-bank"
    Environment = local.environment
    ManagedBy   = "terraform"
  }
}

module "network" {
  source = "../../modules/network"

  environment        = local.environment
  vpc_cidr           = var.vpc_cidr
  azs                = var.azs
  single_nat_gateway = true # 개발 환경은 비용 절감 우선(인프라아키텍처 문서 12절)
  tags               = local.common_tags
}

module "security" {
  source = "../../modules/security"

  environment           = local.environment
  vpc_id                = module.network.vpc_id
  enable_mgmt_db_access = false
  tags                  = local.common_tags
}

module "data" {
  source = "../../modules/data"

  environment             = local.environment
  db_subnet_ids           = module.network.db_subnet_ids
  db_security_group_id    = module.security.db_sg_id
  redis_security_group_id = module.security.redis_sg_id
  db_master_password      = var.db_master_password
  multi_az                = !local.is_demo
  deletion_protection     = false # dev는 파괴·재생성을 반복하므로(토요일분 검증) false 유지
  skip_final_snapshot     = true
  tags                    = local.common_tags
}

module "compute" {
  source = "../../modules/compute"

  environment              = local.environment
  github_repository        = var.github_repository
  github_oidc_provider_arn = var.github_oidc_provider_arn
  vpc_id                   = module.network.vpc_id
  was_subnet_ids           = module.network.was_subnet_ids
  public_subnet_ids        = module.network.public_subnet_ids
  was_security_group_id    = module.security.was_sg_id
  alb_security_group_id    = module.security.alb_sg_id
  node_group_desired_size  = local.is_demo ? 1 : 2
  node_group_min_size      = local.is_demo ? 1 : 2
  tags                     = local.common_tags
}

module "gitops" {
  source = "../../modules/gitops"

  repo_url = "https://github.com/${var.github_repository}.git"
}

module "secrets" {
  source = "../../modules/secrets"

  environment        = local.environment
  aws_region         = var.aws_region
  oidc_provider_arn  = module.compute.eks_oidc_provider_arn
  oidc_issuer_url    = module.compute.eks_oidc_issuer_url
  db_address         = module.data.rds_address
  db_master_password = var.db_master_password
  redis_endpoint     = module.data.redis_endpoint
  tags               = local.common_tags
}

# WAF 웹 ACL을 여기서 ALB에 붙인다 — security 모듈은 ALB를 모르고 compute 모듈은
# WAF를 모르는 채로 각자 만들어두고, 둘 다 아는 루트에서만 연결한다(순환참조 회피).
resource "aws_wafv2_web_acl_association" "alb" {
  resource_arn = module.compute.alb_arn
  web_acl_arn  = module.security.waf_web_acl_arn
}
