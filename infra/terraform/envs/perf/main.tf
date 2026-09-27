locals {
  common_tags = {
    Project     = "j-bank"
    Environment = "perf"
    ManagedBy   = "terraform"
  }
}

module "perf" {
  source = "../../modules/perf"

  availability_zone     = var.availability_zone
  loadgen_instance_type = var.loadgen_instance_type
  github_repository     = var.github_repository
  git_ref               = var.git_ref
  tags                  = local.common_tags
}
