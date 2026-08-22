output "vpc_id" {
  value = module.network.vpc_id
}

output "rds_endpoint" {
  value = module.data.rds_endpoint
}

output "redis_endpoint" {
  value = module.data.redis_endpoint
}
