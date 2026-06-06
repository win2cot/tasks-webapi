# ---------------------------------------------------------------------------
# tasks-webapi ECS Task Role (S1Infra-1 / S2Infra-2 整合)
# S2Infra-2(ECS Service)でこの Role ARN を task_role_arn に指定する。
# rds-db:connect は IAM 認証(AWSAuthenticationPlugin)に必要(§3.6)。
# SSM ポリシーは tasks-webapi が起動時に Parameter Store から設定値を読む権限。
# ---------------------------------------------------------------------------

resource "aws_iam_role" "webapi_task" {
  name = "tasks-${var.env}-webapi-task-role"

  assume_role_policy = jsonencode({
    Version   = "2012-10-17"
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
    Version   = "2012-10-17"
    Statement = [
      {
        Sid      = "RdsIamConnect"
        Effect   = "Allow"
        Action   = "rds-db:connect"
        Resource = "arn:aws:rds-db:${var.region}:*:dbuser:${module.rds.db_instance_resource_id}/tasks_webapi"
      }
    ]
  })
}

# SSM read — Parameter Store から設定値を取得する権限
resource "aws_iam_role_policy" "webapi_ssm" {
  name = "tasks-${var.env}-webapi-ssm"
  role = aws_iam_role.webapi_task.id

  policy = jsonencode({
    Version   = "2012-10-17"
    Statement = [
      {
        Sid      = "SsmReadTasksParams"
        Effect   = "Allow"
        Action   = [
          "ssm:GetParameter",
          "ssm:GetParameters",
          "ssm:GetParametersByPath"
        ]
        Resource = [
          "arn:aws:ssm:${var.region}:*:parameter/tasks/${var.env}/app/*",
          "arn:aws:ssm:${var.region}:*:parameter/tasks/${var.env}/keycloak/oauth-client-secret"
        ]
      },
      {
        Sid      = "KmsDecrypt"
        Effect   = "Allow"
        Action   = "kms:Decrypt"
        Resource = "arn:aws:kms:${var.region}:*:key/alias/aws/ssm"
      }
    ]
  })
}
