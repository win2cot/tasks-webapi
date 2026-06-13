# ---------------------------------------------------------------------------
# ECS + RDS Scheduler — EventBridge Scheduler + Lambda で dev 稼働時間制御 (S3Infra-4 / #620)
# 平日 19:00-02:00 / 土日 10:00-02:00 (JST) に ECS desiredCount と RDS start/stop を切替
# 起動順序: RDS start → available 待機 → ECS desiredCount=1
# 停止順序: ECS desiredCount=0 → RDS stop
# ---------------------------------------------------------------------------

data "archive_file" "ecs_scheduler" {
  type        = "zip"
  source_file = "${path.module}/lambda/ecs_scheduler.py"
  output_path = "${path.module}/lambda/ecs_scheduler.zip"
}

# ---------------------------------------------------------------------------
# Lambda 実行ロール — CloudWatch Logs + ECS UpdateService + RDS Start/Stop
# ---------------------------------------------------------------------------

resource "aws_iam_role" "ecs_scheduler_lambda" {
  name = "tasks-${var.env}-ecs-scheduler-lambda-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "lambda.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })

  tags = {
    Name = "tasks-${var.env}-ecs-scheduler-lambda-role"
  }
}

resource "aws_iam_role_policy" "ecs_scheduler_lambda" {
  name = "tasks-${var.env}-ecs-scheduler-lambda-policy"
  role = aws_iam_role.ecs_scheduler_lambda.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid      = "CloudWatchLogs"
        Effect   = "Allow"
        Action   = ["logs:CreateLogStream", "logs:PutLogEvents"]
        Resource = "arn:aws:logs:${var.region}:${data.aws_caller_identity.current.account_id}:log-group:/aws/lambda/tasks-${var.env}-ecs-scheduler:*"
      },
      {
        Sid    = "EcsUpdateService"
        Effect = "Allow"
        Action = ["ecs:UpdateService"]
        Resource = [
          "arn:aws:ecs:${var.region}:${data.aws_caller_identity.current.account_id}:service/tasks-${var.env}-cluster/tasks-${var.env}-webapi",
          "arn:aws:ecs:${var.region}:${data.aws_caller_identity.current.account_id}:service/platform-${var.env}-cluster/platform-${var.env}-keycloak",
        ]
      },
      {
        Sid      = "RdsStartStop"
        Effect   = "Allow"
        Action   = ["rds:StartDBInstance", "rds:StopDBInstance"]
        Resource = "arn:aws:rds:${var.region}:${data.aws_caller_identity.current.account_id}:db:${module.rds.db_instance_identifier}"
      },
      {
        # rds:DescribeDBInstances は resource-level permission 非対応のため Resource:"*"
        # Describe* prefix は規約 R1 の読取専用 wildcard 許容対象
        Sid      = "RdsDescribe"
        Effect   = "Allow"
        Action   = ["rds:DescribeDBInstances"]
        Resource = "*"
      },
    ]
  })
}

# ---------------------------------------------------------------------------
# Lambda ロググループ — 事前作成して retention を管理
# ---------------------------------------------------------------------------

resource "aws_cloudwatch_log_group" "ecs_scheduler_lambda" {
  name              = "/aws/lambda/tasks-${var.env}-ecs-scheduler"
  retention_in_days = 7

  tags = {
    Name = "tasks-${var.env}-ecs-scheduler-logs"
  }
}

# ---------------------------------------------------------------------------
# Lambda 関数 — ECS desiredCount 切替 + RDS start/stop
# ECS_TARGETS: "cluster1/service1,cluster2/service2" 形式
# timeout 300s: RDS available 待機(最大 240s)+ 処理マージン
# ---------------------------------------------------------------------------

resource "aws_lambda_function" "ecs_scheduler" {
  function_name    = "tasks-${var.env}-ecs-scheduler"
  role             = aws_iam_role.ecs_scheduler_lambda.arn
  handler          = "ecs_scheduler.handler"
  runtime          = "python3.12"
  filename         = data.archive_file.ecs_scheduler.output_path
  source_code_hash = data.archive_file.ecs_scheduler.output_base64sha256
  timeout          = 300

  environment {
    variables = {
      ECS_TARGETS = join(",", [
        "tasks-${var.env}-cluster/tasks-${var.env}-webapi",
        "platform-${var.env}-cluster/platform-${var.env}-keycloak",
      ])
      RDS_INSTANCE_ID = module.rds.db_instance_identifier
    }
  }

  depends_on = [aws_cloudwatch_log_group.ecs_scheduler_lambda]

  tags = {
    Name = "tasks-${var.env}-ecs-scheduler"
  }
}

# ---------------------------------------------------------------------------
# EventBridge Scheduler 実行ロール — Lambda 呼び出し権限
# ---------------------------------------------------------------------------

resource "aws_iam_role" "ecs_scheduler_eb" {
  name = "tasks-${var.env}-ecs-scheduler-eb-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "scheduler.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })

  tags = {
    Name = "tasks-${var.env}-ecs-scheduler-eb-role"
  }
}

resource "aws_iam_role_policy" "ecs_scheduler_eb" {
  name = "tasks-${var.env}-ecs-scheduler-eb-policy"
  role = aws_iam_role.ecs_scheduler_eb.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Sid      = "InvokeLambda"
      Effect   = "Allow"
      Action   = ["lambda:InvokeFunction"]
      Resource = aws_lambda_function.ecs_scheduler.arn
    }]
  })
}

# ---------------------------------------------------------------------------
# EventBridge Scheduler — 3 cron スケジュール (timezone = Asia/Tokyo, JST 全層統一方針)
# schedule_expression は EventBridge Scheduler cron 形式:
#   cron(minute hour day-of-month month day-of-week year)
# ---------------------------------------------------------------------------

# 平日 19:00 JST 起動 — RDS start → ECS desiredCount=1
resource "aws_scheduler_schedule" "ecs_start_weekday" {
  name                         = "tasks-${var.env}-ecs-start-weekday"
  schedule_expression          = "cron(0 19 ? * MON-FRI *)"
  schedule_expression_timezone = "Asia/Tokyo"

  flexible_time_window {
    mode = "OFF"
  }

  target {
    arn      = aws_lambda_function.ecs_scheduler.arn
    role_arn = aws_iam_role.ecs_scheduler_eb.arn
    input    = jsonencode({ action = "start" })
  }
}

# 土日 10:00 JST 起動 — RDS start → ECS desiredCount=1
resource "aws_scheduler_schedule" "ecs_start_weekend" {
  name                         = "tasks-${var.env}-ecs-start-weekend"
  schedule_expression          = "cron(0 10 ? * SAT-SUN *)"
  schedule_expression_timezone = "Asia/Tokyo"

  flexible_time_window {
    mode = "OFF"
  }

  target {
    arn      = aws_lambda_function.ecs_scheduler.arn
    role_arn = aws_iam_role.ecs_scheduler_eb.arn
    input    = jsonencode({ action = "start" })
  }
}

# 毎日 02:00 JST 停止 — ECS desiredCount=0 → RDS stop
resource "aws_scheduler_schedule" "ecs_stop_daily" {
  name                         = "tasks-${var.env}-ecs-stop-daily"
  schedule_expression          = "cron(0 2 * * ? *)"
  schedule_expression_timezone = "Asia/Tokyo"

  flexible_time_window {
    mode = "OFF"
  }

  target {
    arn      = aws_lambda_function.ecs_scheduler.arn
    role_arn = aws_iam_role.ecs_scheduler_eb.arn
    input    = jsonencode({ action = "stop" })
  }
}
