# Parameter Store 設計ドキュメント（SSOT）

**版**: 1.0.0 / **策定日**: 2026-06-04 / **担当**: win2cot

> **本ドキュメントは `/tasks/*` SSM Parameter Store パラメータの SSOT。**
> 将来パラメータを追加する際は必ず本 doc に追記してから実装すること。

---

## 目次

1. [階層構造図](#1-階層構造図)
2. [パラメータ一覧表](#2-パラメータ一覧表)
3. [アクセス IAM Policy 例](#3-アクセス-iam-policy-例)
4. [初期セットアップ手順](#4-初期セットアップ手順)
5. [回転ポリシー](#5-回転ポリシー)
6. [将来追加予定パラメータ](#6-将来追加予定パラメータ)

---

## 1. 階層構造図

```text
/tasks/
└─ <env>/               # dev / stg / prd
    ├─ db/
    │   └─ password          (SecureString) RDS master password
    ├─ keycloak/
    │   ├─ admin-password    (SecureString) Keycloak admin console password
    │   ├─ oauth-client-secret (SecureString) tasks-webapi OAuth2 client secret
    │   └─ smtp-password     (SecureString) Keycloak SMTP email password
    └─ app/
        ├─ jwt-issuer        (String) OAuth2 issuer URI
        └─ tenant-default-id (String) default tenant ID
```

> **スタック所有**: `/tasks/*` パラメータは **tasks stack**（`infra/environments/dev/`）が管理する（ADR-0004）。  
> `/platform/*` パラメータ（VPC / ALB 等の platform 出力値）は platform stack が管理し、tasks stack は `data "aws_ssm_parameter"` で参照のみ。

---

## 2. パラメータ一覧表

| パラメータパス | 型 | 用途 | アクセス許可者 | 回転ポリシー |
|---|---|---|---|---|
| `/tasks/<env>/db/password` | SecureString | RDS master password（DBA 業務用、アプリ非使用）| DBA（手動 ops）| 手動（四半期目安） |
| `/tasks/<env>/keycloak/admin-password` | SecureString | Keycloak admin console password | Keycloak 管理者（手動 ops）| 手動（四半期目安） |
| `/tasks/<env>/keycloak/oauth-client-secret` | SecureString | tasks-webapi realm の OAuth2 client secret | ECS Task Role（tasks-webapi） | Keycloak Realm 更新時に手動ローテーション |
| `/tasks/<env>/keycloak/smtp-password` | SecureString | Keycloak SMTP メール送信用（SES SMTP interface 経由、ADR-0006 派生）。IAM Access Key から生成 | Keycloak ECS Task Role（platform stack 管理） | IAM Access Key ローテーション時に再生成・更新 |
| `/tasks/<env>/app/jwt-issuer` | String | OAuth2 issuer URI（ECS Task Definition の `OIDC_ISSUER_URI` 注入元）| ECS Task Role（tasks-webapi）| Keycloak ホスト変更時に更新 |
| `/tasks/<env>/app/tenant-default-id` | String | デフォルト tenant ID | ECS Task Role（tasks-webapi）| テナント構成変更時に手動更新 |

### dev 環境の具体値

| パラメータパス | 値（dev） |
|---|---|
| `/tasks/dev/db/password` | 初回 apply 後に AWS SSM コンソールで設定 |
| `/tasks/dev/keycloak/admin-password` | 初回 apply 後に AWS SSM コンソールで設定 |
| `/tasks/dev/keycloak/oauth-client-secret` | 初回 apply 後に AWS SSM コンソールで設定 |
| `/tasks/dev/keycloak/smtp-password` | 初回 apply 後に AWS SSM コンソールで設定 |
| `/tasks/dev/app/jwt-issuer` | `https://auth-dev.dgz48.xyz/realms/tasks` |
| `/tasks/dev/app/tenant-default-id` | `1` |

---

## 3. アクセス IAM Policy 例

### 3.1 tasks-webapi ECS Task Role（読み取り専用）

tasks-webapi コンテナが ECS Task Definition の `secrets` セクション経由でパラメータを読むために必要な最小権限。

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "SsmReadTasksParams",
      "Effect": "Allow",
      "Action": [
        "ssm:GetParameter",
        "ssm:GetParameters",
        "ssm:GetParametersByPath"
      ],
      "Resource": [
        "arn:aws:ssm:<region>:<account_id>:parameter/tasks/<env>/app/*",
        "arn:aws:ssm:<region>:<account_id>:parameter/tasks/<env>/keycloak/oauth-client-secret"
      ]
    },
    {
      "Sid": "KmsDecrypt",
      "Effect": "Allow",
      "Action": "kms:Decrypt",
      "Resource": "arn:aws:kms:<region>:<account_id>:key/alias/aws/ssm"
    }
  ]
}
```

> **注意**: `db/password` は tasks-webapi が使用しない（IAM 認証を使用）ため含めない。

### 3.2 Keycloak ECS Task Role（platform stack 管理）

Keycloak コンテナが起動時に admin-password / oauth-client-secret / smtp-password を読むための権限（platform stack の Keycloak Task Role に付与）。

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "SsmReadKeycloakSecrets",
      "Effect": "Allow",
      "Action": [
        "ssm:GetParameter",
        "ssm:GetParameters"
      ],
      "Resource": [
        "arn:aws:ssm:<region>:<account_id>:parameter/tasks/<env>/keycloak/admin-password",
        "arn:aws:ssm:<region>:<account_id>:parameter/tasks/<env>/keycloak/oauth-client-secret",
        "arn:aws:ssm:<region>:<account_id>:parameter/tasks/<env>/keycloak/smtp-password"
      ]
    },
    {
      "Sid": "KmsDecrypt",
      "Effect": "Allow",
      "Action": "kms:Decrypt",
      "Resource": "arn:aws:kms:<region>:<account_id>:key/alias/aws/ssm"
    }
  ]
}
```

### 3.3 Terraform CI ロール（tasks-dev-plan / tasks-dev-apply）

`tasks-dev-plan` ロールの SSM Read ポリシーはすでに `/tasks/dev/*` を対象に設定済み（`infra/shared/modules/iam_oidc/main.tf`）。  
`tasks-dev-apply` ロールの SSM Write ポリシーも同様に `/tasks/dev/*` に対して設定済み。

---

## 4. 初期セットアップ手順

### 4.1 前提条件

- [ ] S0Infra-2 完了（S3 state backend）
- [ ] S0Infra-3 完了（GitHub Actions OIDC + IAM Role）

### 4.2 初回 apply 手順

```bash
cd infra/environments/dev

# 初期化（初回のみ）
terraform init

# 差分確認（CHANGE_ME プレースホルダで SecureString が created と表示される）
terraform plan

# パラメータ作成（CHANGE_ME プレースホルダ値で初期作成）
terraform apply
```

### 4.3 SecureString 実際値の設定（apply 後に手動実施）

`terraform apply` 後、以下のパラメータに実際の secret 値を AWS コンソールまたは CLI で設定する。  
`lifecycle.ignore_changes = [value]` により Terraform は以降の plan/apply でこれらの値を上書きしない。

```bash
# AWS CLI での設定例（各 secret ごとに実行）
aws ssm put-parameter \
  --name "/tasks/dev/db/password" \
  --value "<実際のパスワード>" \
  --type SecureString \
  --overwrite

aws ssm put-parameter \
  --name "/tasks/dev/keycloak/admin-password" \
  --value "<実際のパスワード>" \
  --type SecureString \
  --overwrite

aws ssm put-parameter \
  --name "/tasks/dev/keycloak/oauth-client-secret" \
  --value "<Keycloak Realm の client secret>" \
  --type SecureString \
  --overwrite

aws ssm put-parameter \
  --name "/tasks/dev/keycloak/smtp-password" \
  --value "<SES SMTP パスワード>" \
  --type SecureString \
  --overwrite
```

> **SES SMTP パスワードの生成**: IAM Access Key から SES SMTP クレデンシャルを生成する。  
> 詳細: [AWS ドキュメント — SES SMTP credentials](https://docs.aws.amazon.com/ses/latest/dg/smtp-credentials.html)

### 4.4 設定確認

```bash
# パラメータ一覧確認
aws ssm get-parameters-by-path \
  --path "/tasks/dev" \
  --recursive \
  --with-decryption \
  --query "Parameters[*].{Name:Name,Type:Type}" \
  --output table

# 個別値確認（SecureString）
aws ssm get-parameter \
  --name "/tasks/dev/app/jwt-issuer" \
  --output text \
  --query "Parameter.Value"
```

---

## 5. 回転ポリシー

| 対象パラメータ | 回転方式 | タイミング |
|---|---|---|
| `db/password` | 手動 | 四半期ごと、またはインシデント時 |
| `keycloak/admin-password` | 手動 | 四半期ごと、またはインシデント時 |
| `keycloak/oauth-client-secret` | 手動 | Keycloak Realm 再設定時、またはインシデント時 |
| `keycloak/smtp-password` | 手動（IAM Access Key 連動） | IAM Access Key ローテーション時に SES SMTP パスワードを再生成・更新 |
| `app/jwt-issuer` | Terraform 管理（String） | Keycloak ホスト・Realm 変更時に `terraform apply` |
| `app/tenant-default-id` | Terraform 管理（String） | テナント構成変更時に `terraform apply` |

> AWS Secrets Manager の自動ローテーションは **不使用**（確定前提 #9: コスト面で Parameter Store SecureString を採用）。

---

## 6. 将来追加予定パラメータ

以下は本 Issue のスコープ外。Sprint 1 以降で追加する際に本 doc に追記する。

| パラメータパス（予定） | 型 | 用途 | 追加タイミング |
|---|---|---|---|
| `/tasks/<env>/db/keycloak-spi-read-password` | SecureString | Keycloak SPI federation 用 read-only DB user（`infrastructure-plan.md` §3.6） | S1Infra-1（RDS 構築時） |

> `db/password`（RDS master）は S1Infra-1 で IAM 認証移行後、apps からは参照されなくなるが DBA 業務用として保持する。
