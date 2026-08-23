output "ecr_repository_url" {
  value = aws_ecr_repository.jbank_api.repository_url
}

output "ecr_push_role_arn" {
  description = "backend-cd.yml의 secrets.AWS_ECR_PUSH_ROLE_ARN에 등록할 값."
  value       = aws_iam_role.ecr_push.arn
}
