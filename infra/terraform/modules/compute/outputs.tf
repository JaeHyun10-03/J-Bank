output "ecr_repository_url" {
  value = aws_ecr_repository.jbank_api.repository_url
}

output "ecr_push_role_arn" {
  description = "backend-cd.yml의 secrets.AWS_ECR_PUSH_ROLE_ARN에 등록할 값."
  value       = aws_iam_role.ecr_push.arn
}

output "eks_cluster_name" {
  value = module.eks.cluster_name
}

output "eks_cluster_endpoint" {
  value = module.eks.cluster_endpoint
}

output "eks_oidc_provider_arn" {
  description = "IRSA(파드용 IAM 역할)에 쓸 클러스터 OIDC provider ARN."
  value       = module.eks.oidc_provider_arn
}
