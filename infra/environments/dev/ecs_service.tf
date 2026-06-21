# ---------------------------------------------------------------------------
# ECS Task Execution Role — ECR pull + CloudWatch Logs + SSM secrets injection
# Separate from Task Role (webapi_task in iam.tf) which is the app's runtime identity.
# ---------------------------------------------------------------------------

resource "aws_iam_role" "webapi_exec" {
  name = "tasks-${var.env}-webapi-exec-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "ecs-tasks.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })

  tags = {
    Name = "tasks-${var.env}-webapi-exec-role"
  }
}

# AWS managed policy: ECR pull + CloudWatch Logs agent delivery
resource "aws_iam_role_policy_attachment" "webapi_exec_managed" {
  role       = aws_iam_role.webapi_exec.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

# Inline policy: SSM read for secrets injected via container `secrets` block
resource "aws_iam_role_policy" "webapi_exec_ssm" {
  name = "tasks-${var.env}-webapi-exec-ssm"
  role = aws_iam_role.webapi_exec.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "SsmSecrets"
        Effect = "Allow"
        Action = ["ssm:GetParameter", "ssm:GetParameters"]
        Resource = [
          "arn:aws:ssm:${var.region}:${data.aws_caller_identity.current.account_id}:parameter/tasks/${var.env}/app/jwt-issuer",
          "arn:aws:ssm:${var.region}:${data.aws_caller_identity.current.account_id}:parameter/tasks/${var.env}/app/tenant-default-id",
          "arn:aws:ssm:${var.region}:${data.aws_caller_identity.current.account_id}:parameter/tasks/${var.env}/keycloak/oauth-client-secret",
        ]
      },
    ]
  })
}

# ---------------------------------------------------------------------------
# ADOT Collector 設定 (ADR-0007 §6)
# receivers: OTLP grpc/http(localhost)
# exporters: X-Ray OTLP(sigv4auth) + awsemf(CloudWatch Logs)
# ---------------------------------------------------------------------------

locals {
  adot_config = <<-YAML
    extensions:
      sigv4auth:
        region: ${var.region}
        service: xray
    receivers:
      otlp:
        protocols:
          grpc:
            endpoint: "0.0.0.0:4317"
          http:
            endpoint: "0.0.0.0:4318"
    exporters:
      otlphttp/xray:
        endpoint: https://xray.${var.region}.amazonaws.com
        auth:
          authenticator: sigv4auth
      awsemf:
        region: ${var.region}
        namespace: tasks-${var.env}-webapi
        log_group_name: /ecs/tasks-${var.env}/webapi-metrics
        log_stream_name: metrics
    service:
      extensions: [sigv4auth]
      pipelines:
        traces:
          receivers: [otlp]
          exporters: [otlphttp/xray]
        metrics:
          receivers: [otlp]
          exporters: [awsemf]
  YAML
}

# ---------------------------------------------------------------------------
# ADR-0028 巻き戻し防止 (i) — 現行稼働イメージ注入
# apply 前の ECS タスク定義から稼働中イメージを読み取り、
# Terraform が busybox で上書きするのを防ぐ。
# 前提: タスク定義ファミリーが存在すること（初回 apply 後は常に存在する）。
# ---------------------------------------------------------------------------

data "aws_ecs_task_definition" "webapi_pre_apply" {
  task_definition = "tasks-${var.env}-webapi"
}

locals {
  _pre_apply_containers = jsondecode(data.aws_ecs_task_definition.webapi_pre_apply.container_definitions)
  _pre_apply_image = try(
    [for c in local._pre_apply_containers : c.image if c.name == "webapi"][0],
    var.bootstrap_image
  )
  # ECR の tasks-webapi リポジトリのイメージが稼働中ならそれを保持する。
  # busybox 等のブートストラップ用プレースホルダーは保持しない。
  webapi_image = can(regex("tasks-webapi:", local._pre_apply_image)) ? local._pre_apply_image : var.bootstrap_image
}

# ---------------------------------------------------------------------------
# ECS Task Definition — tasks-webapi (ADR-0028 / S2Infra-2 / ADR-0007)
# CPU 1024 / Memory 2048; webapi(Spring Boot 4 GraalVM Native) + ADOT Collector サイドカー
# Spring プロファイル不使用確定前提 — 環境差は env vars で注入(infrastructure-plan §1 確定前提 5)
# ---------------------------------------------------------------------------

resource "aws_ecs_task_definition" "webapi" {
  family                   = "tasks-${var.env}-webapi"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = "1024"
  memory                   = "2048"
  execution_role_arn       = aws_iam_role.webapi_exec.arn
  task_role_arn            = aws_iam_role.webapi_task.arn

  container_definitions = jsonencode([
    {
      name      = "webapi"
      image     = local.webapi_image
      essential = true

      portMappings = [{
        containerPort = 8080
        protocol      = "tcp"
      }]

      environment = [
        { name = "SERVER_PORT", value = "8080" },
        { name = "TZ", value = "Asia/Tokyo" },
        { name = "DB_HOST", value = module.rds.db_instance_address },
        { name = "DB_PORT", value = tostring(module.rds.db_instance_port) },
        { name = "SPRING_DATASOURCE_USERNAME", value = "tasks_webapi" },
        { name = "RDS_IAM_AUTH_ENABLED", value = "true" },
        { name = "CORS_ALLOWED_ORIGINS", value = "https://tasks-dev.dgz48.xyz" },
        # OTLP エクスポート先は localhost の ADOT Collector サイドカー(ADR-0007)
        { name = "OTEL_EXPORTER_OTLP_ENDPOINT", value = "http://localhost:4317" },
        { name = "OTEL_EXPORTER_OTLP_PROTOCOL", value = "grpc" },
      ]

      secrets = [
        {
          name      = "SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI"
          valueFrom = "arn:aws:ssm:${var.region}:${data.aws_caller_identity.current.account_id}:parameter/tasks/${var.env}/app/jwt-issuer"
        },
        {
          name      = "APP_TENANT_DEFAULT_ID"
          valueFrom = "arn:aws:ssm:${var.region}:${data.aws_caller_identity.current.account_id}:parameter/tasks/${var.env}/app/tenant-default-id"
        },
        {
          name      = "KEYCLOAK_CLIENT_SECRET"
          valueFrom = "arn:aws:ssm:${var.region}:${data.aws_caller_identity.current.account_id}:parameter/tasks/${var.env}/keycloak/oauth-client-secret"
        },
      ]

      logConfiguration = {
        logDriver = "awslogs"
        options = {
          "awslogs-group"         = module.ecs_cluster.webapi_log_group_name
          "awslogs-region"        = var.region
          "awslogs-stream-prefix" = "webapi"
        }
      }
    },
    {
      # ADOT Collector サイドカー(ADR-0007): SigV4 署名 + X-Ray OTLP + CloudWatch EMF
      # essential=false: ADOT 障害でも webapi コンテナを継続稼働させる
      name      = "adot-collector"
      image     = "public.ecr.aws/aws-observability/aws-otel-collector:latest"
      essential = false

      command = ["--config=env:AOT_CONFIG_CONTENT"]

      environment = [
        { name = "AOT_CONFIG_CONTENT", value = local.adot_config },
      ]

      logConfiguration = {
        logDriver = "awslogs"
        options = {
          "awslogs-group"         = module.ecs_cluster.adot_log_group_name
          "awslogs-region"        = var.region
          "awslogs-stream-prefix" = "adot"
        }
      }
    },
  ])

  tags = {
    Name = "tasks-${var.env}-webapi-task-def"
  }
}

# ---------------------------------------------------------------------------
# ADR-0028 巻き戻し防止 (ii) — max(revision) パターン用 data source
# ECS Service の task_definition 属性は「Terraform が作った revision」と
# 「稼働中最新 revision」のうち大きい方を指す。巻き戻し方向への repoint を構造的に排除。
# ---------------------------------------------------------------------------

data "aws_ecs_task_definition" "webapi" {
  task_definition = aws_ecs_task_definition.webapi.family
  depends_on      = [aws_ecs_task_definition.webapi]
}

# ---------------------------------------------------------------------------
# ECS Service — tasks-webapi Fargate Service (S2Infra-2)
# TG / Listener Rule は module.route53 が所有(priority 100, api-<env>.tasks.dgz48.xyz)
# ---------------------------------------------------------------------------

resource "aws_ecs_service" "webapi" {
  name          = "tasks-${var.env}-webapi"
  cluster       = module.ecs_cluster.cluster_arn
  desired_count = 1
  launch_type   = "FARGATE"

  # ADR-0028 (ii): max(revision) で巻き戻し方向への repoint を構造的に排除
  task_definition = "${aws_ecs_task_definition.webapi.family}:${max(
    aws_ecs_task_definition.webapi.revision,
    data.aws_ecs_task_definition.webapi.revision,
  )}"

  network_configuration {
    subnets          = split(",", data.aws_ssm_parameter.private_subnet_ids.value)
    security_groups  = [module.security_group.ecs_sg_id]
    assign_public_ip = false
  }

  load_balancer {
    target_group_arn = module.route53.webapi_tg_arn
    container_name   = "webapi"
    container_port   = 8080
  }

  depends_on = [module.route53]

  tags = {
    Name = "tasks-${var.env}-webapi-service"
  }
}
