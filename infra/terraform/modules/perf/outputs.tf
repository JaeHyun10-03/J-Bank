output "target_instance_id" {
  value = aws_instance.target.id
}

output "target_private_ip" {
  value = aws_instance.target.private_ip
}

output "loadgen_instance_id" {
  value = aws_instance.loadgen.id
}

output "availability_zone" {
  value = data.aws_subnet.selected.availability_zone
}
