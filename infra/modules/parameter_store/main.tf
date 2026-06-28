# ---------------------------------------------------------------------------
# SecureString parameters — secrets bootstrapped with placeholder value.
# After initial `terraform apply`, set actual secrets via AWS SSM console.
# lifecycle.ignore_changes = [value] prevents Terraform from reverting
# externally-updated secret values on subsequent plans/applies.
# ---------------------------------------------------------------------------

resource "aws_ssm_parameter" "db_password" {
  name  = "/tasks/${var.env}/db/password"
  type  = "SecureString"
  value = var.db_password

  lifecycle {
    ignore_changes = [value]
  }

  tags = {
    Name = "tasks-${var.env}-db-password"
  }
}

resource "aws_ssm_parameter" "db_keycloak_spi_read_password" {
  name  = "/tasks/${var.env}/db/keycloak-spi-read-password"
  type  = "SecureString"
  value = var.keycloak_spi_read_password

  lifecycle {
    ignore_changes = [value]
  }

  tags = {
    Name = "tasks-${var.env}-db-keycloak-spi-read-password"
  }
}

# SPI federation: read-only MySQL user name published for platform Keycloak (#322)
resource "aws_ssm_parameter" "db_keycloak_spi_read_username" {
  name  = "/tasks/${var.env}/db/keycloak-spi-read-username"
  type  = "String"
  value = var.keycloak_spi_read_username

  tags = {
    Name = "tasks-${var.env}-db-keycloak-spi-read-username"
  }
}

resource "aws_ssm_parameter" "keycloak_admin_password" {
  name  = "/tasks/${var.env}/keycloak/admin-password"
  type  = "SecureString"
  value = var.keycloak_admin_password

  lifecycle {
    ignore_changes = [value]
  }

  tags = {
    Name = "tasks-${var.env}-keycloak-admin-password"
  }
}

resource "aws_ssm_parameter" "keycloak_oauth_client_secret" {
  name  = "/tasks/${var.env}/keycloak/oauth-client-secret"
  type  = "SecureString"
  value = var.keycloak_oauth_client_secret

  lifecycle {
    ignore_changes = [value]
  }

  tags = {
    Name = "tasks-${var.env}-keycloak-oauth-client-secret"
  }
}

resource "aws_ssm_parameter" "keycloak_smtp_password" {
  name  = "/tasks/${var.env}/keycloak/smtp-password"
  type  = "SecureString"
  value = var.keycloak_smtp_password

  lifecycle {
    ignore_changes = [value]
  }

  tags = {
    Name = "tasks-${var.env}-keycloak-smtp-password"
  }
}

# Audit hash-chain HMAC key (ADR-0038). Stored under app/* so the existing webapi
# task-role policy (ssm:GetParameter* on parameter/tasks/<env>/app/* + kms:Decrypt)
# already grants read; no IAM change required.
resource "aws_ssm_parameter" "audit_hmac_key_v1" {
  name  = "/tasks/${var.env}/app/audit-hash-key-v1"
  type  = "SecureString"
  value = var.audit_hmac_key_v1

  lifecycle {
    ignore_changes = [value]
  }

  tags = {
    Name = "tasks-${var.env}-app-audit-hash-key-v1"
  }
}

# Keycloak Admin REST API client secret (ADR-0040). The webapi calls the Keycloak
# Admin API (credential provisioning / reset-password) via a confidential
# service-account client. Stored under app/* so the existing webapi task-role
# policy (ssm:GetParameter* on parameter/tasks/<env>/app/* + kms:Decrypt) already
# grants read; no IAM change required. The consuming Keycloak client + client code
# land in a later PR (ADR-0040 §6 PR2); this provisions the secret slot ahead of it
# (same precedent as audit_hmac_key_v1 / ADR-0038).
resource "aws_ssm_parameter" "keycloak_admin_client_secret" {
  name  = "/tasks/${var.env}/app/keycloak-admin-client-secret"
  type  = "SecureString"
  value = var.keycloak_admin_client_secret

  lifecycle {
    ignore_changes = [value]
  }

  tags = {
    Name = "tasks-${var.env}-app-keycloak-admin-client-secret"
  }
}

# ---------------------------------------------------------------------------
# String parameters — config values fully managed by Terraform
# ---------------------------------------------------------------------------

resource "aws_ssm_parameter" "jwt_issuer" {
  name  = "/tasks/${var.env}/app/jwt-issuer"
  type  = "String"
  value = var.jwt_issuer

  tags = {
    Name = "tasks-${var.env}-app-jwt-issuer"
  }
}

resource "aws_ssm_parameter" "tenant_default_id" {
  name  = "/tasks/${var.env}/app/tenant-default-id"
  type  = "String"
  value = var.tenant_default_id

  tags = {
    Name = "tasks-${var.env}-app-tenant-default-id"
  }
}
