# ---------------------------------------------------------------------------
# ECS Cluster — Fargate (S1Infra-2 / ADR-0004)
# tasks-webapi + Keycloak Custom Image の 2 Service が乗るクラスタ
# ---------------------------------------------------------------------------

module "ecs_cluster" {
  source = "../../modules/ecs_cluster"

  env = var.env
}
