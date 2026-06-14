output "db_endpoint" {
  description = "RDS endpoint address (without port)"
  value       = aws_db_instance.keycloak.address
}

output "db_port" {
  description = "RDS port"
  value       = aws_db_instance.keycloak.port
}

output "db_name" {
  description = "Database name"
  value       = aws_db_instance.keycloak.db_name
}

output "db_username" {
  description = "Master username"
  value       = aws_db_instance.keycloak.username
}

output "sg_id" {
  description = "Security Group ID for Keycloak DB (SG-KeycloakDB)"
  value       = aws_security_group.keycloak_db.id
}

output "password_ssm_name" {
  description = "SSM parameter name for Keycloak DB password"
  value       = aws_ssm_parameter.keycloak_db_password.name
}

output "db_instance_identifier" {
  description = "RDS instance identifier"
  value       = aws_db_instance.keycloak.identifier
}
