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
