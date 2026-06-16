# ---------------------------------------------------------------------------
# GitHub Actions OIDC provider (one per account)
# ---------------------------------------------------------------------------

resource "aws_iam_openid_connect_provider" "github_actions" {
  url            = "https://token.actions.githubusercontent.com"
  client_id_list = ["sts.amazonaws.com"]
  # AWS manages the thumbprint for GitHub Actions since 2023; value is a
  # required placeholder that will be overwritten automatically.
  thumbprint_list = ["6938fd4d98bab03faadb97b34396831e3780aea1"]
}

locals {
  oidc_arn    = aws_iam_openid_connect_provider.github_actions.arn
  oidc_issuer = "token.actions.githubusercontent.com"
}

# ---------------------------------------------------------------------------
# Helper: trust policy factory
# ---------------------------------------------------------------------------

data "aws_iam_policy_document" "trust" {
  for_each = {
    platform_plan   = "platform-plan"
    platform_apply  = "platform-apply"
    tasks_plan      = "tasks-plan"
    tasks_apply     = "tasks-apply"
    release_build   = "release-build"
    tasks_deploy    = var.env # "dev" → environment:dev OIDC sub (webapi ECS + web S3/CF + artifact verify)
    platform_deploy = var.env # "dev" → environment:dev OIDC sub (keycloak ECS on platform cluster)
  }

  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [local.oidc_arn]
    }

    condition {
      test     = "StringEquals"
      variable = "${local.oidc_issuer}:aud"
      values   = ["sts.amazonaws.com"]
    }

    condition {
      test     = "StringEquals"
      variable = "${local.oidc_issuer}:sub"
      values   = ["repo:${var.repo}:environment:${each.value}"]
    }
  }
}

# ---------------------------------------------------------------------------
# platform-dev-plan  (read-only; PR plan for platform/shared stack)
# ---------------------------------------------------------------------------

resource "aws_iam_role" "platform_plan" {
  name               = "platform-${var.env}-plan"
  assume_role_policy = data.aws_iam_policy_document.trust["platform_plan"].json
}

data "aws_iam_policy_document" "platform_plan" {
  # State read (platform prefix only)
  statement {
    sid       = "StateRead"
    actions   = ["s3:GetObject"]
    resources = ["arn:aws:s3:::${var.state_bucket}/platform/*"]
  }
  statement {
    sid       = "StateBucketList"
    actions   = ["s3:ListBucket"]
    resources = ["arn:aws:s3:::${var.state_bucket}"]
    condition {
      test     = "StringLike"
      variable = "s3:prefix"
      values   = ["platform/*"]
    }
  }

  # IAM read (inspect OIDC provider + roles during plan)
  statement {
    sid       = "IamRead"
    actions   = ["iam:Get*", "iam:List*"]
    resources = ["*"]
  }

  # EC2 read (network: VPC / Subnet / IGW / RT / NAT / EIP / S3 GW EP + alb / keycloak_db SG)
  statement {
    sid       = "Ec2Read"
    actions   = ["ec2:Describe*"]
    resources = ["*"]
  }

  # ELBv2 read (alb: ALB / Listener)
  statement {
    sid       = "ElbRead"
    actions   = ["elasticloadbalancing:Describe*"]
    resources = ["*"]
  }

  # ACM read (alb: base wildcard cert)
  statement {
    sid       = "AcmRead"
    actions   = ["acm:Describe*", "acm:List*", "acm:GetCertificate"]
    resources = ["*"]
  }

  # SES read (ses: domain identity / DKIM / Config Set)
  statement {
    sid       = "SesRead"
    actions   = ["ses:Get*", "ses:List*", "ses:Describe*"]
    resources = ["*"]
  }

  # Route53 read (shared public zone records / cert validation)
  statement {
    sid       = "Route53Read"
    actions   = ["route53:Get*", "route53:List*"]
    resources = ["*"]
  }

  # RDS read (Keycloak DB subnet group / instance inspection during plan)
  statement {
    sid       = "RdsRead"
    actions   = ["rds:Describe*", "rds:List*"]
    resources = ["*"]
  }

  # ECS read (platform cluster / keycloak task definition / service inspection during plan)
  statement {
    sid       = "EcsRead"
    actions   = ["ecs:Describe*", "ecs:List*"]
    resources = ["*"]
  }

  # ECR read (keycloak-custom image metadata inspection during plan)
  statement {
    sid       = "EcrRead"
    actions   = ["ecr:Describe*", "ecr:List*", "ecr:Get*", "ecr:BatchGetImage"]
    resources = ["*"]
  }

  # CloudWatch Logs read (keycloak log group inspection during plan)
  statement {
    sid       = "LogsRead"
    actions   = ["logs:DescribeLogGroups", "logs:ListTagsLogGroup", "logs:ListTagsForResource"]
    resources = ["*"]
  }

  # Lambda read (platform scheduler function inspection during plan)
  statement {
    sid       = "LambdaRead"
    actions   = ["lambda:Get*", "lambda:List*"]
    resources = ["*"]
  }

  # EventBridge Scheduler read (platform scheduler cron inspection during plan)
  statement {
    sid       = "SchedulerRead"
    actions   = ["scheduler:Get*", "scheduler:List*"]
    resources = ["*"]
  }

  # SSM read — platform outputs published to /platform/<env>/*
  statement {
    sid     = "SsmRead"
    actions = ["ssm:GetParameter", "ssm:GetParameters", "ssm:GetParametersByPath", "ssm:ListTagsForResource"]
    resources = [
      "arn:aws:ssm:${var.region}:${var.account_id}:parameter/platform/${var.env}/*",
    ]
  }
  # ssm:DescribeParameters does not support resource-level permissions; must use Resource:"*"
  statement {
    sid       = "SsmDescribe"
    actions   = ["ssm:DescribeParameters"]
    resources = ["*"]
  }
}

resource "aws_iam_role_policy" "platform_plan" {
  name   = "platform-${var.env}-plan-policy"
  role   = aws_iam_role.platform_plan.id
  policy = data.aws_iam_policy_document.platform_plan.json
}

# ---------------------------------------------------------------------------
# platform-dev-apply  (write; merge/tag apply for platform/shared stack)
# ---------------------------------------------------------------------------

resource "aws_iam_role" "platform_apply" {
  name               = "platform-${var.env}-apply"
  assume_role_policy = data.aws_iam_policy_document.trust["platform_apply"].json
}

data "aws_iam_policy_document" "platform_apply" {
  # State read + write (platform prefix only; use_lockfile = PutObject/DeleteObject)
  statement {
    sid       = "StateReadWrite"
    actions   = ["s3:GetObject", "s3:PutObject", "s3:DeleteObject"]
    resources = ["arn:aws:s3:::${var.state_bucket}/platform/*"]
  }
  statement {
    sid       = "StateBucketList"
    actions   = ["s3:ListBucket"]
    resources = ["arn:aws:s3:::${var.state_bucket}"]
    condition {
      test     = "StringLike"
      variable = "s3:prefix"
      values   = ["platform/*"]
    }
  }

  # IAM — manage OIDC provider + 4 roles/policies in this module
  statement {
    sid = "OidcProvider"
    actions = [
      "iam:CreateOpenIDConnectProvider",
      "iam:GetOpenIDConnectProvider",
      "iam:DeleteOpenIDConnectProvider",
      "iam:TagOpenIDConnectProvider",
      "iam:UntagOpenIDConnectProvider",
      "iam:UpdateOpenIDConnectProviderThumbprint",
      "iam:AddClientIDToOpenIDConnectProvider",
      "iam:RemoveClientIDFromOpenIDConnectProvider",
    ]
    resources = [
      "arn:aws:iam::${var.account_id}:oidc-provider/token.actions.githubusercontent.com",
    ]
  }
  statement {
    sid = "IamRoles"
    actions = [
      "iam:CreateRole",
      "iam:GetRole",
      "iam:UpdateRole",
      "iam:DeleteRole",
      "iam:TagRole",
      "iam:UntagRole",
      "iam:UpdateAssumeRolePolicy",
      "iam:AttachRolePolicy",
      "iam:DetachRolePolicy",
      "iam:ListAttachedRolePolicies",
      "iam:ListRolePolicies",
      "iam:PutRolePolicy",
      "iam:GetRolePolicy",
      "iam:DeleteRolePolicy",
    ]
    resources = [
      "arn:aws:iam::${var.account_id}:role/platform-*",
      "arn:aws:iam::${var.account_id}:role/tasks-*",
    ]
  }
  statement {
    sid = "IamPolicies"
    actions = [
      "iam:CreatePolicy",
      "iam:GetPolicy",
      "iam:GetPolicyVersion",
      "iam:CreatePolicyVersion",
      "iam:DeletePolicyVersion",
      "iam:SetDefaultPolicyVersion",
      "iam:DeletePolicy",
      "iam:TagPolicy",
      "iam:UntagPolicy",
      "iam:ListPolicyVersions",
      "iam:ListEntitiesForPolicy",
    ]
    resources = [
      "arn:aws:iam::${var.account_id}:policy/platform-*",
      "arn:aws:iam::${var.account_id}:policy/tasks-*",
    ]
  }
  # iam:List* (non-resource-specific) for plan inspection
  statement {
    sid       = "IamList"
    actions   = ["iam:List*", "iam:Get*"]
    resources = ["*"]
  }

  # Service read — apply 中の refresh / plan に必要(plan role と同範囲)
  statement {
    sid = "ServiceRead"
    actions = [
      "ec2:Describe*",
      "elasticloadbalancing:Describe*",
      "acm:Describe*",
      "acm:List*",
      "acm:GetCertificate",
      "ses:Get*",
      "ses:List*",
      "ses:Describe*",
      "route53:Get*",
      "route53:List*",
      "rds:Describe*",
      "rds:List*",
      "ecs:Describe*",
      "ecs:List*",
      "ecr:Describe*",
      "ecr:List*",
      "ecr:Get*",
      "ecr:BatchGetImage",
      "logs:DescribeLogGroups",
      "logs:ListTagsLogGroup",
      "logs:ListTagsForResource",
      "lambda:Get*",
      "lambda:List*",
      "scheduler:Get*",
      "scheduler:List*",
    ]
    resources = ["*"]
  }

  # EC2 write (network: VPC / Subnet / IGW / RT / NAT / EIP / S3 GW EP + alb / keycloak_db SG)
  # 規約 R1: 書込み action は完全列挙
  # 規約 R2: 一部 action は resource-level permission 非対応(CreateVpc 等)、残りは apply 時点で対象 ARN が未確定のため Resource: *
  statement {
    sid = "Ec2Write"
    actions = [
      "ec2:CreateVpc",
      "ec2:DeleteVpc",
      "ec2:ModifyVpcAttribute",
      "ec2:CreateSubnet",
      "ec2:DeleteSubnet",
      "ec2:ModifySubnetAttribute",
      "ec2:CreateInternetGateway",
      "ec2:DeleteInternetGateway",
      "ec2:AttachInternetGateway",
      "ec2:DetachInternetGateway",
      "ec2:CreateRouteTable",
      "ec2:DeleteRouteTable",
      "ec2:CreateRoute",
      "ec2:ReplaceRoute",
      "ec2:DeleteRoute",
      "ec2:AssociateRouteTable",
      "ec2:DisassociateRouteTable",
      "ec2:AllocateAddress",
      "ec2:ReleaseAddress",
      "ec2:DisassociateAddress",
      "ec2:CreateNatGateway",
      "ec2:DeleteNatGateway",
      "ec2:CreateVpcEndpoint",
      "ec2:ModifyVpcEndpoint",
      "ec2:DeleteVpcEndpoints",
      "ec2:CreateSecurityGroup",
      "ec2:DeleteSecurityGroup",
      "ec2:AuthorizeSecurityGroupIngress",
      "ec2:AuthorizeSecurityGroupEgress",
      "ec2:RevokeSecurityGroupIngress",
      "ec2:RevokeSecurityGroupEgress",
      "ec2:UpdateSecurityGroupRuleDescriptionsIngress",
      "ec2:UpdateSecurityGroupRuleDescriptionsEgress",
      # SG 差し替え時に RDS 等が保持する ENI の SG 関連付けを外すために必要
      # ec2:ModifyNetworkInterfaceAttribute: ENI の SG リストを更新して旧 SG を除去
      # ec2:DetachNetworkInterface: ModifyNetworkInterfaceAttribute が使えない ENI の最終手段
      # いずれも resource-level permission 非対応のため Resource:"*"
      "ec2:ModifyNetworkInterfaceAttribute",
      "ec2:DetachNetworkInterface",
      "ec2:CreateTags",
      "ec2:DeleteTags",
    ]
    resources = ["*"]
  }

  # ELBv2 write (alb: ALB / Listener / keycloak: TG / Listener Rule)
  # 規約 R1: 書込み action は完全列挙
  # 規約 R2: 一部 action は resource-level permission 非対応(CreateLoadBalancer 等)、残りは apply 時点で対象 ARN が未確定のため Resource: *
  statement {
    sid = "ElbWrite"
    actions = [
      "elasticloadbalancing:CreateLoadBalancer",
      "elasticloadbalancing:DeleteLoadBalancer",
      "elasticloadbalancing:ModifyLoadBalancerAttributes",
      "elasticloadbalancing:SetSecurityGroups",
      "elasticloadbalancing:SetSubnets",
      "elasticloadbalancing:CreateListener",
      "elasticloadbalancing:DeleteListener",
      "elasticloadbalancing:ModifyListener",
      "elasticloadbalancing:CreateTargetGroup",
      "elasticloadbalancing:DeleteTargetGroup",
      "elasticloadbalancing:ModifyTargetGroup",
      "elasticloadbalancing:ModifyTargetGroupAttributes",
      "elasticloadbalancing:CreateRule",
      "elasticloadbalancing:DeleteRule",
      "elasticloadbalancing:ModifyRule",
      "elasticloadbalancing:SetRulePriorities",
      "elasticloadbalancing:RegisterTargets",
      "elasticloadbalancing:DeregisterTargets",
      "elasticloadbalancing:AddTags",
      "elasticloadbalancing:RemoveTags",
    ]
    resources = ["*"]
  }

  # Service-linked role for ELB
  statement {
    sid       = "ElbSlr"
    actions   = ["iam:CreateServiceLinkedRole"]
    resources = ["arn:aws:iam::${var.account_id}:role/aws-service-role/elasticloadbalancing.amazonaws.com/*"]
  }

  # ECS write (platform cluster / keycloak task definition / service)
  # 規約 R1: 書込み action は完全列挙
  # 規約 R2: 一部 action は resource-level permission 非対応(CreateCluster 等)、残りは apply 時点で対象 ARN が未確定のため Resource: *
  statement {
    sid = "EcsWrite"
    actions = [
      "ecs:CreateCluster",
      "ecs:DeleteCluster",
      "ecs:UpdateCluster",
      "ecs:UpdateClusterSettings",
      "ecs:PutClusterCapacityProviders",
      "ecs:RegisterTaskDefinition",
      "ecs:DeregisterTaskDefinition",
      "ecs:CreateService",
      "ecs:UpdateService",
      "ecs:DeleteService",
      "ecs:TagResource",
      "ecs:UntagResource",
    ]
    resources = ["*"]
  }

  # Service-linked role for ECS
  statement {
    sid       = "EcsSlr"
    actions   = ["iam:CreateServiceLinkedRole"]
    resources = ["arn:aws:iam::${var.account_id}:role/aws-service-role/ecs.amazonaws.com/*"]
  }

  # IAM PassRole — required for ecs:RegisterTaskDefinition (execution role + task role)
  # 規約 R1: 書込み action は完全列挙
  # 規約 R2: platform-* ロール ARN に絞る
  statement {
    sid     = "IamPassRole"
    actions = ["iam:PassRole"]
    resources = [
      "arn:aws:iam::${var.account_id}:role/platform-*",
    ]
  }

  # CloudWatch Logs write (keycloak log group)
  # 規約 R1: 書込み action は完全列挙
  # 規約 R2: /ecs/platform-<env>/* ロググループ ARN に絞る
  # logs:DescribeLogGroups は resource-level permission 非対応のため ServiceRead の resources:["*"] ブロックに含める
  statement {
    sid = "LogsWrite"
    actions = [
      "logs:CreateLogGroup",
      "logs:DeleteLogGroup",
      "logs:PutRetentionPolicy",
      "logs:DeleteRetentionPolicy",
      "logs:TagLogGroup",
      "logs:UntagLogGroup",
      "logs:TagResource",
      "logs:UntagResource",
    ]
    resources = [
      "arn:aws:logs:${var.region}:${var.account_id}:log-group:/ecs/platform-${var.env}/*",
    ]
  }

  # ACM write (alb: base wildcard cert)
  # 規約 R1: 書込み action は完全列挙
  # 規約 R2: 一部 action は resource-level permission 非対応(RequestCertificate 等)、残りは apply 時点で対象 ARN が未確定のため Resource: *
  statement {
    sid = "AcmWrite"
    actions = [
      "acm:RequestCertificate",
      "acm:DeleteCertificate",
      "acm:AddTagsToCertificate",
      "acm:RemoveTagsFromCertificate",
    ]
    resources = ["*"]
  }

  # SESv2 write (ses: domain identity / DKIM / Config Set。SESv2 API の IAM prefix も "ses:")
  # 規約 R1: 書込み action は完全列挙
  # 規約 R2: 一部 action は resource-level permission 非対応(CreateConfigurationSet 等)、残りは apply 時点で対象 ARN が未確定のため Resource: *
  statement {
    sid = "SesWrite"
    actions = [
      "ses:CreateEmailIdentity",
      "ses:DeleteEmailIdentity",
      "ses:PutEmailIdentityMailFromAttributes",
      "ses:PutEmailIdentityDkimAttributes",
      "ses:PutEmailIdentityFeedbackAttributes",
      "ses:CreateConfigurationSet",
      "ses:DeleteConfigurationSet",
      "ses:PutConfigurationSetDeliveryOptions",
      "ses:PutConfigurationSetReputationOptions",
      "ses:PutConfigurationSetSendingOptions",
      "ses:PutConfigurationSetTrackingOptions",
      "ses:TagResource",
      "ses:UntagResource",
    ]
    resources = ["*"]
  }

  # Route53 write (shared public zone records / cert validation)
  # 規約 R1: 書込み action は完全列挙
  # 規約 R2: apply 時点で対象ゾーン ID が未確定のため Resource: *
  statement {
    sid = "Route53Write"
    actions = [
      "route53:ChangeResourceRecordSets",
      "route53:ChangeTagsForResource",
    ]
    resources = ["*"]
  }

  # RDS write (keycloak_db: DB instance / subnet group / parameter group)
  # 規約 R1: 書込み action は完全列挙
  # 規約 R2: 一部 action は resource-level permission 非対応(CreateDBSubnetGroup 等)、残りは apply 時点で対象 ARN が未確定のため Resource: *
  statement {
    sid = "RdsWrite"
    actions = [
      "rds:CreateDBInstance",
      "rds:DeleteDBInstance",
      "rds:ModifyDBInstance",
      "rds:CreateDBSubnetGroup",
      "rds:DeleteDBSubnetGroup",
      "rds:ModifyDBSubnetGroup",
      "rds:CreateDBParameterGroup",
      "rds:ModifyDBParameterGroup",
      "rds:DeleteDBParameterGroup",
      "rds:AddTagsToResource",
      "rds:RemoveTagsFromResource",
    ]
    resources = ["*"]
  }
  statement {
    sid       = "RdsSlr"
    actions   = ["iam:CreateServiceLinkedRole"]
    resources = ["arn:aws:iam::${var.account_id}:role/aws-service-role/rds.amazonaws.com/*"]
  }

  # SSM write — publish platform outputs to /platform/<env>/*
  statement {
    sid = "SsmWrite"
    actions = [
      "ssm:PutParameter",
      "ssm:GetParameter",
      "ssm:GetParameters",
      "ssm:GetParametersByPath",
      "ssm:DeleteParameter",
      "ssm:ListTagsForResource",
      "ssm:AddTagsToResource",
      "ssm:RemoveTagsFromResource",
    ]
    resources = [
      "arn:aws:ssm:${var.region}:${var.account_id}:parameter/platform/${var.env}/*",
    ]
  }
  # ssm:DescribeParameters does not support resource-level permissions; must use Resource:"*"
  statement {
    sid       = "SsmDescribe"
    actions   = ["ssm:DescribeParameters"]
    resources = ["*"]
  }

  # Lambda write (platform scheduler function CRUD)
  # 規約 R1: 書込み action は完全列挙
  # 規約 R2: platform-* function ARN にスコープ
  statement {
    sid = "LambdaWrite"
    actions = [
      "lambda:CreateFunction",
      "lambda:DeleteFunction",
      "lambda:UpdateFunctionCode",
      "lambda:UpdateFunctionConfiguration",
      "lambda:TagResource",
      "lambda:UntagResource",
    ]
    resources = ["arn:aws:lambda:${var.region}:${var.account_id}:function:platform-*"]
  }

  # EventBridge Scheduler write (platform scheduler: schedule CRUD, default group)
  # 規約 R1: 書込み action は完全列挙
  # 規約 R2: default group 内 platform-* schedule ARN にスコープ
  statement {
    sid = "SchedulerWrite"
    actions = [
      "scheduler:CreateSchedule",
      "scheduler:UpdateSchedule",
      "scheduler:DeleteSchedule",
      "scheduler:TagResource",
      "scheduler:UntagResource",
    ]
    resources = ["arn:aws:scheduler:${var.region}:${var.account_id}:schedule/default/platform-*"]
  }

  # CloudWatch Logs write for Lambda log groups (/aws/lambda/platform-<env>-*)
  # 規約 R1: 書込み action は完全列挙
  # 規約 R2: /aws/lambda/platform-<env>-* ロググループ ARN にスコープ
  statement {
    sid = "LogsWriteLambda"
    actions = [
      "logs:CreateLogGroup",
      "logs:DeleteLogGroup",
      "logs:PutRetentionPolicy",
      "logs:DeleteRetentionPolicy",
      "logs:TagLogGroup",
      "logs:UntagLogGroup",
      "logs:TagResource",
      "logs:UntagResource",
      "logs:ListTagsLogGroup",
      "logs:ListTagsForResource",
    ]
    resources = [
      "arn:aws:logs:${var.region}:${var.account_id}:log-group:/aws/lambda/platform-${var.env}-*",
    ]
  }
}

resource "aws_iam_role_policy" "platform_apply" {
  name   = "platform-${var.env}-apply-policy"
  role   = aws_iam_role.platform_apply.id
  policy = data.aws_iam_policy_document.platform_apply.json
}

# ---------------------------------------------------------------------------
# tasks-dev-plan  (read-only; PR plan for tasks stack)
# ---------------------------------------------------------------------------

resource "aws_iam_role" "tasks_plan" {
  name               = "tasks-${var.env}-plan"
  assume_role_policy = data.aws_iam_policy_document.trust["tasks_plan"].json
}

data "aws_iam_policy_document" "tasks_plan" {
  # State read (tasks prefix only)
  statement {
    sid       = "StateRead"
    actions   = ["s3:GetObject"]
    resources = ["arn:aws:s3:::${var.state_bucket}/tasks/*"]
  }
  statement {
    sid       = "StateBucketList"
    actions   = ["s3:ListBucket"]
    resources = ["arn:aws:s3:::${var.state_bucket}"]
    condition {
      test     = "StringLike"
      variable = "s3:prefix"
      values   = ["tasks/*"]
    }
  }

  # EC2 read (security_group: SG-ECS / SG-RDS)
  statement {
    sid       = "Ec2Read"
    actions   = ["ec2:Describe*"]
    resources = ["*"]
  }

  # ECS read (ecs_cluster: Cluster / capacity providers)
  statement {
    sid       = "EcsRead"
    actions   = ["ecs:Describe*", "ecs:List*"]
    resources = ["*"]
  }

  # RDS read (rds: DB instance / subnet group)
  statement {
    sid       = "RdsRead"
    actions   = ["rds:Describe*", "rds:List*"]
    resources = ["*"]
  }

  # ECR read (ecr: repository / lifecycle policy)
  statement {
    sid       = "EcrRead"
    actions   = ["ecr:Describe*", "ecr:List*", "ecr:Get*", "ecr:BatchGetImage"]
    resources = ["*"]
  }

  # ELBv2 read (route53 module: TG / Listener Rule)
  statement {
    sid       = "ElbRead"
    actions   = ["elasticloadbalancing:Describe*"]
    resources = ["*"]
  }

  # ACM read (route53 module: regional / CloudFront 証明書)
  statement {
    sid       = "AcmRead"
    actions   = ["acm:Describe*", "acm:List*", "acm:GetCertificate"]
    resources = ["*"]
  }

  # Route53 read (route53 module: PHZ tasks.internal + 各 record)
  statement {
    sid       = "Route53Read"
    actions   = ["route53:Get*", "route53:List*"]
    resources = ["*"]
  }

  # IAM read (webapi task role inspection)
  statement {
    sid       = "IamRead"
    actions   = ["iam:Get*", "iam:List*"]
    resources = ["*"]
  }

  # CloudWatch Logs read (ECS cluster log groups inspection during plan)
  statement {
    sid       = "LogsRead"
    actions   = ["logs:DescribeLogGroups", "logs:ListTagsLogGroup", "logs:ListTagsForResource"]
    resources = ["*"]
  }

  # Lambda read (ecs-scheduler function 検査)
  statement {
    sid       = "LambdaRead"
    actions   = ["lambda:Get*", "lambda:List*"]
    resources = ["*"]
  }

  # EventBridge Scheduler read (ecs-scheduler cron 検査)
  statement {
    sid       = "SchedulerRead"
    actions   = ["scheduler:Get*", "scheduler:List*"]
    resources = ["*"]
  }

  # S3 read (frontend: S3 bucket inspection during plan)
  statement {
    sid     = "S3Read"
    actions = ["s3:Get*", "s3:List*"]
    # s3:GetBucketLocation etc. support resource-level permissions, but bucket ARN is unknown during initial plan
    resources = ["*"]
  }

  # CloudFront read (frontend: distribution / OAC inspection during plan)
  statement {
    sid       = "CloudFrontRead"
    actions   = ["cloudfront:Get*", "cloudfront:List*"]
    resources = ["*"]
  }

  # SSM read — platform outputs + tasks params
  statement {
    sid     = "SsmRead"
    actions = ["ssm:GetParameter", "ssm:GetParameters", "ssm:GetParametersByPath", "ssm:ListTagsForResource"]
    resources = [
      "arn:aws:ssm:${var.region}:${var.account_id}:parameter/platform/${var.env}/*",
      "arn:aws:ssm:${var.region}:${var.account_id}:parameter/tasks/${var.env}/*",
    ]
  }
  # ssm:DescribeParameters does not support resource-level permissions; must use Resource:"*"
  statement {
    sid       = "SsmDescribe"
    actions   = ["ssm:DescribeParameters"]
    resources = ["*"]
  }
}

resource "aws_iam_role_policy" "tasks_plan" {
  name   = "tasks-${var.env}-plan-policy"
  role   = aws_iam_role.tasks_plan.id
  policy = data.aws_iam_policy_document.tasks_plan.json
}

# ---------------------------------------------------------------------------
# tasks-dev-apply  (write; merge/tag apply for tasks stack)
# ---------------------------------------------------------------------------

resource "aws_iam_role" "tasks_apply" {
  name               = "tasks-${var.env}-apply"
  assume_role_policy = data.aws_iam_policy_document.trust["tasks_apply"].json
}

data "aws_iam_policy_document" "tasks_apply" {
  # State read + write (tasks prefix only)
  statement {
    sid       = "StateReadWrite"
    actions   = ["s3:GetObject", "s3:PutObject", "s3:DeleteObject"]
    resources = ["arn:aws:s3:::${var.state_bucket}/tasks/*"]
  }
  statement {
    sid       = "StateBucketList"
    actions   = ["s3:ListBucket"]
    resources = ["arn:aws:s3:::${var.state_bucket}"]
    condition {
      test     = "StringLike"
      variable = "s3:prefix"
      values   = ["tasks/*"]
    }
  }

  # Service read — apply 中の refresh / plan に必要(plan role と同範囲)
  statement {
    sid = "ServiceRead"
    actions = [
      "ec2:Describe*",
      "ecs:Describe*",
      "ecs:List*",
      "rds:Describe*",
      "rds:List*",
      "ecr:Describe*",
      "ecr:List*",
      "ecr:Get*",
      "ecr:BatchGetImage",
      "elasticloadbalancing:Describe*",
      "acm:Describe*",
      "acm:List*",
      "acm:GetCertificate",
      "route53:Get*",
      "route53:List*",
      "s3:Get*",
      "s3:List*",
      "cloudfront:Get*",
      "cloudfront:List*",
      "lambda:Get*",
      "lambda:List*",
      "scheduler:Get*",
      "scheduler:List*",
    ]
    resources = ["*"]
  }

  # EC2 write (security_group: SG-ECS / SG-RDS)
  # 規約 R1: 書込み action は完全列挙
  # 規約 R2: 一部 action は resource-level permission 非対応(CreateSecurityGroup 等)、残りは apply 時点で対象 ARN が未確定のため Resource: *
  statement {
    sid = "Ec2Write"
    actions = [
      "ec2:CreateSecurityGroup",
      "ec2:DeleteSecurityGroup",
      "ec2:AuthorizeSecurityGroupIngress",
      "ec2:AuthorizeSecurityGroupEgress",
      "ec2:RevokeSecurityGroupIngress",
      "ec2:RevokeSecurityGroupEgress",
      "ec2:UpdateSecurityGroupRuleDescriptionsIngress",
      "ec2:UpdateSecurityGroupRuleDescriptionsEgress",
      "ec2:CreateTags",
      "ec2:DeleteTags",
    ]
    resources = ["*"]
  }

  # ECS write (ecs_cluster: Cluster / capacity providers + webapi: Service / Task Definition (S2Infra-2))
  # 規約 R1: 書込み action は完全列挙
  # 規約 R2: 一部 action は resource-level permission 非対応(PutClusterCapacityProviders 等)、残りは apply 時点で対象 ARN が未確定のため Resource: *
  statement {
    sid = "EcsWrite"
    actions = [
      "ecs:CreateCluster",
      "ecs:DeleteCluster",
      "ecs:UpdateCluster",
      "ecs:UpdateClusterSettings",
      "ecs:PutClusterCapacityProviders",
      "ecs:RegisterTaskDefinition",
      "ecs:DeregisterTaskDefinition",
      "ecs:CreateService",
      "ecs:UpdateService",
      "ecs:DeleteService",
      "ecs:TagResource",
      "ecs:UntagResource",
    ]
    resources = ["*"]
  }

  # RDS write (rds: DB instance / subnet group / parameter group)
  # 規約 R1: 書込み action は完全列挙
  # 規約 R2: 一部 action は resource-level permission 非対応(CreateDBSubnetGroup 等)、残りは apply 時点で対象 ARN が未確定のため Resource: *
  statement {
    sid = "RdsWrite"
    actions = [
      "rds:CreateDBInstance",
      "rds:DeleteDBInstance",
      "rds:ModifyDBInstance",
      "rds:CreateDBSubnetGroup",
      "rds:DeleteDBSubnetGroup",
      "rds:ModifyDBSubnetGroup",
      "rds:CreateDBParameterGroup",
      "rds:ModifyDBParameterGroup",
      "rds:DeleteDBParameterGroup",
      "rds:AddTagsToResource",
      "rds:RemoveTagsFromResource",
    ]
    resources = ["*"]
  }
  statement {
    sid       = "RdsSlr"
    actions   = ["iam:CreateServiceLinkedRole"]
    resources = ["arn:aws:iam::${var.account_id}:role/aws-service-role/rds.amazonaws.com/*"]
  }

  # ECR write (ecr: repository / lifecycle policy)
  # 規約 R1: 書込み action は完全列挙
  # 規約 R2: 一部 action は resource-level permission 非対応(CreateRepository 等)、残りは apply 時点で対象 ARN が未確定のため Resource: *
  statement {
    sid = "EcrWrite"
    actions = [
      "ecr:CreateRepository",
      "ecr:DeleteRepository",
      "ecr:PutLifecyclePolicy",
      "ecr:DeleteLifecyclePolicy",
      "ecr:PutImageScanningConfiguration",
      "ecr:PutImageTagMutability",
      "ecr:TagResource",
      "ecr:UntagResource",
    ]
    resources = ["*"]
  }

  # ELBv2 write (route53 module: TG / Listener Rule / cert attach to shared ALB)
  # 規約 R1: 書込み action は完全列挙
  # 規約 R2: 一部 action は resource-level permission 非対応(CreateTargetGroup 等)、残りは apply 時点で対象 ARN が未確定のため Resource: *
  statement {
    sid = "ElbWrite"
    actions = [
      "elasticloadbalancing:CreateTargetGroup",
      "elasticloadbalancing:DeleteTargetGroup",
      "elasticloadbalancing:ModifyTargetGroup",
      "elasticloadbalancing:ModifyTargetGroupAttributes",
      "elasticloadbalancing:CreateRule",
      "elasticloadbalancing:DeleteRule",
      "elasticloadbalancing:ModifyRule",
      "elasticloadbalancing:SetRulePriorities",
      "elasticloadbalancing:AddListenerCertificates",
      "elasticloadbalancing:RemoveListenerCertificates",
      "elasticloadbalancing:AddTags",
      "elasticloadbalancing:RemoveTags",
    ]
    resources = ["*"]
  }

  # ACM write (route53 module: regional / CloudFront 証明書)
  # 規約 R1: 書込み action は完全列挙
  # 規約 R2: 一部 action は resource-level permission 非対応(RequestCertificate 等)、残りは apply 時点で対象 ARN が未確定のため Resource: *
  statement {
    sid = "AcmWrite"
    actions = [
      "acm:RequestCertificate",
      "acm:DeleteCertificate",
      "acm:AddTagsToCertificate",
      "acm:RemoveTagsFromCertificate",
    ]
    resources = ["*"]
  }

  # Route53 write (route53 module: PHZ tasks.internal + 各 record)
  # 規約 R1: 書込み action は完全列挙
  # 規約 R2: apply 時点で対象ゾーン ID が未確定のため Resource: *
  statement {
    sid = "Route53Write"
    actions = [
      "route53:CreateHostedZone",
      "route53:DeleteHostedZone",
      "route53:UpdateHostedZoneComment",
      "route53:AssociateVPCWithHostedZone",
      "route53:DisassociateVPCFromHostedZone",
      "route53:ChangeResourceRecordSets",
      "route53:ChangeTagsForResource",
    ]
    resources = ["*"]
  }

  # IAM (webapi task role 実装済み / SMTP IAM user は未実装)
  statement {
    sid = "IamRoles"
    actions = [
      "iam:CreateRole",
      "iam:GetRole",
      "iam:UpdateRole",
      "iam:DeleteRole",
      "iam:TagRole",
      "iam:UntagRole",
      "iam:UpdateAssumeRolePolicy",
      "iam:AttachRolePolicy",
      "iam:DetachRolePolicy",
      "iam:ListAttachedRolePolicies",
      "iam:ListRolePolicies",
      "iam:PutRolePolicy",
      "iam:GetRolePolicy",
      "iam:DeleteRolePolicy",
      "iam:PassRole",
    ]
    resources = [
      "arn:aws:iam::${var.account_id}:role/tasks-*",
    ]
  }
  statement {
    sid = "IamUsers"
    actions = [
      "iam:CreateUser",
      "iam:GetUser",
      "iam:DeleteUser",
      "iam:TagUser",
      "iam:UntagUser",
      "iam:CreateAccessKey",
      "iam:DeleteAccessKey",
      "iam:ListAccessKeys",
      "iam:AttachUserPolicy",
      "iam:DetachUserPolicy",
      "iam:ListAttachedUserPolicies",
      "iam:PutUserPolicy",
      "iam:GetUserPolicy",
      "iam:DeleteUserPolicy",
    ]
    resources = [
      "arn:aws:iam::${var.account_id}:user/tasks-*",
    ]
  }
  statement {
    sid       = "IamList"
    actions   = ["iam:List*", "iam:Get*"]
    resources = ["*"]
  }
  statement {
    sid       = "EcsSlr"
    actions   = ["iam:CreateServiceLinkedRole"]
    resources = ["arn:aws:iam::${var.account_id}:role/aws-service-role/ecs.amazonaws.com/*"]
  }

  # CloudWatch Logs CRUD scoped to ECS task log groups
  statement {
    sid = "LogsWrite"
    actions = [
      "logs:CreateLogGroup",
      "logs:DeleteLogGroup",
      "logs:PutRetentionPolicy",
      "logs:DeleteRetentionPolicy",
      "logs:TagLogGroup",
      "logs:UntagLogGroup",
      "logs:TagResource",
      "logs:UntagResource",
      "logs:ListTagsLogGroup",
      "logs:ListTagsForResource",
    ]
    resources = [
      "arn:aws:logs:${var.region}:${var.account_id}:log-group:/ecs/tasks-${var.env}/*",
    ]
  }
  # logs:DescribeLogGroups does not support resource-level permissions; must use Resource:"*"
  statement {
    sid       = "LogsDescribe"
    actions   = ["logs:DescribeLogGroups"]
    resources = ["*"]
  }

  # S3 write (frontend: bucket / public access block / bucket policy)
  # 規約 R1: 書込み action は完全列挙
  # 規約 R2: scoped to tasks-<env>-frontend bucket ARN
  statement {
    sid = "S3Write"
    actions = [
      "s3:CreateBucket",
      "s3:DeleteBucket",
      "s3:PutBucketPolicy",
      "s3:DeleteBucketPolicy",
      "s3:PutBucketPublicAccessBlock",
      "s3:PutBucketTagging",
      "s3:DeleteBucketTagging",
    ]
    resources = ["arn:aws:s3:::tasks-${var.env}-frontend"]
  }

  # CloudFront write (frontend: distribution + OAC + Response Headers Policy)
  # 規約 R1: 書込み action は完全列挙
  # 規約 R2: 一部 action は resource-level permission 非対応(CreateDistribution 等)、残りは apply 時点で対象 ARN が未確定のため Resource: *
  statement {
    sid = "CloudFrontWrite"
    actions = [
      "cloudfront:CreateDistribution",
      "cloudfront:UpdateDistribution",
      "cloudfront:DeleteDistribution",
      "cloudfront:TagResource",
      "cloudfront:UntagResource",
      "cloudfront:CreateOriginAccessControl",
      "cloudfront:UpdateOriginAccessControl",
      "cloudfront:DeleteOriginAccessControl",
      "cloudfront:CreateResponseHeadersPolicy",
      "cloudfront:UpdateResponseHeadersPolicy",
      "cloudfront:DeleteResponseHeadersPolicy",
    ]
    resources = ["*"]
  }

  # Lambda write (ecs-scheduler: function CRUD)
  # 規約 R1: 書込み action は完全列挙
  # 規約 R2: tasks-* function ARN にスコープ
  statement {
    sid = "LambdaWrite"
    actions = [
      "lambda:CreateFunction",
      "lambda:DeleteFunction",
      "lambda:UpdateFunctionCode",
      "lambda:UpdateFunctionConfiguration",
      "lambda:TagResource",
      "lambda:UntagResource",
    ]
    resources = ["arn:aws:lambda:${var.region}:${var.account_id}:function:tasks-*"]
  }

  # EventBridge Scheduler write (ecs-scheduler: schedule CRUD, default group)
  # 規約 R1: 書込み action は完全列挙
  # 規約 R2: default group 内 tasks-* schedule ARN にスコープ
  statement {
    sid = "SchedulerWrite"
    actions = [
      "scheduler:CreateSchedule",
      "scheduler:UpdateSchedule",
      "scheduler:DeleteSchedule",
      "scheduler:TagResource",
      "scheduler:UntagResource",
    ]
    resources = ["arn:aws:scheduler:${var.region}:${var.account_id}:schedule/default/tasks-*"]
  }

  # CloudWatch Logs write for Lambda log groups (/aws/lambda/tasks-<env>-*)
  # 規約 R1: 書込み action は完全列挙
  # 規約 R2: /aws/lambda/tasks-<env>-* ロググループ ARN にスコープ
  statement {
    sid = "LogsWriteLambda"
    actions = [
      "logs:CreateLogGroup",
      "logs:DeleteLogGroup",
      "logs:PutRetentionPolicy",
      "logs:DeleteRetentionPolicy",
      "logs:TagLogGroup",
      "logs:UntagLogGroup",
      "logs:TagResource",
      "logs:UntagResource",
      "logs:ListTagsLogGroup",
      "logs:ListTagsForResource",
    ]
    resources = [
      "arn:aws:logs:${var.region}:${var.account_id}:log-group:/aws/lambda/tasks-${var.env}-*",
    ]
  }

  # SSM — read platform outputs, write tasks params
  statement {
    sid     = "SsmReadPlatform"
    actions = ["ssm:GetParameter", "ssm:GetParameters", "ssm:GetParametersByPath", "ssm:ListTagsForResource"]
    resources = [
      "arn:aws:ssm:${var.region}:${var.account_id}:parameter/platform/${var.env}/*",
    ]
  }
  statement {
    sid = "SsmWriteTasks"
    actions = [
      "ssm:PutParameter",
      "ssm:GetParameter",
      "ssm:GetParameters",
      "ssm:GetParametersByPath",
      "ssm:DeleteParameter",
      "ssm:ListTagsForResource",
      "ssm:AddTagsToResource",
      "ssm:RemoveTagsFromResource",
    ]
    resources = [
      "arn:aws:ssm:${var.region}:${var.account_id}:parameter/tasks/${var.env}/*",
    ]
  }
  # ssm:DescribeParameters does not support resource-level permissions; must use Resource:"*"
  statement {
    sid       = "SsmDescribe"
    actions   = ["ssm:DescribeParameters"]
    resources = ["*"]
  }
}

resource "aws_iam_role_policy" "tasks_apply" {
  name   = "tasks-${var.env}-apply-policy"
  role   = aws_iam_role.tasks_apply.id
  policy = data.aws_iam_policy_document.tasks_apply.json
}

# ---------------------------------------------------------------------------
# release-build  (write; v* tag → ECR push + S3 web bundle upload)
# Trust: environment:release-build (GitHub Environment, works for both tag
# push and workflow_dispatch triggers when jobs use environment: release-build)
# ---------------------------------------------------------------------------

resource "aws_iam_role" "release_build" {
  name               = "release-build"
  assume_role_policy = data.aws_iam_policy_document.trust["release_build"].json
}

data "aws_iam_policy_document" "release_build" {
  # ECR auth token — GetAuthorizationToken does not support resource-level
  statement {
    sid       = "EcrAuth"
    actions   = ["ecr:GetAuthorizationToken"]
    resources = ["*"]
  }

  # ECR push + retag for tasks-webapi and keycloak-custom
  # ecr:GetDownloadUrlForLayer is intentionally omitted: all base images (keycloak FROM quay.io,
  # webapi builder from Paketo/paketobuildpacks) are pulled from public registries, not ECR.
  # Smoke test runs against the locally-built image before ECR push, so pull is not needed.
  statement {
    sid = "EcrPush"
    actions = [
      "ecr:BatchCheckLayerAvailability",
      "ecr:InitiateLayerUpload",
      "ecr:UploadLayerPart",
      "ecr:CompleteLayerUpload",
      "ecr:PutImage",
      "ecr:BatchGetImage",
      "ecr:DescribeImages",
      "ecr:ListImages",
    ]
    resources = [
      "arn:aws:ecr:${var.region}:${var.account_id}:repository/tasks-webapi",
      "arn:aws:ecr:${var.region}:${var.account_id}:repository/keycloak-custom",
    ]
  }

  # S3: web bundle objects under web/ prefix (GetObject needed for s3 cp server-side copy)
  # s3:DeleteObject is omitted: s3 sync runs without --delete, and s3 cp does not delete objects.
  statement {
    sid     = "S3WebBundleObjects"
    actions = ["s3:GetObject", "s3:PutObject"]
    resources = [
      "arn:aws:s3:::tasks-${var.env}-frontend/web/*",
    ]
  }

  # s3:ListBucket does not support resource-level prefix restrictions inline;
  # use a condition to scope it to the web/ prefix
  statement {
    sid       = "S3WebBundleList"
    actions   = ["s3:ListBucket"]
    resources = ["arn:aws:s3:::tasks-${var.env}-frontend"]
    condition {
      test     = "StringLike"
      variable = "s3:prefix"
      values   = ["web/*"]
    }
  }
}

resource "aws_iam_role_policy" "release_build" {
  name   = "release-build-policy"
  role   = aws_iam_role.release_build.id
  policy = data.aws_iam_policy_document.release_build.json
}

# ---------------------------------------------------------------------------
# tasks-<env>-deploy  (write; deploy workflow — webapi ECS + web S3/CF + verify)
# Trust: environment:<env> (e.g. "dev") — GitHub Environment for the deploy target
# Used by deploy.yml jobs: verify / deploy-webapi / deploy-web
# ---------------------------------------------------------------------------

resource "aws_iam_role" "tasks_deploy" {
  name               = "tasks-${var.env}-deploy"
  assume_role_policy = data.aws_iam_policy_document.trust["tasks_deploy"].json

  tags = {
    Name = "tasks-${var.env}-deploy"
  }
}

data "aws_iam_policy_document" "tasks_deploy" {
  # ECR read — both repos for artifact verify + no-op digest check
  # ecr:DescribeImages のみ使用(aws ecr describe-images); BatchGetImage / GetAuthorizationToken 不要
  statement {
    sid     = "EcrRead"
    actions = ["ecr:DescribeImages"]
    resources = [
      "arn:aws:ecr:${var.region}:${var.account_id}:repository/tasks-webapi",
      "arn:aws:ecr:${var.region}:${var.account_id}:repository/keycloak-custom",
    ]
  }

  # ECS — webapi task definition 更新 + サービス安定待ち
  # 規約 R1: 書込み action は完全列挙
  # 規約 R2: RegisterTaskDefinition / DescribeTaskDefinition は resource-level 非対応 → Resource: *
  statement {
    sid     = "EcsTaskDefinition"
    actions = ["ecs:DescribeTaskDefinition", "ecs:RegisterTaskDefinition"]
    # RegisterTaskDefinition / DescribeTaskDefinition は resource-level permission 非対応
    resources = ["*"]
  }

  # 規約 R2: UpdateService / DescribeServices は resource-level 対応 → クラスター/サービス ARN にスコープ
  statement {
    sid     = "EcsServiceUpdate"
    actions = ["ecs:UpdateService", "ecs:DescribeServices"]
    resources = [
      "arn:aws:ecs:${var.region}:${var.account_id}:cluster/tasks-${var.env}-cluster",
      "arn:aws:ecs:${var.region}:${var.account_id}:service/tasks-${var.env}-cluster/tasks-${var.env}-webapi",
    ]
  }

  # IAM PassRole — ecs:RegisterTaskDefinition 時に task execution role / task role を指定するために必要
  # 規約 R1: 書込み action は完全列挙
  # 規約 R2: tasks-<env>-webapi-* ロール ARN にスコープ
  statement {
    sid     = "IamPassRole"
    actions = ["iam:PassRole"]
    resources = [
      "arn:aws:iam::${var.account_id}:role/tasks-${var.env}-webapi-exec-role",
      "arn:aws:iam::${var.account_id}:role/tasks-${var.env}-webapi-task-role",
    ]
  }

  # S3: web バンドルの読み書き(web/<version>/ から web/live/ へ sync)
  # 規約 R1: 書込み action は完全列挙
  # GetObject: コピー元バージョン prefix (web/<version>/) の読み取り
  # PutObject: コピー先 live/ への書き込み
  # DeleteObject: sync --delete で live/ 内の旧ファイル削除; バージョン prefix は削除不可
  statement {
    sid     = "S3WebBundleReadWrite"
    actions = ["s3:GetObject", "s3:PutObject"]
    resources = [
      "arn:aws:s3:::tasks-${var.env}-frontend/web/*",
    ]
  }
  statement {
    sid     = "S3WebLiveDelete"
    actions = ["s3:DeleteObject"]
    resources = [
      "arn:aws:s3:::tasks-${var.env}-frontend/web/live/*",
    ]
  }
  # s3:ListBucket は resource-level prefix 制限を condition で行う
  statement {
    sid       = "S3WebBundleList"
    actions   = ["s3:ListBucket"]
    resources = ["arn:aws:s3:::tasks-${var.env}-frontend"]
    condition {
      test     = "StringLike"
      variable = "s3:prefix"
      values   = ["web/*"]
    }
  }

  # CloudFront invalidation — live/ prefix を無効化して新バンドルを即時配信
  # 規約 R1: 書込み action は完全列挙
  # 規約 R2: distribution ID は apply 時点で未確定のため account-scope pattern で最小化
  statement {
    sid     = "CloudFrontInvalidate"
    actions = ["cloudfront:CreateInvalidation"]
    resources = [
      "arn:aws:cloudfront::${var.account_id}:distribution/*",
    ]
  }
}

resource "aws_iam_role_policy" "tasks_deploy" {
  name   = "tasks-${var.env}-deploy-policy"
  role   = aws_iam_role.tasks_deploy.id
  policy = data.aws_iam_policy_document.tasks_deploy.json
}

# ---------------------------------------------------------------------------
# platform-<env>-deploy  (write; deploy workflow — keycloak ECS on platform cluster)
# Trust: environment:<env> (e.g. "dev") — GitHub Environment for the deploy target
# Used by deploy.yml job: deploy-keycloak
# ---------------------------------------------------------------------------

resource "aws_iam_role" "platform_deploy" {
  name               = "platform-${var.env}-deploy"
  assume_role_policy = data.aws_iam_policy_document.trust["platform_deploy"].json

  tags = {
    Name = "platform-${var.env}-deploy"
  }
}

data "aws_iam_policy_document" "platform_deploy" {
  # ECR read — keycloak-custom のみ(no-op digest check)
  # ecr:DescribeImages のみ使用(aws ecr describe-images); BatchGetImage / GetAuthorizationToken 不要
  statement {
    sid     = "EcrRead"
    actions = ["ecr:DescribeImages"]
    resources = [
      "arn:aws:ecr:${var.region}:${var.account_id}:repository/keycloak-custom",
    ]
  }

  # ECS — platform cluster の keycloak task definition 更新
  # 規約 R1: 書込み action は完全列挙
  # 規約 R2: RegisterTaskDefinition / DescribeTaskDefinition は resource-level 非対応 → Resource: *
  statement {
    sid     = "EcsTaskDefinition"
    actions = ["ecs:DescribeTaskDefinition", "ecs:RegisterTaskDefinition"]
    # RegisterTaskDefinition / DescribeTaskDefinition は resource-level permission 非対応
    resources = ["*"]
  }

  # 規約 R2: UpdateService / DescribeServices は resource-level 対応 → クラスター/サービス ARN にスコープ
  statement {
    sid     = "EcsServiceUpdate"
    actions = ["ecs:UpdateService", "ecs:DescribeServices"]
    resources = [
      "arn:aws:ecs:${var.region}:${var.account_id}:cluster/platform-${var.env}-cluster",
      "arn:aws:ecs:${var.region}:${var.account_id}:service/platform-${var.env}-cluster/platform-${var.env}-keycloak",
    ]
  }

  # IAM PassRole — ecs:RegisterTaskDefinition 時に keycloak 実行ロールを指定するために必要
  # 規約 R1: 書込み action は完全列挙
  # 規約 R2: platform-<env>-keycloak-* ロール ARN にスコープ
  statement {
    sid     = "IamPassRole"
    actions = ["iam:PassRole"]
    resources = [
      "arn:aws:iam::${var.account_id}:role/platform-${var.env}-keycloak-exec-role",
      "arn:aws:iam::${var.account_id}:role/platform-${var.env}-keycloak-task-role",
    ]
  }
}

resource "aws_iam_role_policy" "platform_deploy" {
  name   = "platform-${var.env}-deploy-policy"
  role   = aws_iam_role.platform_deploy.id
  policy = data.aws_iam_policy_document.platform_deploy.json
}
