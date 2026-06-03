# platform(共有インフラ)stack の state backend。
# bucket は複数 project 共用の中立名(bootstrap 手順は infra/docs/bootstrap.md)。
# ロックは use_lockfile(S3 ネイティブ、DynamoDB 不採用)。key prefix で tasks stack と分離。
terraform {
  backend "s3" {
    bucket       = "dgz48-tfstate"
    key          = "platform/dev/terraform.tfstate"
    region       = "ap-northeast-1"
    encrypt      = true
    use_lockfile = true
  }
}
