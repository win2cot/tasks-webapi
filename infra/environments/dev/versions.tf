terraform {
  # use_lockfile(S3 ネイティブ state ロック)は Terraform 1.11 GA。
  required_version = ">= 1.11"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}
