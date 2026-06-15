# ---------------------------------------------------------------------------
# SG-Keycloak — inbound from SG-ALB on HTTP:8080 (ALB terminates TLS)
#               egress to Keycloak DB SG :3306 (platform, intra-stack)
#               egress to VPC CIDR :3306 (tasks RDS SPI federation, cross-stack)
#               egress HTTPS :443 (ECR / SSM / CloudWatch via NAT Gateway)
# ---------------------------------------------------------------------------

resource "aws_security_group" "keycloak" {
  name        = "platform-${var.env}-sg-keycloak"
  description = "Keycloak ECS: inbound from ALB on :8080, outbound to DBs and Internet"
  vpc_id      = var.vpc_id

  ingress {
    description     = "HTTP from ALB (ALB terminates TLS)"
    from_port       = 8080
    to_port         = 8080
    protocol        = "tcp"
    security_groups = [var.alb_sg_id]
  }

  egress {
    description     = "MySQL to Keycloak DB (platform, intra-stack)"
    from_port       = 3306
    to_port         = 3306
    protocol        = "tcp"
    security_groups = [var.keycloak_db_sg_id]
  }

  egress {
    # 規約 R2: tasks RDS は cross-stack のため SG 参照不可; VPC CIDR で代替
    description = "MySQL to tasks RDS via VPC CIDR (SPI federation, cross-stack)"
    from_port   = 3306
    to_port     = 3306
    protocol    = "tcp"
    cidr_blocks = [var.vpc_cidr]
  }

  egress {
    description = "HTTPS to Internet (ECR pull / SSM / CloudWatch via NAT)"
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "platform-${var.env}-sg-keycloak"
  }
}

# ---------------------------------------------------------------------------
# ECS Task Execution Role — used by ECS agent to pull image + write logs
# and inject SSM SecureString secrets into the container environment
# ---------------------------------------------------------------------------

resource "aws_iam_role" "exec" {
  name = "platform-${var.env}-keycloak-exec-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "ecs-tasks.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })

  tags = {
    Name = "platform-${var.env}-keycloak-exec-role"
  }
}

# AWS managed policy: ECR pull + CloudWatch Logs (agent-level log delivery)
resource "aws_iam_role_policy_attachment" "exec_managed" {
  role       = aws_iam_role.exec.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

# Inline policy: SSM SecureString read for secrets injected via container `secrets` block
resource "aws_iam_role_policy" "exec_ssm" {
  name = "platform-${var.env}-keycloak-exec-ssm"
  role = aws_iam_role.exec.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "SsmSecrets"
        Effect = "Allow"
        Action = ["ssm:GetParameter", "ssm:GetParameters"]
        Resource = [
          "arn:aws:ssm:${var.region}:${var.account_id}:parameter/platform/${var.env}/keycloak-db-password",
          "arn:aws:ssm:${var.region}:${var.account_id}:parameter/tasks/${var.env}/keycloak/admin-password",
        ]
      }
    ]
  })
}

# ---------------------------------------------------------------------------
# ECS Task Role — runtime IAM identity for the Keycloak container
# No AWS SDK calls needed at MVP; placeholder for future (e.g. STS, custom metrics)
# ---------------------------------------------------------------------------

resource "aws_iam_role" "task" {
  name = "platform-${var.env}-keycloak-task-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "ecs-tasks.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })

  tags = {
    Name = "platform-${var.env}-keycloak-task-role"
  }
}

# ---------------------------------------------------------------------------
# CloudWatch Log Group — /ecs/platform-<env>/keycloak
# ---------------------------------------------------------------------------

resource "aws_cloudwatch_log_group" "keycloak" {
  name              = "/ecs/platform-${var.env}/keycloak"
  retention_in_days = 30

  tags = {
    Name = "platform-${var.env}-keycloak-logs"
  }
}

# ---------------------------------------------------------------------------
# ALB Target Group — HTTP:8080, /realms/master, ip target type (Fargate awsvpc)
# Keycloak 25+ moved /health/ready to the management interface (port 9000).
# /realms/master is stable on port 8080 and returns 200 when Keycloak is ready.
# ---------------------------------------------------------------------------

resource "aws_lb_target_group" "keycloak" {
  name        = "platform-${var.env}-tg-keycloak"
  port        = 8080
  protocol    = "HTTP"
  target_type = "ip"
  vpc_id      = var.vpc_id

  health_check {
    enabled             = true
    path                = "/realms/master"
    protocol            = "HTTP"
    port                = "traffic-port"
    healthy_threshold   = 2
    unhealthy_threshold = 3
    timeout             = 5
    interval            = 30
    matcher             = "200"
  }

  deregistration_delay = 30

  tags = {
    Name = "platform-${var.env}-tg-keycloak"
  }
}

# ---------------------------------------------------------------------------
# ALB Listener Rule — Host: <hostname> → Keycloak TG
# platform priority range: 10-99 (tasks range: 100-199 per ADR-0004)
# ---------------------------------------------------------------------------

resource "aws_lb_listener_rule" "keycloak" {
  listener_arn = var.https_listener_arn
  priority     = var.listener_rule_priority

  condition {
    host_header {
      values = [var.hostname]
    }
  }

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.keycloak.arn
  }

  tags = {
    Name = "platform-${var.env}-rule-keycloak"
  }
}

# ---------------------------------------------------------------------------
# ECS Task Definition — Keycloak Custom Image (#321), Fargate, awsvpc
#
# KC_HTTP_ENABLED=true: ALB terminates TLS; Keycloak runs plain HTTP internally
# KC_HOSTNAME_STRICT=false: required when ALB health check uses container IP
# KEYCLOAK_ADMIN / KEYCLOAK_ADMIN_PASSWORD: first-boot bootstrap only
# ---------------------------------------------------------------------------

resource "aws_ecs_task_definition" "keycloak" {
  family                   = "platform-${var.env}-keycloak"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = "512"
  memory                   = "1024"
  execution_role_arn       = aws_iam_role.exec.arn
  task_role_arn            = aws_iam_role.task.arn

  container_definitions = jsonencode([{
    name      = "keycloak"
    image     = var.image_uri
    essential = true

    # Explicit command overrides Dockerfile CMD; keeps Terraform plan readable.
    # --import-realm: idempotent realm import from /opt/keycloak/data/import/ (IGNORE_EXISTING by default).
    # See docs/dev/keycloak-realm-import.md for re-import procedure.
    command = ["start", "--optimized", "--import-realm"]

    portMappings = [{
      containerPort = 8080
      protocol      = "tcp"
    }]

    environment = [
      { name = "KC_DB", value = "mysql" },
      { name = "KC_DB_URL", value = "jdbc:mysql://${var.keycloak_db_endpoint}:3306/keycloak" },
      { name = "KC_DB_USERNAME", value = "keycloak_admin" },
      { name = "KC_HOSTNAME", value = var.hostname },
      { name = "KC_HOSTNAME_STRICT", value = "false" },
      { name = "KC_HTTP_ENABLED", value = "true" },
      { name = "KC_HEALTH_ENABLED", value = "true" },
      { name = "KC_METRICS_ENABLED", value = "true" },
      { name = "KEYCLOAK_ADMIN", value = "admin" },
      { name = "KC_PROXY_HEADERS", value = "xforwarded" },
    ]

    secrets = [
      {
        name      = "KC_DB_PASSWORD"
        valueFrom = "arn:aws:ssm:${var.region}:${var.account_id}:parameter/platform/${var.env}/keycloak-db-password"
      },
      {
        name      = "KEYCLOAK_ADMIN_PASSWORD"
        valueFrom = "arn:aws:ssm:${var.region}:${var.account_id}:parameter/tasks/${var.env}/keycloak/admin-password"
      },
    ]

    logConfiguration = {
      logDriver = "awslogs"
      options = {
        "awslogs-group"         = aws_cloudwatch_log_group.keycloak.name
        "awslogs-region"        = var.region
        "awslogs-stream-prefix" = "keycloak"
      }
    }
  }])

  tags = {
    Name = "platform-${var.env}-keycloak-task-def"
  }
}

# ---------------------------------------------------------------------------
# ECS Service — Fargate, private subnets, SG-Keycloak
# ignore_changes on task_definition to allow CD pipeline to update image tag
# ---------------------------------------------------------------------------

resource "aws_ecs_service" "keycloak" {
  name            = "platform-${var.env}-keycloak"
  cluster         = var.ecs_cluster_arn
  task_definition = aws_ecs_task_definition.keycloak.arn
  desired_count   = var.desired_count
  launch_type     = "FARGATE"

  # Keycloak 26 takes ~50s to boot (Quarkus + realm import). Without this grace
  # period, ALB reports 3 consecutive 503s and ECS stops the task before it is ready.
  health_check_grace_period_seconds = 120

  network_configuration {
    subnets          = var.private_subnet_ids
    security_groups  = [aws_security_group.keycloak.id]
    assign_public_ip = false
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.keycloak.arn
    container_name   = "keycloak"
    container_port   = 8080
  }

  lifecycle {
    ignore_changes = [task_definition]
  }

  depends_on = [aws_lb_listener_rule.keycloak]

  tags = {
    Name = "platform-${var.env}-keycloak-service"
  }
}
