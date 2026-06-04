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
