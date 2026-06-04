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
