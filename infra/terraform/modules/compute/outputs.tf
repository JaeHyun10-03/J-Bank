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

output "eks_cluster_ca_certificate" {
  description = "helm/kubectl provider가 클러스터 TLS 검증에 쓰는 base64 CA 인증서(gitops 모듈)."
  value       = module.eks.cluster_certificate_authority_data
}

output "eks_oidc_provider_arn" {
  description = "IRSA(파드용 IAM 역할)에 쓸 클러스터 OIDC provider ARN."
  value       = module.eks.oidc_provider_arn
}

output "eks_oidc_issuer_url" {
  description = "IRSA 신뢰 정책의 sub 조건에 쓸 OIDC issuer URL(https:// 접두어 포함)."
  value       = module.eks.cluster_oidc_issuer_url
}

output "alb_arn" {
  value = aws_lb.this.arn
}

output "alb_dns_name" {
  value = aws_lb.this.dns_name
}

output "alb_placeholder_target_group_arn" {
  value = aws_lb_target_group.placeholder.arn
}
