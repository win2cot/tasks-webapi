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
# ADR-0028 巻き戻し防止 (i) — 現行稼働イメージ読取
#
# 初回 apply では Task Definition family が未存在のため data source read が失敗する。
# ブートストラップ手順 (一度だけ):
#   aws ecs register-task-definition \
#     --family tasks-${env}-webapi \
#     --requires-compatibilities FARGATE \
#     --network-mode awsvpc \
#     --cpu 512 --memory 1024 \
#     --container-definitions '[{"name":"webapi","image":"<var.bootstrap_image>","essential":true}]'
# 登録後に terraform apply を実行すると以降は CI デプロイ済みイメージを自動注入する。
# ---------------------------------------------------------------------------

data "aws_ecs_task_definition" "webapi_current" {
  task_definition = "tasks-${var.env}-webapi"
}

data "aws_ecs_container_definition" "webapi_current" {
  task_definition = data.aws_ecs_task_definition.webapi_current.id
  container_name  = "webapi"
}

locals {
  # ADR-0028 (i): inject currently running image to prevent rollback when Terraform applies env-var changes.
  webapi_image = try(data.aws_ecs_container_definition.webapi_current.image, var.bootstrap_image)
}

# ---------------------------------------------------------------------------
# ECS Task Definition — tasks-webapi (ADR-0028 / S2Infra-2)
# CPU 512 / Memory 1024; Spring Boot 4 + GraalVM Native Image (ADR-0021)
# Spring プロファイル不使用確定前提 — 環境差は env vars で注入(infrastructure-plan §1 確定前提 5)
# ---------------------------------------------------------------------------

resource "aws_ecs_task_definition" "webapi" {
  family                   = "tasks-${var.env}-webapi"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = "512"
  memory                   = "1024"
  execution_role_arn       = aws_iam_role.webapi_exec.arn
  task_role_arn            = aws_iam_role.webapi_task.arn

  container_definitions = jsonencode([{
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
      { name = "SPRING_DATASOURCE_URL", value = "jdbc:mysql://db.tasks.internal:3306/tasks" },
      { name = "SPRING_DATASOURCE_USERNAME", value = "tasks_webapi" },
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
  }])

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
