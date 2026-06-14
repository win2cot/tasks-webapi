# ---------------------------------------------------------------------------
# Scheduler module — Node.js Lambda + EventBridge Scheduler (fire-and-forget)
# Shared source code; each stack deploys independent Lambda + IAM + schedules.
# Event payload: { action: "start"|"stop", rds: [...ids], ecs: [[cluster, svc], ...] }
# ---------------------------------------------------------------------------

data "archive_file" "scheduler" {
  type        = "zip"
  source_file = "${path.module}/lambda/handler.js"
  output_path = "${path.module}/lambda/handler.zip"
}

# ---------------------------------------------------------------------------
# Lambda 実行ロール — CloudWatch Logs + ECS UpdateService + RDS Start/Stop
# ---------------------------------------------------------------------------

resource "aws_iam_role" "scheduler_lambda" {
  name = "${var.stack}-${var.env}-scheduler-lambda-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "lambda.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })

  tags = {
    Name = "${var.stack}-${var.env}-scheduler-lambda-role"
  }
}

resource "aws_iam_role_policy" "scheduler_lambda" {
  name = "${var.stack}-${var.env}-scheduler-lambda-policy"
  role = aws_iam_role.scheduler_lambda.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = concat(
      [
        {
          Sid      = "CloudWatchLogs"
          Effect   = "Allow"
          Action   = ["logs:CreateLogStream", "logs:PutLogEvents"]
          Resource = "arn:aws:logs:${var.region}:${var.account_id}:log-group:/aws/lambda/${var.stack}-${var.env}-scheduler:*"
        },
      ],
      length(var.ecs_service_arns) > 0 ? [
        {
          Sid      = "EcsUpdateService"
          Effect   = "Allow"
          Action   = ["ecs:UpdateService"]
          Resource = var.ecs_service_arns
        },
      ] : [],
      length(var.rds_instance_arns) > 0 ? [
        {
          Sid      = "RdsStartStop"
          Effect   = "Allow"
          Action   = ["rds:StartDBInstance", "rds:StopDBInstance"]
          Resource = var.rds_instance_arns
        },
      ] : [],
    )
  })
}

# ---------------------------------------------------------------------------
# Lambda ロググループ — 事前作成して retention を管理
# ---------------------------------------------------------------------------

resource "aws_cloudwatch_log_group" "scheduler_lambda" {
  name              = "/aws/lambda/${var.stack}-${var.env}-scheduler"
  retention_in_days = 7

  tags = {
    Name = "${var.stack}-${var.env}-scheduler-logs"
  }
}

# ---------------------------------------------------------------------------
# Lambda 関数 — Node.js 24.x / SDK v3 同梱 / タイムアウト 30s (待機なし)
# ---------------------------------------------------------------------------

resource "aws_lambda_function" "scheduler" {
  function_name    = "${var.stack}-${var.env}-scheduler"
  role             = aws_iam_role.scheduler_lambda.arn
  handler          = "handler.handler"
  runtime          = "nodejs22.x"
  filename         = data.archive_file.scheduler.output_path
  source_code_hash = data.archive_file.scheduler.output_base64sha256
  timeout          = 30

  depends_on = [aws_cloudwatch_log_group.scheduler_lambda]

  tags = {
    Name = "${var.stack}-${var.env}-scheduler"
  }
}

# ---------------------------------------------------------------------------
# EventBridge Scheduler 実行ロール — Lambda 呼び出し権限
# ---------------------------------------------------------------------------

resource "aws_iam_role" "scheduler_eb" {
  name = "${var.stack}-${var.env}-scheduler-eb-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "scheduler.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })

  tags = {
    Name = "${var.stack}-${var.env}-scheduler-eb-role"
  }
}

resource "aws_iam_role_policy" "scheduler_eb" {
  name = "${var.stack}-${var.env}-scheduler-eb-policy"
  role = aws_iam_role.scheduler_eb.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Sid      = "InvokeLambda"
      Effect   = "Allow"
      Action   = ["lambda:InvokeFunction"]
      Resource = aws_lambda_function.scheduler.arn
    }]
  })
}

# ---------------------------------------------------------------------------
# EventBridge Scheduler — var.schedules をそのままリソースに展開
# schedule_expression は呼び出し側で cron(…) 形式で渡す
# timezone は Asia/Tokyo 固定 (JST 全層統一方針)
# ---------------------------------------------------------------------------

resource "aws_scheduler_schedule" "schedules" {
  for_each = { for s in var.schedules : s.name => s }

  name                         = "${var.stack}-${var.env}-${each.key}"
  schedule_expression          = each.value.schedule_expression
  schedule_expression_timezone = "Asia/Tokyo"

  flexible_time_window {
    mode = "OFF"
  }

  target {
    arn      = aws_lambda_function.scheduler.arn
    role_arn = aws_iam_role.scheduler_eb.arn
    input    = each.value.input
  }
}
