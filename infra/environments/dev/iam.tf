# ---------------------------------------------------------------------------
# tasks-webapi ECS Task Role (S1Infra-1 / S2Infra-2 整合)
# S2Infra-2(ECS Service)でこの Role ARN を task_role_arn に指定する。
# rds-db:connect は IAM 認証(AWSAuthenticationPlugin)に必要(§3.6)。
# SSM ポリシーは tasks-webapi が起動時に Parameter Store から設定値を読む権限。
# ---------------------------------------------------------------------------

resource "aws_iam_role" "webapi_task" {
  name = "tasks-${var.env}-webapi-task-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Principal = {
          Service = "ecs-tasks.amazonaws.com"
        }
        Action = "sts:AssumeRole"
      }
    ]
  })

  tags = {
    Name = "tasks-${var.env}-webapi-task-role"
  }
}

# rds-db:connect — IAM 認証で DB user tasks_webapi として接続する権限
resource "aws_iam_role_policy" "webapi_rds_connect" {
  name = "tasks-${var.env}-webapi-rds-connect"
  role = aws_iam_role.webapi_task.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid      = "RdsIamConnect"
        Effect   = "Allow"
        Action   = "rds-db:connect"
        Resource = "arn:aws:rds-db:${var.region}:${data.aws_caller_identity.current.account_id}:dbuser:${module.rds.db_instance_resource_id}/tasks_webapi"
      }
    ]
  })
}

# ADOT Collector サイドカー用権限 (ADR-0007)
# X-Ray OTLP エンドポイント(Application Signals)+ CloudWatch EMF(awsemf exporter)
resource "aws_iam_role_policy" "webapi_adot" {
  name = "tasks-${var.env}-webapi-adot"
  role = aws_iam_role.webapi_task.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        # X-Ray OTLP エンドポイント(POST /v1/traces)への送出
        # otlphttp/xray exporter は X-Ray SDK を使わないため PutTelemetryRecords / GetSampling* 不要
        # xray:PutTraceSegments は resource-level permissions 非対応のため Resource:"*"
        Sid      = "XrayTraces"
        Effect   = "Allow"
        Action   = "xray:PutTraceSegments"
        Resource = "*"
      },
      {
        # awsemf exporter — EMF ログストリームへの書き込み
        Sid    = "EmfLogStreamWrite"
        Effect = "Allow"
        Action = [
          "logs:CreateLogGroup",
          "logs:CreateLogStream",
          "logs:PutLogEvents",
          "logs:DescribeLogStreams",
        ]
        Resource = [
          "arn:aws:logs:${var.region}:${data.aws_caller_identity.current.account_id}:log-group:/ecs/tasks-${var.env}/webapi-metrics",
          "arn:aws:logs:${var.region}:${data.aws_caller_identity.current.account_id}:log-group:/ecs/tasks-${var.env}/webapi-metrics:log-stream:*",
        ]
      },
      {
        # logs:DescribeLogGroups は resource-level permissions 非対応のため Resource:"*"
        Sid      = "EmfDescribeLogGroups"
        Effect   = "Allow"
        Action   = "logs:DescribeLogGroups"
        Resource = "*"
      },
    ]
  })
}

# SSM read — Parameter Store から設定値を取得する権限
resource "aws_iam_role_policy" "webapi_ssm" {
  name = "tasks-${var.env}-webapi-ssm"
  role = aws_iam_role.webapi_task.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "SsmReadTasksParams"
        Effect = "Allow"
        Action = [
          "ssm:GetParameter",
          "ssm:GetParameters",
          "ssm:GetParametersByPath"
        ]
        Resource = [
          "arn:aws:ssm:${var.region}:${data.aws_caller_identity.current.account_id}:parameter/tasks/${var.env}/app/*",
          "arn:aws:ssm:${var.region}:${data.aws_caller_identity.current.account_id}:parameter/tasks/${var.env}/keycloak/oauth-client-secret"
        ]
      },
      {
        Sid      = "KmsDecrypt"
        Effect   = "Allow"
        Action   = "kms:Decrypt"
        Resource = "arn:aws:kms:${var.region}:${data.aws_caller_identity.current.account_id}:key/alias/aws/ssm"
      }
    ]
  })
}

# webapi の SES 実送信(会員登録/招待/通知/リセットのメール、ADR-0040 / ADR-0041)。
# 規約 R1: 書込み action は完全列挙(ses:SendEmail のみ)。
# R2: リソースは必要な SES identity を ARN で列挙(wildcard 不使用)。
#   - 送信元: mail.dgz48.xyz(検証済み送信ドメイン)
#   - 宛先:   e2e.dgz48.xyz(ADR-0041 の E2E 受信ドメイン)
# NOTE: SES サンドボックスでは ses:SendEmail が「送信元」identity だけでなく
#       「宛先」verified identity に対しても認可評価される。送信元単体スコープでは
#       宛先評価で 403 となり会員登録メールが送出できないため、宛先 identity も列挙する。
resource "aws_iam_role_policy" "webapi_ses" {
  name = "tasks-${var.env}-webapi-ses"
  role = aws_iam_role.webapi_task.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "SesSendFromMailIdentity"
        Effect = "Allow"
        Action = "ses:SendEmail"
        Resource = [
          "arn:aws:ses:${var.region}:${data.aws_caller_identity.current.account_id}:identity/mail.dgz48.xyz",
          "arn:aws:ses:${var.region}:${data.aws_caller_identity.current.account_id}:identity/e2e.dgz48.xyz",
        ]
      }
    ]
  })
}
