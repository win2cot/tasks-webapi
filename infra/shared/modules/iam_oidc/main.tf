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

  # RDS write (keycloak_db: DB instance / subnet group)
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

  # ECS write (ecs_cluster: Cluster / capacity providers。Service / Task Definition は S2Infra-2 で追加)
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
      "ecs:TagResource",
      "ecs:UntagResource",
    ]
    resources = ["*"]
  }

  # RDS write (rds: DB instance / subnet group)
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
