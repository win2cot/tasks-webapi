provider "aws" {
  region = "ap-northeast-1"

  default_tags {
    tags = {
      Project     = "platform"
      Environment = "dev"
      ManagedBy   = "terraform"
    }
  }
}

module "iam_oidc" {
  source = "../../modules/iam_oidc"

  account_id   = "138285070797"
  repo         = "win2cot/tasks-webapi"
  state_bucket = "dgz48-tfstate"
  env          = "dev"
  region       = "ap-northeast-1"
}

module "network" {
  source = "../../modules/network"

  env    = "dev"
  region = "ap-northeast-1"

  availability_zones = ["ap-northeast-1a", "ap-northeast-1c"]
}

module "alb" {
  source = "../../modules/alb"

  env               = "dev"
  vpc_id            = module.network.vpc_id
  public_subnet_ids = module.network.public_subnet_ids
  base_domain       = "dgz48.xyz"
}

module "ses" {
  source = "../../modules/ses"

  env         = "dev"
  base_domain = "dgz48.xyz"
  region      = "ap-northeast-1"
}

# ---------------------------------------------------------------------------
# SSM: publish network outputs for tasks stack (ADR-0004)
# ---------------------------------------------------------------------------

resource "aws_ssm_parameter" "vpc_id" {
  name  = "/platform/dev/vpc-id"
  type  = "String"
  value = module.network.vpc_id
}

resource "aws_ssm_parameter" "vpc_cidr" {
  name  = "/platform/dev/vpc-cidr"
  type  = "String"
  value = module.network.vpc_cidr
}

resource "aws_ssm_parameter" "public_subnet_ids" {
  name  = "/platform/dev/public-subnet-ids"
  type  = "StringList"
  value = join(",", module.network.public_subnet_ids)
}

resource "aws_ssm_parameter" "private_subnet_ids" {
  name  = "/platform/dev/private-subnet-ids"
  type  = "StringList"
  value = join(",", module.network.private_subnet_ids)
}

resource "aws_ssm_parameter" "private_route_table_ids" {
  name  = "/platform/dev/private-route-table-ids"
  type  = "String"
  value = module.network.private_route_table_id
}

# ---------------------------------------------------------------------------
# SSM: publish ALB outputs for tasks stack (ADR-0004)
# ---------------------------------------------------------------------------

resource "aws_ssm_parameter" "alb_arn" {
  name  = "/platform/dev/alb-arn"
  type  = "String"
  value = module.alb.alb_arn
}

resource "aws_ssm_parameter" "alb_https_listener_arn" {
  name  = "/platform/dev/alb-https-listener-arn"
  type  = "String"
  value = module.alb.https_listener_arn
}

resource "aws_ssm_parameter" "alb_sg_id" {
  name  = "/platform/dev/alb-sg-id"
  type  = "String"
  value = module.alb.sg_id
}

resource "aws_ssm_parameter" "alb_dns_name" {
  name  = "/platform/dev/alb-dns-name"
  type  = "String"
  value = module.alb.dns_name
}

resource "aws_ssm_parameter" "alb_zone_id" {
  name  = "/platform/dev/alb-zone-id"
  type  = "String"
  value = module.alb.zone_id
}

resource "aws_ssm_parameter" "alb_base_cert_arn" {
  name  = "/platform/dev/alb-base-cert-arn"
  type  = "String"
  value = module.alb.base_cert_arn
}

# ---------------------------------------------------------------------------
# SSM: publish SES outputs for tasks stack (ADR-0004)
# ---------------------------------------------------------------------------

resource "aws_ssm_parameter" "ses_config_set" {
  name  = "/platform/dev/ses-config-set"
  type  = "String"
  value = module.ses.config_set_name
}

# ---------------------------------------------------------------------------
# Keycloak 専用 DB (ADR-0004 / #387)
# Always-on; Keycloak runtime requires persistent DB (not subject to stop schedule)
# SecureString placeholder: set /platform/dev/keycloak-db-password in SSM console after apply
# ---------------------------------------------------------------------------

module "keycloak_db" {
  source = "../../modules/keycloak_db"

  env                = "dev"
  vpc_id             = module.network.vpc_id
  vpc_cidr           = module.network.vpc_cidr
  private_subnet_ids = module.network.private_subnet_ids
  db_password        = "CHANGE_ME"

  # iam_oidc が platform_apply ロールポリシー(rds:*)を更新してから RDS を作成する。
  # 同一 apply で並列実行するとポリシー反映前に rds:CreateDBSubnetGroup が失敗するため。
  depends_on = [module.iam_oidc]
}

# ---------------------------------------------------------------------------
# SSM: publish Keycloak DB connection info for platform consumers (#322)
# ---------------------------------------------------------------------------

resource "aws_ssm_parameter" "keycloak_db_endpoint" {
  name  = "/platform/dev/keycloak-db-endpoint"
  type  = "String"
  value = module.keycloak_db.db_endpoint
}

resource "aws_ssm_parameter" "keycloak_db_port" {
  name  = "/platform/dev/keycloak-db-port"
  type  = "String"
  value = tostring(module.keycloak_db.db_port)
}

resource "aws_ssm_parameter" "keycloak_db_name" {
  name  = "/platform/dev/keycloak-db-name"
  type  = "String"
  value = module.keycloak_db.db_name
}

resource "aws_ssm_parameter" "keycloak_db_username" {
  name  = "/platform/dev/keycloak-db-username"
  type  = "String"
  value = module.keycloak_db.db_username
}

resource "aws_ssm_parameter" "keycloak_db_sg_id" {
  name  = "/platform/dev/keycloak-db-sg-id"
  type  = "String"
  value = module.keycloak_db.sg_id
}
