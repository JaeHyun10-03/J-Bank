output "alb_sg_id" {
  value = aws_security_group.alb.id
}

output "was_sg_id" {
  value = aws_security_group.was.id
}

output "db_sg_id" {
  value = aws_security_group.db.id
}

output "redis_sg_id" {
  value = aws_security_group.redis.id
}

output "mgmt_sg_id" {
  value = aws_security_group.mgmt.id
}

output "waf_web_acl_arn" {
  value = aws_wafv2_web_acl.alb.arn
}
