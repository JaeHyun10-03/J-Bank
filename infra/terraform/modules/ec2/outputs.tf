output "instance_id" {
  value = aws_instance.app.id
}

output "public_ip" {
  description = "api.j-bank.site, grafana.j-bank.site A 레코드에 넣을 EIP."
  value       = aws_eip.app.public_ip
}

output "deploy_role_arn" {
  description = "backend-cd.yml의 secrets.AWS_DEPLOY_ROLE_ARN에 등록할 값."
  value       = aws_iam_role.deploy.arn
}
