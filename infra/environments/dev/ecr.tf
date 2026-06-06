# ---------------------------------------------------------------------------
# ECR repositories — tasks-webapi / keycloak-custom (S1Infra-7)
# Owned by the tasks stack. IAM push/pull policies will be attached in
# Sprint 2 when the CI OIDC role and ECS Task Execution Role are created.
# ---------------------------------------------------------------------------

module "ecr" {
  source = "../../modules/ecr"

  env = var.env
}
