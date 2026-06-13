variable "env" {
  type        = string
  default     = "dev"
  description = "Environment name (dev / stg / prd)"
}

variable "region" {
  type        = string
  default     = "ap-northeast-1"
  description = "AWS region"
}

# ---------------------------------------------------------------------------
# Parameter Store — SecureString placeholders
# Set actual values in AWS SSM console after initial `terraform apply`.
# These variables are declared with placeholder defaults so CI plan succeeds
# without secret injection; lifecycle.ignore_changes prevents drift.
# ---------------------------------------------------------------------------

variable "db_password" {
  type        = string
  sensitive   = true
  default     = "CHANGE_ME"
  description = "RDS master password placeholder (DBA use only; app uses IAM auth)"
}

variable "keycloak_spi_read_password" {
  type        = string
  sensitive   = true
  default     = "CHANGE_ME"
  description = "Keycloak SPI federation read-only DB user password placeholder"
}

variable "keycloak_spi_read_username" {
  type        = string
  default     = "keycloak_spi_read"
  description = "Keycloak SPI federation read-only DB username published to /tasks/<env>/db/keycloak-spi-read-username"
}

variable "keycloak_admin_password" {
  type        = string
  sensitive   = true
  default     = "CHANGE_ME"
  description = "Keycloak admin console password placeholder"
}

variable "keycloak_oauth_client_secret" {
  type        = string
  sensitive   = true
  default     = "CHANGE_ME"
  description = "OAuth2 client secret placeholder for tasks-webapi realm"
}

variable "keycloak_smtp_password" {
  type        = string
  sensitive   = true
  default     = "CHANGE_ME"
  description = "SES SMTP password placeholder for Keycloak email sending"
}

# ---------------------------------------------------------------------------
# Parameter Store — String values
# ---------------------------------------------------------------------------

variable "jwt_issuer" {
  type        = string
  default     = "https://auth-dev.dgz48.xyz/realms/tasks"
  description = "OAuth2 JWT issuer URI for dev environment"
}

variable "tenant_default_id" {
  type        = string
  default     = "1"
  description = "Default tenant ID"
}

# ---------------------------------------------------------------------------
# ECS Task Definition — bootstrap image (ADR-0028)
# ---------------------------------------------------------------------------

variable "bootstrap_image" {
  type        = string
  default     = "public.ecr.aws/docker/library/busybox:latest"
  description = "Placeholder image for initial Task Definition bootstrap. Used only on first apply before CI pushes the real ECR image."
}
