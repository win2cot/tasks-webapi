# ADR-0001: Private DNS for RDS — Route53 Private Hosted Zone による RDS エンドポイント固定化

- **Status**: Accepted
- **Date**: 2026-05-24
- **Deciders**: win2cot (Masayuki Ishikawa)
- **Tags**: infra, network, dns, rds, terraform

## 目次

- [1. コンテキスト(Context)](#1-コンテキストcontext)
- [2. 検討した選択肢(Options Considered)](#2-検討した選択肢options-considered)
- [3. 決定(Decision)](#3-決定decision)
- [4. 理由(Rationale)](#4-理由rationale)
- [5. 影響(Consequences)](#5-影響consequences)
- [6. 実装メモ(Implementation Notes)](#6-実装メモimplementation-notes)
- [7. 参考リンク(References)](#7-参考リンクreferences)

## 1. コンテキスト(Context)

### 背景

`tasks-webapi` は dev / stg / prd の 3 環境を想定したマルチ env 構成を採る。各 env は独立した VPC に ECS Fargate + RDS MySQL 8.4 を配置し、コンテナイメージは全環境共通（確定前提 #6）である。

RDS のエンドポイントは AWS が自動生成する DNS 名（例: `tasks-dev.cluster-xxxxx.ap-northeast-1.rds.amazonaws.com`）であり、次の課題がある。

1. **env 依存 ENV**: RDS エンドポイントが env ごとに異なるため、`DATASOURCE_URL` を env ごとに書き替える必要があり、コンテナイメージ共通化の恩恵が薄れる。
2. **Aurora 移行 / エンジン切替時の影響**: エンジンやインスタンス名を変えると DNS 名が変わり、ECS Task Definition の env 変数変更と再デプロイが必要になる。
3. **インスタンスリプレース / 昇格時の影響**: Aurora クラスタの Writer 昇格や RDS リネームで DNS 名が変わる可能性がある。

### 要求

- アプリケーション側 ENV（`DATASOURCE_URL`）を全 env 共通にしたい。
- Aurora 移行・エンジン切替時にアプリ ENV を変えずに済む抽象化層がほしい。
- env 間の DNS 漏洩リスクをゼロにしたい。

### 参照ドキュメント

- `docs/architecture/infrastructure-plan.md` v5 §3.5.1（本 ADR の決定内容の SSOT）
- Sprint 0 Infra S0Infra-8: Route53 PHZ 構築 Terraform
- Sprint 1 Infra S1Infra-1: RDS CNAME 登録 Terraform

---

## 2. 検討した選択肢(Options Considered)

### 選択肢 (α): env 毎独立 PHZ + 同名 record（**採用案**）

各 env の VPC に **同名の Private Hosted Zone `tasks.internal`** を独立して作成し、VPC association で当該 VPC からのみ参照可能にする。各 PHZ 内に `db.tasks.internal` の CNAME を登録し、その参照先を当該 env の RDS エンドポイントにする。

```
[dev VPC]
tasks-webapi container
  └─ env DATASOURCE_URL=jdbc:mysql://db.tasks.internal:3306/tasks
        │
        ▼ VPC DNS resolver
    Route53 PHZ "tasks.internal"（dev VPC association）
        │ CNAME db.tasks.internal → tasks-dev.cluster-xxx.ap-northeast-1.rds.amazonaws.com
        ▼
    dev RDS MySQL 8.4（IAM 認証）

[stg / prd VPC（将来）]
同様に PHZ "tasks.internal" が独立して作成、
db.tasks.internal の参照先は当該 env の RDS エンドポイント
```

- **利点**:
  - `DATASOURCE_URL=jdbc:mysql://db.tasks.internal:3306/tasks` が全 env で完全に同一になる
  - Aurora 移行 / エンジン切替 / インスタンスリネーム時は CNAME 先を書き替えるだけでアプリ ENV 不変
  - VPC 境界 = PHZ 境界のため、env 間 DNS 漏洩リスクがゼロ
  - 将来 ElastiCache 等を追加する場合も `cache.tasks.internal` のように同一 PHZ に record を追加するだけで ENV 名前空間が統一できる
- **欠点**:
  - DNS 解決層が 1 段増える（CNAME ホップ）→ トラブルシューティング時に `dig db.tasks.internal` 等の手順が増える
  - Route53 PHZ $0.50/月 × env 数 + クエリ料（ほぼ 0）のコストが発生する
  - Terraform で PHZ リソースと CNAME record を両方管理する必要がある
- **リスク・未知数**:
  - CNAME 経由の接続遅延は実測ではほぼ 0（VPC 内 DNS キャッシュが効く）
  - Aurora クラスタエンドポイントに CNAME を被せる場合、read replica endpoint 等は別途 record（`db-ro.tasks.internal` 等）が必要になる（MVP では writer のみで十分）

### 選択肢 (β): 単一 PHZ + env prefix（代替案）

1 つの PHZ `tasks.internal` を共用し、record 名に env prefix を付ける（例: `db-dev.tasks.internal` / `db-stg.tasks.internal`）。

- **利点**:
  - PHZ を 1 つだけ管理すればよい
- **欠点**:
  - `DATASOURCE_URL` が env ごとに異なる（`db-dev.tasks.internal` / `db-stg.tasks.internal`）→ コンテナイメージ共通化の恩恵がなくなる
  - PHZ を複数 VPC に association する必要があり、VPC 間 DNS 漏洩リスクが生じる（例: stg VPC が dev RDS endpoint を解決できてしまう可能性）
  - env prefix の命名規則管理が必要
- **リスク・未知数**:
  - VPC association の適切な制御を怠ると env 越境 DNS 解決が発生するセキュリティリスクがある

### 選択肢 (γ): Private DNS 不採用（Terraform output → ECS Task Definition env 直接注入）

Terraform が管理する RDS エンドポイントを `terraform output` で取得し、ECS Task Definition の environment に直接設定する。DNS 抽象化層を設けない。

- **利点**:
  - インフラのシンプルさ（Route53 PHZ リソース不要）
  - DNS 解決層がないためトラブルシューティングが直感的
  - コストゼロ（PHZ 費用が発生しない）
- **欠点**:
  - `DATASOURCE_URL` が env ごとに異なるため、ECS Task Definition を env ごとに変更する必要がある
  - Aurora 移行 / エンジン切替 / RDS インスタンスリネーム時に Terraform apply → ECS 再デプロイが必要
  - コンテナイメージの完全共通化（確定前提 #6）が崩れる（ECS Task Definition 差分として管理が必要）
- **リスク・未知数**:
  - Terraform state に RDS エンドポイントが平文で記録される（セキュリティ上は PHZ 経由と同等だが、明示的な抽象化がない）

---

## 3. 決定(Decision)

**採用: 選択肢 (α) — env 毎独立 PHZ + 同名 record**

各 env の VPC に **同名の Private Hosted Zone `tasks.internal`** を独立して作成し、`db.tasks.internal` の CNAME を当該 env の RDS エンドポイントに向ける。アプリケーション側の `DATASOURCE_URL` は `jdbc:mysql://db.tasks.internal:3306/tasks?useSSL=true&serverTimezone=UTC` で全 env 共通とする。

---

## 4. 理由(Rationale)

1. **コンテナイメージ完全共通化**: 確定前提 #6「コンテナイメージは全環境共通」を達成するためには `DATASOURCE_URL` が全 env で同一でなければならない。選択肢 (α) のみがこの要件を満たす。
2. **Aurora 移行時の透過性**: Phase 2 以降で RDS MySQL から Aurora MySQL / Aurora Serverless v2 への移行を計画している（ADR-0007 参照）。CNAME 先を書き替えるだけでアプリ側 ENV を変えずに移行できる点が大きなメリットである。
3. **env 間 DNS 漏洩リスクゼロ**: VPC ごとに PHZ を独立させることで、VPC 境界が DNS 境界と完全に一致し、env 越境 DNS 解決が構造的に不可能になる。
4. **コスト対効果**: PHZ $0.50/月 × env 数（最大 3 env で $1.50/月）は、運用工数削減・将来拡張性（`cache.tasks.internal` 等）を考慮すれば十分に許容範囲内である。
5. **将来拡張性**: `tasks.internal` ゾーン内に record を追加するだけで ElastiCache・内部 API 等も同一命名規則で吸収できる。reader endpoint も `db-ro.tasks.internal` として同一 PHZ に追加可能。

捨てた利点として、選択肢 (γ) のシンプルさ（PHZ 不要・コストゼロ）は魅力的だが、コンテナイメージ共通化と Aurora 移行透過性のトレードオフとしては受け入れられない。

---

## 5. 影響(Consequences)

### 良い影響(Positive)

- `DATASOURCE_URL=jdbc:mysql://db.tasks.internal:3306/tasks?useSSL=true&serverTimezone=UTC` が dev / stg / prd 全環境で完全に同一になる
- Aurora 移行 / エンジン切替 / インスタンスリネーム時にアプリ ENV 変更・コンテナ再ビルド不要
- VPC 境界 = PHZ 境界によって env 間 DNS 漏洩リスクがゼロになる
- `tasks.internal` ゾーンを将来の内部サービス（ElastiCache 等）の命名基盤として活用できる

### 悪い影響・制約(Negative)

- DNS 解決層が 1 段増えるため、接続トラブル時は `dig db.tasks.internal` で CNAME 解決先を確認する手順が必要
- Route53 PHZ $0.50/月 × env 数（dev + stg + prd = $1.50/月 + クエリ料ほぼ 0）のランニングコストが発生
- Terraform で `aws_route53_zone`（PHZ）と `aws_route53_record`（CNAME）の 2 リソースを env ごとに管理する必要がある
- Aurora クラスタの reader endpoint が必要になった場合は `db-ro.tasks.internal` を別途追加する必要がある（MVP では writer のみで十分）

### 既存ドキュメント・規約への波及

- `docs/architecture/infrastructure-plan.md` §3.5.1 が本 ADR の決定内容を SSOT として記載済みであり、追加変更は不要
- ECS Task Definition テンプレート（`infra/` 配下の Terraform）に `DATASOURCE_URL=jdbc:mysql://db.tasks.internal:3306/tasks?useSSL=true&serverTimezone=UTC` を設定する（Sprint 0 Infra S0Infra-8 / Sprint 1 Infra S1Infra-1 で実施）

---

## 6. 実装メモ(Implementation Notes)

### Sprint 0 Infra S0Infra-8: Route53 Private Hosted Zone 構築

```hcl
resource "aws_route53_zone" "tasks_internal" {
  name    = "tasks.internal"
  comment = "Private hosted zone for ${var.env} env internal services"

  vpc {
    vpc_id = aws_vpc.main.id
  }

  tags = {
    Env = var.env
  }
}
```

- `aws_route53_zone` の `vpc` ブロックで当該 env の VPC のみに association させる
- この時点では RDS がまだ存在しないため、CNAME record は placeholder（後続 Sprint で追加）

### Sprint 1 Infra S1Infra-1: RDS CNAME 登録

```hcl
resource "aws_route53_record" "db" {
  zone_id = aws_route53_zone.tasks_internal.zone_id
  name    = "db.tasks.internal"
  type    = "CNAME"
  ttl     = 300
  records = [aws_db_instance.main.address]
}
```

- `aws_db_instance.main.address` は RDS の DNS エンドポイント（ポート不含）
- Aurora に移行する場合は `aws_rds_cluster.main.endpoint` に差し替えるだけでアプリ ENV は不変

### ECS Task Definition

```json
{
  "name": "DATASOURCE_URL",
  "value": "jdbc:mysql://db.tasks.internal:3306/tasks?useSSL=true&serverTimezone=UTC"
}
```

全 env の Task Definition でこの値を共通使用する。

---

## 7. 参考リンク(References)

- `docs/architecture/infrastructure-plan.md` v5 §3.5.1（本 ADR の決定内容の SSOT）
- Sprint 0 Infra S0Infra-8: Route53 Private Hosted Zone(`tasks.internal`、env 毎独立) + dev VPC association
- Sprint 1 Infra S1Infra-1: RDS MySQL 8.4(dev) + PHZ CNAME(`db.tasks.internal` → dev RDS endpoint)登録
- [GitHub Issue #222](https://github.com/win2cot/tasks-webapi/issues/222): 本 ADR の起票 Issue
- [GitHub Issue #206](https://github.com/win2cot/tasks-webapi/issues/206): 親 tracker Issue
- `docs/adr/0007-rds-mysql-vs-aurora.md`: Aurora 移行に関する横断 ADR（将来の CNAME 差替え移行シナリオに関連）
- [AWS Route53 Private Hosted Zones](https://docs.aws.amazon.com/Route53/latest/DeveloperGuide/hosted-zones-private.html)
