# Terraform 規約

タスク管理システム(tasks-webapi)

Version 1.0

2026-06-07

作成者: 開発チーム

## 改訂履歴

| 版数 | 改訂日 | 改訂内容 | 改訂者 |
|---|---|---|---|
| 1.0 | 2026-06-07 | 新規作成(Issue #456)。IAM ポリシー最小権限 3 ルール(R1〜R3)+ CI 機械検知 + 命名・タグ規約 pointer | 開発チーム |

## 目次

- [改訂履歴](#改訂履歴)
- [0. 本書の位置付け](#0-本書の位置付け)
- [1. IAM ポリシー最小権限](#1-iam-ポリシー最小権限)
  - [1.1 R1: 書込み系 wildcard の禁止](#11-r1-書込み系-wildcard-の禁止)
  - [1.2 R2: resources の ARN 絞り](#12-r2-resources-の-arn-絞り)
  - [1.3 R3: リソース実装と IAM 権限の同時追加](#13-r3-リソース実装と-iam-権限の同時追加)
  - [1.4 CI による機械検知](#14-ci-による機械検知)
- [2. 命名・タグ規約](#2-命名タグ規約)
- [3. 関連ドキュメント](#3-関連ドキュメント)

## 0. 本書の位置付け

`infra/` 配下の Terraform コード全体(platform / tasks 両 stack)に適用する恒常ルールを定める。アーキテクチャ上の意思決定(環境分離方式、module 粒度、outbound 経路等)は `infra/docs/adr/` の ADR が SSOT であり、本書は実装時に常時参照するルールのみを集約する。

アプリケーションコードの規約は [`./設計規約.md`](./設計規約.md) / [`./コーディング規約.md`](./コーディング規約.md) を参照。

## 1. IAM ポリシー最小権限

最小権限の原則(要件定義書 AC)を IaC レベルで担保するためのルール。CI/CD 用 plan / apply role(`infra/shared/modules/iam_oidc/`)、ECS Task Role 等、Terraform で管理するすべての IAM ポリシーに適用する。

### 1.1 R1: 書込み系 wildcard の禁止

action の wildcard は **原則禁止**(deny-by-default)とし、例外として **action 名が `Get` / `List` / `Describe` で始まる読取専用 action の wildcard のみ許容**する。書込み系 action は完全列挙する。

- 禁止: `"ec2:*"` などのサービス全体 wildcard
- 禁止: `"ec2:Create*"`、`"iam:Put*"`、`"rds:Delete*"` などの書込み系 prefix wildcard
- 禁止: 許容 3 種に該当しないその他すべての wildcard(例: `"kms:ReEncrypt*"`。読み書きの判定で迷う prefix は列挙に倒す)
- 許容: `"ec2:Describe*"`、`"iam:Get*"`、`"iam:List*"` など読取専用 prefix の wildcard のみ

読取専用 prefix を許容する理由: plan 用の read 権限を完全列挙すると、AWS provider 更新で新しい Describe 系 API が呼ばれるたびに AccessDenied で plan が壊れ、保守コストが過大になる。read は被害半径が限定的であるため、トレードオフとして明示的に許容する。

```hcl
# NG: サービス全体 wildcard
statement {
  sid       = "Ec2Write"
  actions   = ["ec2:*"]
  resources = ["*"]
}

# OK: 書込み系は完全列挙 + ARN 絞り(R2)
statement {
  sid     = "IamRoles"
  actions = ["iam:CreateRole", "iam:DeleteRole", "iam:TagRole", "iam:PutRolePolicy"]
  resources = ["arn:aws:iam::${var.account_id}:role/tasks-*"]
}

# OK: 読取専用 prefix wildcard は許容
# ec2:Describe* は resource-level permission 非対応のため Resource:"*"(R2)
statement {
  sid       = "Ec2Read"
  actions   = ["ec2:Describe*"]
  resources = ["*"]
}
```

### 1.2 R2: resources の ARN 絞り

`resources` は可能な限り ARN で絞る。名前 prefix によるパターン絞り(例: `arn:aws:iam::<account>:role/tasks-*`)も可。

resource-level permission に対応しない action のみ `"*"` を許し、**その旨を必ずコメントで明記する**。

```hcl
# ssm:DescribeParameters does not support resource-level permissions; must use Resource:"*"
statement {
  sid       = "SsmDescribe"
  actions   = ["ssm:DescribeParameters"]
  resources = ["*"]
}
```

resource-level permission の対応有無は [AWS サービス認証リファレンス](https://docs.aws.amazon.com/service-authorization/latest/reference/) の **Resource types** 列で確認する(コーディング規約 §18 レビューチェックリストの対応項目を参照)。

### 1.3 R3: リソース実装と IAM 権限の同時追加

Terraform でリソースを新規実装・変更・削除する PR では、その plan / apply / destroy に必要な最小 action を、対応する IAM ポリシーに**同 PR で**追加・削除する。

- plan role: 新リソースの read(`Describe*` / `Get*` / `List*` prefix で可)
- apply role: create / update / delete に必要な write action を列挙
- **未実装リソース向けの先行の広い許可(「future:」コメント付き wildcard 等)を置いてはならない**。権限はリソースの実装と同時にだけ増やす
- 列挙漏れによる AccessDenied は terraform-plan / terraform-apply CI で検出し、同 Issue 内で追補する

注意: CI ロール自身の IAM ポリシー変更は merge → apply 後に初めて有効になる(自己管理 IAM の循環依存)。列挙漏れがあった場合は追補 PR が必要になることを許容する。

**同一 PR で新リソースと IAM 権限を追加する場合の depends_on 必須ルール**

`module.iam_oidc` に write 権限を追加しつつ同一 PR で新リソースも追加する場合、そのリソースに `depends_on = [module.iam_oidc]` を付けること。`terraform apply` は IAM ポリシー更新と新リソース作成を並列実行するため、ポリシー反映前にリソース作成が走ると AccessDenied になる。

```hcl
# OK: iam_oidc に ecs:CreateCluster を追加した同 PR で ECS クラスタを追加する場合
resource "aws_ecs_cluster" "example" {
  name = "..."
  depends_on = [module.iam_oidc]
}

# OK: モジュール呼び出しでも同様
module "keycloak" {
  source     = "..."
  depends_on = [module.iam_oidc]
}
```

参考: `module.keycloak_db`(PR #455)・`aws_ecs_cluster.platform`/`module.keycloak`(PR #465)に同じ対策を適用済み。

### 1.4 CI による機械検知

R1 は `terraform-lint.yml` の wildcard gate で機械検知する(Issue #457)。`infra/**/*.tf` の IAM action のうち、wildcard を含み action 名が `Get` / `List` / `Describe` で始まらないものを検知して fail する(判定基準は R1 と同一)。

## 2. 命名・タグ規約

リソース命名・module 命名・必須タグ(`Project` / `Env` / `ManagedBy`)は [infra ADR-0004 §D](../../infra/docs/adr/0004-platform-project-infra-separation.md)(ADR-0002 §3.F を改訂) を SSOT とする。本書には転記しない。

## 3. 関連ドキュメント

- [`./設計規約.md`](./設計規約.md)
- [`./コーディング規約.md`](./コーディング規約.md)(§18 レビューチェックリスト: IAM resource スコープ確認)
- [`../../infra/docs/adr/`](../../infra/docs/adr/) — Terraform アーキテクチャ意思決定(ADR-0002: プロジェクト構造ほか)
- [`../architecture/infrastructure-plan.md`](../architecture/infrastructure-plan.md)
