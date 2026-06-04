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
