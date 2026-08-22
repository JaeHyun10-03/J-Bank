# 인프라아키텍처 문서 7절: RDS PostgreSQL Multi-AZ, ElastiCache Redis 단일 노드(Phase 1,
# 클러스터 모드 전환은 Phase 3). 둘 다 network 모듈의 db(isolated) 서브넷에 배치되고,
# security 모듈에서 만든 보안그룹으로 WAS 서브넷 트래픽만 받는다.

resource "aws_db_subnet_group" "this" {
  name       = "jbank-${var.environment}-db"
  subnet_ids = var.db_subnet_ids

  tags = merge(var.tags, { Name = "jbank-${var.environment}-db-subnet-group" })
}

resource "aws_db_instance" "this" {
  identifier     = "jbank-${var.environment}"
  engine         = "postgres"
  engine_version = var.db_engine_version

  instance_class    = var.db_instance_class
  allocated_storage = var.db_allocated_storage
  storage_type      = "gp3"
  storage_encrypted = true

  db_name  = var.db_name
  username = var.db_master_username
  password = var.db_master_password

  db_subnet_group_name   = aws_db_subnet_group.this.name
  vpc_security_group_ids = [var.db_security_group_id]

  multi_az                  = var.multi_az
  backup_retention_period   = var.backup_retention_days
  deletion_protection       = var.deletion_protection
  skip_final_snapshot       = var.skip_final_snapshot
  final_snapshot_identifier = var.skip_final_snapshot ? null : "jbank-${var.environment}-final"

  tags = merge(var.tags, { Name = "jbank-${var.environment}-rds" })
}

resource "aws_elasticache_subnet_group" "this" {
  name       = "jbank-${var.environment}-redis"
  subnet_ids = var.db_subnet_ids

  tags = merge(var.tags, { Name = "jbank-${var.environment}-redis-subnet-group" })
}

# Phase 1 단일 노드. OTP·갱신토큰 화이트리스트·로그인 실패 카운터는 만료 시간이 있는
# 휘발성 데이터라 유실돼도 재시도로 복구되는 성질이라(인프라아키텍처 문서 7절) 지금은
# 복제본 없이 단일 노드로 충분하다고 판단.
resource "aws_elasticache_cluster" "this" {
  cluster_id      = "jbank-${var.environment}-redis"
  engine          = "redis"
  engine_version  = var.redis_engine_version
  node_type       = var.redis_node_type
  num_cache_nodes = 1
  port            = 6379

  subnet_group_name  = aws_elasticache_subnet_group.this.name
  security_group_ids = [var.redis_security_group_id]

  tags = merge(var.tags, { Name = "jbank-${var.environment}-redis" })
}
