output "db_password_arn" {
  description = "ARN of /tasks/<env>/db/password"
  value       = aws_ssm_parameter.db_password.arn
  sensitive   = true
}

output "db_keycloak_spi_read_password_arn" {
  description = "ARN of /tasks/<env>/db/keycloak-spi-read-password"
  value       = aws_ssm_parameter.db_keycloak_spi_read_password.arn
  sensitive   = true
}

output "keycloak_admin_password_arn" {
  description = "ARN of /tasks/<env>/keycloak/admin-password"
  value       = aws_ssm_parameter.keycloak_admin_password.arn
  sensitive   = true
}

output "keycloak_oauth_client_secret_arn" {
  description = "ARN of /tasks/<env>/keycloak/oauth-client-secret"
  value       = aws_ssm_parameter.keycloak_oauth_client_secret.arn
  sensitive   = true
}

output "keycloak_smtp_password_arn" {
  description = "ARN of /tasks/<env>/keycloak/smtp-password"
  value       = aws_ssm_parameter.keycloak_smtp_password.arn
  sensitive   = true
}

output "jwt_issuer_name" {
  description = "SSM parameter name (path) of /tasks/<env>/app/jwt-issuer"
  value       = aws_ssm_parameter.jwt_issuer.name
}

output "tenant_default_id_name" {
  description = "SSM parameter name (path) of /tasks/<env>/app/tenant-default-id"
  value       = aws_ssm_parameter.tenant_default_id.name
}
