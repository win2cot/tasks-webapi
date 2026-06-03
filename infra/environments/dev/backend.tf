# tasks(プロジェクト専用インフラ)stack の state backend。
# platform stack と同一 bucket を key prefix で共用(bootstrap 手順は infra/docs/bootstrap.md)。
# ロックは use_lockfile(S3 ネイティブ、DynamoDB 不採用)。
terraform {
  backend "s3" {
    bucket       = "dgz48-tfstate"
    key          = "tasks/dev/terraform.tfstate"
    region       = "ap-northeast-1"
    encrypt      = true
    use_lockfile = true
  }
}
