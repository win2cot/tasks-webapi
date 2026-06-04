# tasks-dev — Terraform variable values (ADR-0002 §E: committed intentionally)
#
# SecureString parameters: CHANGE_ME values are placeholders for initial apply.
# After `terraform apply` creates the parameters, set actual secrets via AWS SSM
# console. The lifecycle.ignore_changes = [value] in the module prevents Terraform
# from reverting externally-updated values on subsequent plans.

env    = "dev"
region = "ap-northeast-1"

# SecureString placeholders — replace in SSM console after initial apply
db_password                  = "CHANGE_ME"
keycloak_admin_password      = "CHANGE_ME"
keycloak_oauth_client_secret = "CHANGE_ME"
keycloak_smtp_password       = "CHANGE_ME"

# String config values
jwt_issuer        = "https://auth-dev.dgz48.xyz/realms/tasks"
tenant_default_id = "1"
