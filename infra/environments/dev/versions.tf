terraform {
  # use_lockfile(S3 ネイティブ state ロック)は Terraform 1.11 GA。
  required_version = ">= 1.11"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
    archive = {
      source  = "hashicorp/archive"
      version = "~> 2.0"
    }
  }
}

provider "aws" {
  region = var.region

  default_tags {
    tags = {
      Project   = "tasks"
      Env       = var.env
      ManagedBy = "terraform"
    }
  }
}

provider "aws" {
  alias  = "us_east_1"
  region = "us-east-1"

  default_tags {
    tags = {
      Project   = "tasks"
      Env       = var.env
      ManagedBy = "terraform"
    }
  }
}
