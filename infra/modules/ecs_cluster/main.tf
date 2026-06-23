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
# CloudWatch Log Groups — ADOT / APM 用(ADR-0007)
#
# アプリ(webapi)/ 監査ログは S3Infra-3 でログ基盤(logging モジュール,
# `/tasks/<env>/*`, ADR-0005)へ移管した。Keycloak ログは共有 platform 所有
# (`/ecs/platform-<env>/keycloak`)。本モジュールには ECS task 定義と密結合する
# ADOT Collector / EMF メトリクス用ロググループのみ残す。
# ---------------------------------------------------------------------------

resource "aws_cloudwatch_log_group" "adot" {
  name              = "/ecs/tasks-${var.env}/adot"
  retention_in_days = 7

  tags = {
    Name = "/ecs/tasks-${var.env}/adot"
  }
}

# EMF ログから CloudWatch メトリクスが自動抽出されるロググループ(ADOT awsemf exporter)
resource "aws_cloudwatch_log_group" "webapi_metrics" {
  name              = "/ecs/tasks-${var.env}/webapi-metrics"
  retention_in_days = 30

  tags = {
    Name = "/ecs/tasks-${var.env}/webapi-metrics"
  }
}
