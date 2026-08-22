output "rds_endpoint" {
  value = aws_db_instance.this.endpoint
}

output "rds_address" {
  value = aws_db_instance.this.address
}

output "redis_endpoint" {
  value = aws_elasticache_cluster.this.cache_nodes[0].address
}
