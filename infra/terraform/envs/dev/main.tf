locals {
  environment = "dev"

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
  multi_az                = true
  deletion_protection     = false # dev는 파괴·재생성을 반복하므로(토요일분 검증) false 유지
  skip_final_snapshot     = true
  tags                    = local.common_tags
}
