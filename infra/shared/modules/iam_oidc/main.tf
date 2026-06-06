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
    platform_plan  = "platform-plan"
    platform_apply = "platform-apply"
    tasks_plan     = "tasks-plan"
    tasks_apply    = "tasks-apply"
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

  # EC2 read (future: VPC / Subnet / IGW / RT / NAT / EIP / VPC Endpoints)
  statement {
    sid       = "Ec2Read"
    actions   = ["ec2:Describe*"]
    resources = ["*"]
  }

  # ELBv2 read (future: ALB / Listener)
  statement {
    sid       = "ElbRead"
    actions   = ["elasticloadbalancing:Describe*"]
    resources = ["*"]
  }

  # ACM read (future: base wildcard cert)
  statement {
    sid       = "AcmRead"
    actions   = ["acm:Describe*", "acm:List*", "acm:GetCertificate"]
    resources = ["*"]
  }

  # SES read (future: domain identity / DKIM / Config Set)
  statement {
    sid       = "SesRead"
    actions   = ["ses:Get*", "ses:List*", "ses:Describe*"]
    resources = ["*"]
  }

  # Route53 read (future: shared public zone)
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

  # EC2 CRUD (future: VPC / Subnet / IGW / RT / NAT / EIP / S3 GW EP)
  statement {
    sid       = "Ec2Write"
    actions   = ["ec2:*"]
    resources = ["*"]
  }

  # ELBv2 CRUD (future: ALB / Listener / SG-ALB)
  statement {
    sid       = "ElbWrite"
    actions   = ["elasticloadbalancing:*"]
    resources = ["*"]
  }

  # Service-linked role for ELB (future)
  statement {
    sid       = "ElbSlr"
    actions   = ["iam:CreateServiceLinkedRole"]
    resources = ["arn:aws:iam::${var.account_id}:role/aws-service-role/elasticloadbalancing.amazonaws.com/*"]
  }

  # ACM CRUD (future: base wildcard cert)
  statement {
    sid       = "AcmWrite"
    actions   = ["acm:*"]
    resources = ["*"]
  }

  # SES CRUD (future: domain identity / DKIM / Config Set)
  statement {
    sid       = "SesWrite"
    actions   = ["ses:*"]
    resources = ["*"]
  }

  # Route53 CRUD (future: shared public zone records)
  statement {
    sid       = "Route53Write"
    actions   = ["route53:*"]
    resources = ["*"]
  }

  # RDS CRUD (Keycloak DB: subnet group / parameter group / DB instance)
  statement {
    sid       = "RdsWrite"
    actions   = ["rds:*"]
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

  # EC2 read (future: SG inspection)
  statement {
    sid       = "Ec2Read"
    actions   = ["ec2:Describe*"]
    resources = ["*"]
  }

  # ECS read (future)
  statement {
    sid       = "EcsRead"
    actions   = ["ecs:Describe*", "ecs:List*"]
    resources = ["*"]
  }

  # RDS read (future)
  statement {
    sid       = "RdsRead"
    actions   = ["rds:Describe*", "rds:List*"]
    resources = ["*"]
  }

  # ECR read (future)
  statement {
    sid       = "EcrRead"
    actions   = ["ecr:Describe*", "ecr:List*", "ecr:Get*", "ecr:BatchGetImage"]
    resources = ["*"]
  }

  # ELBv2 read (future: listener rule / TG)
  statement {
    sid       = "ElbRead"
    actions   = ["elasticloadbalancing:Describe*"]
    resources = ["*"]
  }

  # ACM read (future: deep cert)
  statement {
    sid       = "AcmRead"
    actions   = ["acm:Describe*", "acm:List*", "acm:GetCertificate"]
    resources = ["*"]
  }

  # Route53 read (future: PHZ)
  statement {
    sid       = "Route53Read"
    actions   = ["route53:Get*", "route53:List*"]
    resources = ["*"]
  }

  # IAM read (future: SMTP user / task role inspection)
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

  # EC2 CRUD (future: SG-ECS / SG-RDS)
  statement {
    sid       = "Ec2Write"
    actions   = ["ec2:*"]
    resources = ["*"]
  }

  # ECS CRUD (future: Cluster / Service / Task Definition)
  statement {
    sid       = "EcsWrite"
    actions   = ["ecs:*"]
    resources = ["*"]
  }

  # RDS CRUD (future: DB instance)
  statement {
    sid       = "RdsWrite"
    actions   = ["rds:*"]
    resources = ["*"]
  }

  # ECR CRUD (future)
  statement {
    sid       = "EcrWrite"
    actions   = ["ecr:*"]
    resources = ["*"]
  }

  # ELBv2 write (future: listener rule / TG / cert attach to shared ALB)
  statement {
    sid       = "ElbWrite"
    actions   = ["elasticloadbalancing:*"]
    resources = ["*"]
  }

  # ACM CRUD (future: deep cert *.tasks.dgz48.xyz)
  statement {
    sid       = "AcmWrite"
    actions   = ["acm:*"]
    resources = ["*"]
  }

  # Route53 CRUD (future: PHZ tasks.internal + alias record)
  statement {
    sid       = "Route53Write"
    actions   = ["route53:*"]
    resources = ["*"]
  }

  # IAM (future: tasks ECS task role / SMTP IAM user)
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
