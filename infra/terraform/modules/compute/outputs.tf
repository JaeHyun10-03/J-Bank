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

output "eks_node_security_group_id" {
  description = <<-EOT
    워커노드 EC2 인스턴스에 실제로 붙는 보안그룹 ID. security 모듈의 was_sg는
    `cluster_additional_security_group_ids`로 클러스터 ENI에는 붙지만 노드
    자체의 ENI에는 안 붙는다 — 그래서 RDS/ElastiCache 인그레스 규칙이 was_sg
    기준으로만 있으면 파드가 DB에 실제로 연결을 못 한다(2026-08-31 실측
    배포에서 발견, docs/adr/0008 참고). 이 출력값을 루트에서 db_sg/redis_sg
    인그레스에 추가로 연결한다.
  EOT
  value       = module.eks.node_security_group_id
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
