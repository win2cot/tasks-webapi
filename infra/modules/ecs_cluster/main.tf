# ---------------------------------------------------------------------------
# ECS Cluster — tasks stack 専用 Fargate クラスタ (ADR-0004)
# ECS Cluster オブジェクト自体は VPC 非依存(Service が private subnet を参照)
# ---------------------------------------------------------------------------

resource "aws_ecs_cluster" "main" {
  name = "tasks-${var.env}-cluster"

  setting {
    name  = "containerInsights"
    value = "enabled"
  }

  tags = {
    Name = "tasks-${var.env}-cluster"
  }
}

# ---------------------------------------------------------------------------
# Capacity Providers — FARGATE + FARGATE_SPOT
# EC2 は採用しない(ADR-0004 §1 仕分け「ECS は専用 / Fargate 採用」)
# ---------------------------------------------------------------------------

resource "aws_ecs_cluster_capacity_providers" "main" {
  cluster_name = aws_ecs_cluster.main.name

  capacity_providers = ["FARGATE", "FARGATE_SPOT"]

  default_capacity_provider_strategy {
    capacity_provider = "FARGATE"
    weight            = 1
    base              = 1
  }
}

# ---------------------------------------------------------------------------
# CloudWatch Log Groups — 雛形(本格運用設定は S3Infra-3)
# tasks-webapi / keycloak 各 Service が参照するロググループを事前作成
# ---------------------------------------------------------------------------

resource "aws_cloudwatch_log_group" "webapi" {
  name              = "/ecs/tasks-${var.env}/webapi"
  retention_in_days = 7

  tags = {
    Name = "/ecs/tasks-${var.env}/webapi"
  }
}

resource "aws_cloudwatch_log_group" "keycloak" {
  name              = "/ecs/tasks-${var.env}/keycloak"
  retention_in_days = 7

  tags = {
    Name = "/ecs/tasks-${var.env}/keycloak"
  }
}
