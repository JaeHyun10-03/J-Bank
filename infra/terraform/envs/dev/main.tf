locals {
  environment = "dev"

  common_tags = {
    Project     = "j-bank"
    Environment = local.environment
    ManagedBy   = "terraform"
  }
}

module "ec2" {
  source = "../../modules/ec2"

  environment              = local.environment
  instance_type            = var.instance_type
  github_repository        = var.github_repository
  github_owner_id          = var.github_owner_id
  github_repository_id     = var.github_repository_id
  github_oidc_provider_arn = var.github_oidc_provider_arn
  tags                     = local.common_tags
}
