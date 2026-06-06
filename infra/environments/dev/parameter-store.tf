module "parameter_store" {
  source = "../../modules/parameter_store"

  env                          = var.env
  db_password                  = var.db_password
  keycloak_spi_read_password   = var.keycloak_spi_read_password
  keycloak_admin_password      = var.keycloak_admin_password
  keycloak_oauth_client_secret = var.keycloak_oauth_client_secret
  keycloak_smtp_password       = var.keycloak_smtp_password
  jwt_issuer                   = var.jwt_issuer
  tenant_default_id            = var.tenant_default_id
}
