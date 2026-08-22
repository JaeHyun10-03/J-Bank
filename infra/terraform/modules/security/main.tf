# 인프라아키텍처 문서 5.2절 "계층 간 화이트리스트" 그대로: ALB는 443만 인터넷에 열고,
# WAS는 ALB로부터만, DB/Redis는 WAS로부터만 받는다. 관리존→DB존 직접 접속은 점검
# 목적의 예외라 기본값 off(enable_mgmt_db_access)로 상시 차단한다.

resource "aws_security_group" "alb" {
  name_prefix = "jbank-${var.environment}-alb-"
  description = "ALB - allows only 443 inbound from the internet"
  vpc_id      = var.vpc_id

  tags = merge(var.tags, { Name = "jbank-${var.environment}-alb-sg" })

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_security_group_rule" "alb_ingress_https" {
  type              = "ingress"
  security_group_id = aws_security_group.alb.id
  from_port         = 443
  to_port           = 443
  protocol          = "tcp"
  cidr_blocks       = ["0.0.0.0/0"]
  description       = "internet to ALB HTTPS"
}

resource "aws_security_group_rule" "alb_egress_to_was" {
  type                     = "egress"
  security_group_id        = aws_security_group.alb.id
  from_port                = var.app_port
  to_port                  = var.app_port
  protocol                 = "tcp"
  source_security_group_id = aws_security_group.was.id
  description              = "ALB to WAS app port"
}

resource "aws_security_group" "was" {
  name_prefix = "jbank-${var.environment}-was-"
  description = "WAS (EKS worker nodes) - inbound only from ALB"
  vpc_id      = var.vpc_id

  tags = merge(var.tags, { Name = "jbank-${var.environment}-was-sg" })

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_security_group_rule" "was_ingress_from_alb" {
  type                     = "ingress"
  security_group_id        = aws_security_group.was.id
  from_port                = var.app_port
  to_port                  = var.app_port
  protocol                 = "tcp"
  source_security_group_id = aws_security_group.alb.id
  description              = "ALB to WAS app port"
}

# WAS는 NAT 경유로 외부 API·패키지 레지스트리를 호출해야 해서 아웃바운드는 전체 허용한다.
# 인바운드가 ALB로만 좁혀져 있어 방어의 핵심은 인바운드 쪽이다.
resource "aws_security_group_rule" "was_egress_all" {
  type              = "egress"
  security_group_id = aws_security_group.was.id
  from_port         = 0
  to_port           = 0
  protocol          = "-1"
  cidr_blocks       = ["0.0.0.0/0"]
  description       = "WAS full egress (via NAT Gateway)"
}

resource "aws_security_group" "db" {
  name_prefix = "jbank-${var.environment}-db-"
  description = "RDS PostgreSQL - inbound only from WAS"
  vpc_id      = var.vpc_id

  tags = merge(var.tags, { Name = "jbank-${var.environment}-db-sg" })

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_security_group_rule" "db_ingress_from_was" {
  type                     = "ingress"
  security_group_id        = aws_security_group.db.id
  from_port                = 5432
  to_port                  = 5432
  protocol                 = "tcp"
  source_security_group_id = aws_security_group.was.id
  description              = "WAS to RDS PostgreSQL"
}

resource "aws_security_group_rule" "db_ingress_from_mgmt" {
  count = var.enable_mgmt_db_access ? 1 : 0

  type                     = "ingress"
  security_group_id        = aws_security_group.db.id
  from_port                = 5432
  to_port                  = 5432
  protocol                 = "tcp"
  source_security_group_id = aws_security_group.mgmt.id
  description              = "mgmt zone to RDS PostgreSQL (maintenance exception, not always-on)"
}

resource "aws_security_group" "redis" {
  name_prefix = "jbank-${var.environment}-redis-"
  description = "ElastiCache Redis - inbound only from WAS"
  vpc_id      = var.vpc_id

  tags = merge(var.tags, { Name = "jbank-${var.environment}-redis-sg" })

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_security_group_rule" "redis_ingress_from_was" {
  type                     = "ingress"
  security_group_id        = aws_security_group.redis.id
  from_port                = 6379
  to_port                  = 6379
  protocol                 = "tcp"
  source_security_group_id = aws_security_group.was.id
  description              = "WAS to ElastiCache Redis"
}

resource "aws_security_group" "mgmt" {
  name_prefix = "jbank-${var.environment}-mgmt-"
  description = "mgmt zone (SSM Session Manager) - no inbound, SSM egress only"
  vpc_id      = var.vpc_id

  tags = merge(var.tags, { Name = "jbank-${var.environment}-mgmt-sg" })

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_security_group_rule" "mgmt_egress_https" {
  type              = "egress"
  security_group_id = aws_security_group.mgmt.id
  from_port         = 443
  to_port           = 443
  protocol          = "tcp"
  cidr_blocks       = ["0.0.0.0/0"]
  description       = "for SSM VPC endpoint access (endpoints added in compute phase)"
}
