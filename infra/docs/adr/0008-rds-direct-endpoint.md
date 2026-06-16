# ADR-0008: RDS エンドポイント直接注入 — PHZ/CNAME 廃止と RDS IAM 認証の整合

- **Status**: Accepted
- **Date**: 2026-06-16
- **Deciders**: win2cot (Masayuki Ishikawa)
- **Supersedes**: [ADR-0001](0001-private-dns-for-rds.md)
- **Tags**: infra, network, dns, rds, iam, terraform

## 目次

- [1. コンテキスト(Context)](#1-コンテキストcontext)
- [2. 決定(Decision)](#2-決定decision)
- [3. 理由(Rationale)](#3-理由rationale)
- [4. 影響(Consequences)](#4-影響consequences)
- [5. 参考リンク(References)](#5-参考リンクreferences)

## 1. コンテキスト(Context)

ADR-0001 では選択肢 (α)「env 毎独立 PHZ + 同名 CNAME」を採用し、`db.tasks.internal` CNAME を介してアプリから RDS に接続する方針を決定した。

その後、RDS IAM 認証（`useSSL=true` + IAM トークン生成）の実装を進める中で、技術的な障害が明らかになった。

### 問題

RDS IAM 認証では、JDBC ドライバが IAM トークン（SigV4 署名）を生成する際に **JDBC URL に含まれるホスト名をそのまま署名のホストに使用する**。

AWS ドキュメント（"Generating an authentication token"）の全サンプルコードは、RDS の実エンドポイント（`tasks-dev.xxxxxxxx.ap-northeast-1.rds.amazonaws.com` 形式）を直接使用しており、CNAME 経由での IAM トークン署名の動作は保証されていない。

具体的には:

- `db.tasks.internal`（CNAME）で署名した IAM トークンを RDS に送っても、RDS 側は実エンドポイントで検証するため、署名ホスト不一致により **認証が失敗するリスク**がある
- AWS 内部での CNAME 解決タイミングによっては動作するケースもあるが、**動作保証がない**ため本番環境での使用は不適切である

この問題は CNAME 抽象化層と RDS IAM 認証の根本的な非整合であり、ADR-0001 の決定時点では RDS IAM 認証を前提とした評価が行われていなかった。

### 実測による確認（dev 環境）

ECS タスク（task role = `tasks-dev-webapi-task-role`）で Python boto3 + pymysql を用いて 3 パターンを実測した結果:

| トークン生成時のホスト | 接続先ホスト | 結果 |
|---|---|---|
| `db.tasks.internal`（CNAME） | `db.tasks.internal` | **Access denied (1045)** |
| 実エンドポイント | 実エンドポイント | **接続成功** |
| `db.tasks.internal`（CNAME） | 実エンドポイント | **Access denied (1045)** |

接続先ホストは結果に影響しない。**トークン生成時のホスト名が実エンドポイントでなければ MySQL の `AWSAuthenticationPlugin` はトークンを拒否する**ことが実証された。

## 2. 決定(Decision)

**Route53 PHZ `tasks.internal` および CNAME `db.tasks.internal` を廃止し、ECS Task Definition に RDS 実エンドポイントを直接注入する。**

具体的には:

- `aws_route53_zone.tasks_internal` / `aws_route53_record.db` を Terraform から削除
- ECS Task Definition の `DATASOURCE_URL` に `module.rds.db_instance_address`（Terraform output）を直接設定
- env ごとの JDBC URL 差分は Terraform variables / tfvars で管理

## 3. 理由(Rationale)

### RDS IAM 認証の動作保証優先

RDS IAM 認証は SaaS の認証セキュリティ要件（ADR-0005 相当）として採用済みの前提条件である。動作保証のない CNAME 経由より、実エンドポイントを直接使う確実な実装を優先する。

### ADR-0001 の採用理由との再評価

ADR-0001 が PHZ を採用した主要理由:

1. コンテナイメージ完全共通化（確定前提 #6）
2. Aurora 移行時の透過性

これらを再評価する:

1. **コンテナイメージ共通化について**: ECS Task Definition はコンテナイメージとは独立して env ごとに管理される（Terraform module で env 変数として渡す設計）。JDBC URL が env ごとに異なっても、コンテナイメージ自体は共通のままである。ECS Task Definition の env 差分は Terraform で吸収するため、運用負荷の増加は限定的である。
2. **Aurora 移行透過性について**: Aurora 移行時は Terraform の `module.rds.db_instance_address` 参照先を変えるだけで ECS Task Definition が自動更新される。アプリケーションコードへの変更は不要であり、透過性は維持される。

### コスト・複雑性削減

PHZ 廃止により:

- Route53 PHZ $0.50/月 × env 数（最大 $1.50/月）が削減される
- Terraform リソース数が減り、infra の複雑性が低下する
- DNS 解決層がなくなり、接続トラブル時のデバッグが直接的になる

## 4. 影響(Consequences)

### 良い影響(Positive)

- RDS IAM 認証の動作が AWS ドキュメントの仕様と完全に一致する
- Route53 PHZ コスト（$0.50/月 × env 数）が削減される
- Terraform リソース数が減り、infra がシンプルになる
- DNS 解決層がなくなりトラブルシューティングが容易になる

### 悪い影響・制約(Negative)

- ECS Task Definition の `DATASOURCE_URL` が env ごとに異なる値になる（Terraform の `var.db_endpoint` で管理）
- Aurora 移行・エンジン切替時は Terraform apply が必要（ただし `module.rds.db_instance_address` を参照しているため自動反映される）
- stg / prd 環境を追加する際は env ごとの tfvars に RDS エンドポイントが含まれる形になる

### 既存ドキュメント・規約への波及

- ADR-0001 の Status を `Superseded by ADR-0008` に更新（同 PR で実施）
- `docs/architecture/infrastructure-plan.md` §3.5.1 が ADR-0001 の決定内容を SSOT として記載しているため、更新が必要（別 Issue で対応予定）

## 5. 参考リンク(References)

- [ADR-0001: Private DNS for RDS](0001-private-dns-for-rds.md) — 本 ADR が supersede する元の決定
- [PR #654](https://github.com/win2cot/tasks-webapi/pull/654): 本 ADR に対応する実装 PR（PHZ 廃止 + RDS endpoint 直接注入）
- [AWS Docs: Generating an IAM DB auth token](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/UsingWithRDS.IAMDBAuth.Connecting.Java.html) — 実エンドポイントを使用したトークン生成の公式サンプル
