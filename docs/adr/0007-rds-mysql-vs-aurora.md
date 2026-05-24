# ADR-0007: dev 環境の DB エンジンは RDS MySQL 8.4 を採用、Aurora は Post-Sprint-0 で再評価

- **Status**: Accepted
- **Date**: 2026-05-23
- **Deciders**: 開発チーム(win2cot)
- **Tags**: database, infrastructure, aws, cost

## 目次

- [1. コンテキスト](#1-コンテキスト)
- [2. 検討した選択肢](#2-検討した選択肢)
  - [選択肢 A: RDS MySQL 8.4(Single AZ、db.t4g.micro)](#選択肢-a-rds-mysql-84single-azdbt4gmicro)
  - [選択肢 B: Aurora MySQL 互換(Serverless v2)](#選択肢-b-aurora-mysql-互換serverless-v2)
  - [選択肢 C: dev=RDS MySQL / prd=Aurora 混合案](#選択肢-c-devrds-mysql--prdaurora-混合案)
- [3. 決定](#3-決定)
- [4. 理由](#4-理由)
- [5. 影響](#5-影響)
  - [良い影響(Positive)](#良い影響positive)
  - [悪い影響・制約(Negative)](#悪い影響制約negative)
  - [既存ドキュメント・規約への波及](#既存ドキュメント規約への波及)
- [6. 実装メモ](#6-実装メモ)
  - [Post-Sprint-0 再評価条件](#post-sprint-0-再評価条件)
- [7. 参考リンク](#7-参考リンク)

## 1. コンテキスト

2026-05-23 の infrastructure-plan v5 策定プロセスで、MVP(Phase 1)における RDS エンジン選定を決定する必要が生じた。

本プロジェクトの制約・要件は次のとおり:

- **フェーズ**: Phase 1(dev 環境構築、副業規模の単人開発)
- **ワークロード**: 初期ユーザー数が少ない SaaS タスク管理、オンラインスケーリングは不要
- **コスト制約**: 副業コストとして AWS 月額費用を最小化する必要がある
- **互換性要件**: ローカル開発・CI で Testcontainers MySQL 8.4 を使用、エンジン固有の挙動差があると CI と本番の乖離リスクになる
- **認証方式**: tasks-webapi は IAM 認証(`AWSAuthenticationPlugin`)を採用(パスワード管理不要)
- **DNS 抽象化**: Route53 Private Hosted Zone `db.tasks.internal` で RDS エンドポイントを抽象化するため、将来のエンジン切替時もアプリ ENV は不変(§3.5.1)

選定の論点は Issue #221 の議論ポイント 1〜7 として整理した:

1. **コスト比較**: 月額 / インスタンスサイズ別 / Multi-AZ 影響
2. **可用性 / failover**: RDS Multi-AZ vs Aurora の failover 速度
3. **ローカル開発互換性**: testcontainers / ローカル MySQL との挙動差
4. **スケーラビリティ**: Aurora Serverless v2 の auto-scale が必要か
5. **Read Replica**: 必要数 / lag 要件
6. **Multi-Region DR**: Aurora Global Database の利点 vs MVP では不要か
7. **ベンダーロックイン**: AWS 独自機能依存度

## 2. 検討した選択肢

### 選択肢 A: RDS MySQL 8.4(Single AZ、db.t4g.micro)

- **概要**: AWS マネージド MySQL 8.4。インスタンスクラス `db.t4g.micro`、Single AZ 構成。IAM 認証有効化。
- **コスト(dev)**:
  - インスタンス: db.t4g.micro 約 $12〜15/月(ap-northeast-1)
  - ストレージ: gp3 20GiB 約 $2.3/月
  - **合計: 約 $14〜17/月**
  - Multi-AZ 構成にすると約 $28〜33/月(2 倍)
- **可用性 / failover**:
  - Single AZ: AZ 障害時はダウン。dev 環境では許容範囲。
  - Multi-AZ に昇格すれば自動フェイルオーバー 60〜120 秒。
- **ローカル開発互換性**: Testcontainers MySQL 8.4 と完全互換。挙動差なし。
- **スケーラビリティ**: 垂直スケール(インスタンスクラス変更)のみ。MVP では十分。
- **Read Replica**: 手動で追加可(MySQL Replication)。lag は数秒〜数十秒。
- **Multi-Region DR**: 対応外。prd でグローバル展開が必要になれば Aurora Global Database への移行を検討。
- **ベンダーロックイン**: MySQL 標準互換のため最小。
- **利点**: コスト最小、testcontainers 完全互換、運用シンプル、IAM 認証対応
- **欠点**: Aurora と比較して failover が遅い、auto-scale 不可、Read Replica 設定が手動
- **リスク・未知数**: prd 構築時にスケール要件が顕在化した場合、Aurora への移行コストが発生する

### 選択肢 B: Aurora MySQL 互換(Serverless v2)

- **概要**: Amazon Aurora MySQL 互換、Serverless v2(ACU ベースの auto-scale)。
- **コスト(dev)**:
  - Serverless v2 最小構成(0.5 ACU 常時): 約 $43/月(ap-northeast-1 概算)
  - 停止不可(最小 ACU を 0 にすると cold start が長い): コスト圧縮が難しい
  - **合計: 約 $43〜60/月**
- **可用性 / failover**:
  - Aurora クラスター標準: 自動フェイルオーバー 30 秒以内(Multi-AZ が前提)
  - Read Replica(Aurora Replica)はリアルタイム同期、lag は数ミリ秒〜秒
- **ローカル開発互換性**:
  - Aurora MySQL 8.0 互換は MySQL 8.0 の機能サブセット。MySQL 8.4 固有機能と差異がある可能性。
  - Testcontainers は `mysql:8.4` イメージを使用しており、Aurora 固有の挙動(e.g. `aurora_version()` 関数、一部の system variable)は再現不可。
- **スケーラビリティ**: ACU ベース auto-scale でピーク対応が容易。prd で真価を発揮。
- **Read Replica**: Aurora Replica として自動作成可、lag 最小。
- **Multi-Region**: Aurora Global Database でリージョン間レプリケーション可(RPO < 1 秒、RTO < 1 分)。
- **ベンダーロックイン**: Aurora 固有 API・設定値への依存度が高い。他クラウドへの移行コストが増大。
- **利点**: 高可用性、自動フェイルオーバー、auto-scale、Read Replica lag 最小、Multi-Region 対応
- **欠点**: コストが RDS の約 3 倍、testcontainers との完全互換性なし、dev 用途にオーバースペック
- **リスク・未知数**: Serverless v2 の ACU 課金は予測しにくく、コスト上振れリスクあり

### 選択肢 C: dev=RDS MySQL / prd=Aurora 混合案

- **概要**: 環境ごとにエンジンを使い分ける。
- **利点**: 各環境でコスト最適・性能最適を実現できる
- **欠点**:
  - dev と prd でエンジンが異なると、挙動差がバグを隠蔽するリスク
  - Terraform モジュール・設定の二重管理
  - 本番事故時に「dev で再現できない」ケースが生じうる
- **リスク・未知数**: エンジン差異が顕在化するのは高トラフィック時が多く、MVP では許容できるかもしれないが、設計一貫性の観点から複雑性が増す

## 3. 決定

**採用**: 選択肢 A — RDS MySQL 8.4(Single AZ、db.t4g.micro)

dev 環境のDBエンジンは **RDS MySQL 8.4 で確定** する。Aurora は prd 構築時(Post-Sprint-0)に改めて要件評価した上で採用するかどうかを決定する。

## 4. 理由

1. **コスト**: RDS MySQL 8.4(db.t4g.micro)は約 $14〜17/月。Aurora Serverless v2 は約 $43〜60/月で約 3 倍。副業規模の開発において dev 環境に $40+ を投じる根拠がない。
2. **testcontainers 完全互換**: CI・ローカル開発で使用する `mysql:8.4` イメージと RDS MySQL 8.4 は完全互換。Aurora との挙動差が CI / prd 乖離を生むリスクを排除できる。
3. **ベンダーロックイン最小化**: RDS MySQL は MySQL 標準 SQL 互換が高く、Aurora 固有 API への依存を避けられる。将来クラウドベンダーを変更する際のリスクが低い。
4. **シンプルな運用**: dev 環境は Single AZ で十分。フェイルオーバー速度(Aurora 30 秒 vs RDS Multi-AZ 60〜120 秒)は dev 環境の価値要件ではない。
5. **DNS 抽象化で移行コスト最小化**: Route53 Private Hosted Zone `db.tasks.internal` で RDS エンドポイントを抽象化しているため、将来 Aurora に移行してもアプリの ENV 変数は変更不要。Aurora 移行の技術的コストを下げた上での判断。
6. **スケーラビリティは prd 要件に応じて判断**: MVP フェーズではスケール要件が確定していない。Read Replica や Multi-Region DR の必要性が判明してから Aurora 採用を判断するほうが意思決定の確実性が高い。

選択肢 B を不採用とした理由: コストが不適切(約 3 倍)、testcontainers との完全互換性がない、dev 用途にオーバースペック。

選択肢 C を不採用とした理由: エンジン差異による dev/prd 挙動乖離リスクと Terraform 二重管理コストが、コスト最適化のメリットを上回ると判断。

## 5. 影響

### 良い影響(Positive)

- dev 環境の DB コストを月 $14〜17 に抑制(Aurora 比で約 $26〜43/月の削減)
- Testcontainers MySQL 8.4 との挙動差ゼロにより、CI と dev の信頼性が向上
- MySQL 標準互換により、ローカル開発者が MySQL クライアント / ツールをそのまま使用可能
- IAM 認証有効化によりパスワード管理が不要(tasks-webapi の接続はトークンのみ)
- Private DNS 抽象化と組み合わせることで、将来 Aurora への移行時にアプリコード・ENV は変更不要

### 悪い影響・制約(Negative)

- **failover 速度**: dev 環境は Single AZ のため AZ 障害時にダウン。prd で高可用性が必要なら Multi-AZ または Aurora への移行が必要。
- **auto-scale 不可**: RDS MySQL は垂直スケール(インスタンス変更)のみ。突発的な高トラフィックに即応できない。
- **Read Replica lag**: Aurora Replica(数ミリ秒)と比較して MySQL Replication は lag が数秒〜数十秒になる場合がある。Read-heavy なワークロードでは Aurora の優位性あり。
- **Multi-Region DR 非対応**: prd でグローバル展開が必要になれば Aurora Global Database への移行を検討する必要がある。
- **prd 移行コスト**: 将来 Aurora を採用する場合、インスタンスタイプ・パラメータグループ・バイナリログ設定などの再構成が必要。DNS 抽象化により接続先変更はゼロだが、Terraform コードの変更は発生する。

### 既存ドキュメント・規約への波及

- `docs/architecture/infrastructure-plan.md` §3.5 に本決定が反映済み(v5 策定時、2026-05-23)
- Phase 1 Sprint 1 Infra タスク S1Infra-1(RDS MySQL 8.4 構築)の前提条件として本 ADR を参照する

## 6. 実装メモ

Phase 1 Sprint 1 Infra(S1Infra-1)で Terraform により構築する内容:

- `aws_db_instance` リソース: engine=mysql 8.4、instance_class=db.t4g.micro、storage_type=gp3
- `iam_database_authentication_enabled = true`
- `multi_az = false`(dev は Single AZ)
- セキュリティグループ: SG-RDS(ECS からの 3306 のみ許可)
- Route53 PHZ CNAME: `db.tasks.internal` → RDS エンドポイント

IAM 認証の設定詳細は `docs/architecture/infrastructure-plan.md` §3.5 参照。

### Post-Sprint-0 再評価条件

prd 構築時(Post-Sprint-0)に以下の条件を評価し、Aurora 移行の採否を判断する:

| 条件 | 評価トリガー | Aurora 採用の判断基準 |
|---|---|---|
| **Read Replica 要件** | prd の DB CPU / IO が閾値を超過 | Read Replica lag < 100ms が必要、またはレプリカ 2 台以上が必要な場合 |
| **failover 速度要件** | SLA / RTO 要件が確定 | RTO < 60 秒が SLA 要件となる場合 |
| **Multi-Region DR 要件** | グローバル展開または DR 要件の発生 | RPO < 1 秒・RTO < 1 分が必要な場合 |
| **auto-scale 要件** | ピークと平常時の DB 負荷差が 5 倍以上 | インスタンス変更でのスケールが追いつかない場合 |
| **コスト逆転** | 読み取りワークロードが増加し Read Replica が常時必要になる場合 | RDS Multi-AZ + Read Replica のコストが Aurora を上回る場合 |

上記いずれかの条件が発生した場合に Aurora MySQL 互換への移行を ADR として記録し、Terraform を更新する。

## 7. 参考リンク

- `docs/architecture/infrastructure-plan.md` §3.5 — RDS 認証(K-A 案、MySQL 8.4)
- `docs/architecture/infrastructure-plan.md` §3.5.1 — Private DNS で RDS エンドポイント固定化
- `docs/architecture/infrastructure-plan.md` §5.3 — Phase 1 Setup 2 対象 ADR 一覧
- `infra/docs/adr/0001-private-dns-for-rds.md` — Route53 PHZ 採否 ADR(Setup2-3)
- Issue #221 — RDS MySQL vs Aurora 選定 ADR 起票
- Issue #206 — Phase 1 Setup 2 親 tracker
- [AWS Aurora MySQL vs RDS MySQL 公式比較](https://docs.aws.amazon.com/AmazonRDS/latest/AuroraUserGuide/Aurora.AuroraMySQL.Compare-80-v3.html)
- [Aurora Serverless v2 料金](https://aws.amazon.com/rds/aurora/pricing/)
- [RDS MySQL 料金(ap-northeast-1)](https://aws.amazon.com/rds/mysql/pricing/)
