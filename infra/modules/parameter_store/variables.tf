variable "env" {
  type        = string
  description = "Environment name (dev / stg / prd)"
}

variable "db_password" {
  type        = string
  sensitive   = true
  description = "RDS master password (DBA use only; tasks-webapi uses IAM auth)"
}

variable "keycloak_spi_read_password" {
  type        = string
  sensitive   = true
  description = "Password for the Keycloak SPI federation read-only DB user"
}

variable "keycloak_spi_read_username" {
  type        = string
  description = "Username for the Keycloak SPI federation read-only DB user; published to /tasks/<env>/db/keycloak-spi-read-username"
  default     = "keycloak_spi_read"
}

variable "keycloak_admin_password" {
  type        = string
  sensitive   = true
  description = "Keycloak admin console password"
}

variable "keycloak_oauth_client_secret" {
  type        = string
  sensitive   = true
  description = "OAuth2 client secret for tasks-webapi realm"
}

variable "keycloak_smtp_password" {
  type        = string
  sensitive   = true
  description = "SES SMTP interface password for Keycloak email sending (generated from IAM access key)"
}

variable "jwt_issuer" {
  type        = string
  description = "OAuth2 JWT issuer URI (e.g. https://auth-dev.dgz48.xyz/realms/tasks)"
}

variable "tenant_default_id" {
  type        = string
  description = "Default tenant ID"
}
