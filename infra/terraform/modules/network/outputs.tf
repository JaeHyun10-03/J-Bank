output "vpc_id" {
  value = aws_vpc.this.id
}

output "public_subnet_ids" {
  value = [for s in aws_subnet.public : s.id]
}

output "was_subnet_ids" {
  value = [for s in aws_subnet.was : s.id]
}

output "db_subnet_ids" {
  value = [for s in aws_subnet.db : s.id]
}

output "mgmt_subnet_ids" {
  value = [for s in aws_subnet.mgmt : s.id]
}
