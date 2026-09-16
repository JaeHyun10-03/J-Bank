output "vpc_id" {
  value = module.network.vpc_id
}

output "rds_endpoint" {
  value = module.data.rds_endpoint
}

output "redis_endpoint" {
  value = module.data.redis_endpoint
}

output "ecr_repository_url" {
  value = module.compute.ecr_repository_url
}

output "ecr_push_role_arn" {
  description = "backend-cd.yml의 secrets.AWS_ECR_PUSH_ROLE_ARN에 등록할 값."
  value       = module.compute.ecr_push_role_arn
}

output "eks_cluster_name" {
  value = module.compute.eks_cluster_name
}

output "alb_dns_name" {
  value = module.compute.alb_dns_name
}
