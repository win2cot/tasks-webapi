output "ecs_sg_id" {
  description = "Security Group ID for ECS tasks (SG-ECS)"
  value       = aws_security_group.ecs.id
}

output "rds_sg_id" {
  description = "Security Group ID for RDS (SG-RDS)"
  value       = aws_security_group.rds.id
}

output "eice_sg_id" {
  description = "Security Group ID for EC2 Instance Connect Endpoint (SG-EICE)"
  value       = aws_security_group.eice.id
}
