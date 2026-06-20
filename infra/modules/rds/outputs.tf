output "db_instance_address" {
  description = "RDS endpoint hostname (no port) — used as CNAME target in Route53 PHZ"
  value       = aws_db_instance.main.address
}

output "db_instance_port" {
  description = "RDS instance port"
  value       = aws_db_instance.main.port
}

output "db_instance_identifier" {
  description = "RDS instance identifier"
  value       = aws_db_instance.main.identifier
}

output "db_instance_resource_id" {
  description = "RDS resource ID (dbi-resource-id) — required for rds-db:connect IAM policy Resource ARN"
  value       = aws_db_instance.main.resource_id
}
