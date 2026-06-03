# Terraform state backend bootstrap 手順

Terraform の remote state を置く S3 バケットは、自分自身の state に入れられない純粋ブートストラップ
（chicken-and-egg）。そのため **初回のみ admin 権限の手元 CLI で 1 回だけ手動作成**する。これは
ADR-0004 の確定前提「手元 apply しない」の唯一の例外。バケット作成後は OIDC（S0Infra-3 / #246）+
remote state に乗せ、以降の `terraform apply` を手元で実行しない。

関連 ADR: ADR-0002（プロジェクト構造）/ ADR-0004（platform / tasks 分離）。

## 構成

- **S3 バケット 1 個のみ**: `dgz48-tfstate`（複数 project の state を集約する中立名、グローバル一意）。
  DynamoDB テーブルは作らない。
- **ロック**: `use_lockfile = true`（S3 ネイティブ、Terraform 1.11 GA）。DynamoDB ロックは deprecated の
  ため不採用。key 単位ロックのため共有バケットでも platform / tasks の衝突なし。
- **バケット設定**: versioning ON / デフォルト暗号化 SSE-S3（AES256, BucketKey ON）/ public access 全ブロック。
- **key レイアウト**: `platform/dev/terraform.tfstate` / `tasks/dev/terraform.tfstate`
  （将来 env / project 追加時は `<project>/<env>/terraform.tfstate`）。
- **`required_version`**: `>= 1.11`（use_lockfile 要件。実値は着手時の最新 stable で固定）。

## 初回 bootstrap（admin creds で 1 回だけ、手元実行）

SSO で admin 相当のクレデンシャルにログインした状態で、以下を 1 回だけ実行する
（リージョンは `ap-northeast-1`）。

```bash
BUCKET=dgz48-tfstate
REGION=ap-northeast-1

aws s3api create-bucket --bucket "$BUCKET" --region "$REGION" \
  --create-bucket-configuration LocationConstraint="$REGION"

aws s3api put-bucket-versioning --bucket "$BUCKET" \
  --versioning-configuration Status=Enabled

aws s3api put-bucket-encryption --bucket "$BUCKET" \
  --server-side-encryption-configuration \
  '{"Rules":[{"ApplyServerSideEncryptionByDefault":{"SSEAlgorithm":"AES256"},"BucketKeyEnabled":true}]}'

aws s3api put-public-access-block --bucket "$BUCKET" \
  --public-access-block-configuration \
  BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true
```

## backend.tf（各 root）

platform stack（`infra/shared/environments/dev/backend.tf`）は key=`platform/dev/...`、
tasks stack（`infra/environments/dev/backend.tf`）は key=`tasks/dev/...`。

```hcl
terraform {
  backend "s3" {
    bucket       = "dgz48-tfstate"
    key          = "platform/dev/terraform.tfstate" # tasks 側は tasks/dev/terraform.tfstate
    region       = "ap-northeast-1"
    encrypt      = true
    use_lockfile = true
  }
}
```

バケット作成後、各 root で `terraform init` が成功することを確認する。

```bash
cd infra/shared/environments/dev && terraform init   # platform stack
cd infra/environments/dev        && terraform init   # tasks stack
```

## IAM（S0Infra-3 / #246 で実装）

OIDC の platform-apply / tasks-apply role は `dgz48-tfstate` への S3 アクセスを **key prefix で分離**する
（platform-apply → `platform/*`、tasks-apply → `tasks/*`）+ バケット list。use_lockfile は
`s3:PutObject` / `s3:GetObject` / `s3:DeleteObject` で完結し、DynamoDB 権限は不要。

## staging / prod 構築時

同じバケット `dgz48-tfstate` を流用し、key の env 部分を差し替える
（例: `platform/stg/terraform.tfstate` / `tasks/prd/terraform.tfstate`）。バケットの再作成は不要。
本ファイルの bootstrap は dev 初回の 1 回のみ。

## バケットの terraform 管理

当面はブートストラップとして terraform 管理外に置く。必要になった時点で platform stack に
`import` できる。
