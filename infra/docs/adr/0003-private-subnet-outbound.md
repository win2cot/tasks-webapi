# ADR-0003: Private Subnet outbound 経路 — dev は単一 NAT Gateway + S3 Gateway Endpoint

- **Status**: Accepted
- **Date**: 2026-06-03
- **Deciders**: win2cot (Masayuki Ishikawa)
- **Tags**: infra, network, nat-gateway, vpc-endpoint, terraform

> **2026-06-03 注記(ADR-0004)**: dev 兼用化に伴い、本 ADR の NAT Gateway + EIP + S3 Gateway Endpoint は共有 stack の `platform/network` module が所有する(`infra/docs/adr/0004-platform-project-infra-separation.md`)。outbound 経路選定の技術決定は不変。

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

dev 環境の Private Subnet ワークロード(ECS Fargate 上の tasks-webapi / Keycloak Custom)から AWS マネージドサービスおよび外部ネットワークへの outbound 経路を確定する。本 ADR は S0Infra-4(VPC + Route Table)の Private Subnet default route を確定するための前提(ブロッカー)である。

前提として次が確定している(`docs/architecture/infrastructure-plan.md` v5 §2 / §3.1)。

- MVP リリース時の稼働環境は **local + gha + dev のみ**。stg / prd は Post-Sprint-0 に延期。
- 実行基盤は **ECS on Fargate**(EC2 不使用)。
- dev は部分稼働(平日 19:00-02:00 + 土日 10:00-02:00、ECS の起動 / 停止で切替)。NAT Gateway / VPC Endpoint は稼働状態に関わらず時間課金される。
- 運用は win2cot 単独、コスト感度が高い。

### MVP で必要な outbound 用途

dev の ECS タスクが外向きに必要とする通信は、ほぼすべて AWS マネージドサービス(同一リージョン)宛である。

- ECR からのイメージ pull — `ecr.api` / `ecr.dkr`(API)+ レイヤ実体は S3
- CloudWatch Logs へのログ送信 — `logs`
- Parameter Store からの secrets 注入 — `ssm`
- SecureString の復号 — `kms`
- Keycloak のメール送信 — SES SMTP(`email-smtp`)

Route53 Private Hosted Zone(`tasks.internal`、ADR-0001)の名前解決は VPC リゾルバ経由で endpoint 不要。RDS IAM 認証トークンは SDK のローカル署名で生成され、接続時の外部 API 呼び出しは不要。

### Phase 2 で必要になりうる用途

外部 IdP federation(Keycloak → 外部 OIDC / SAML)、外部 Webhook 通知、外部 API 連携。これらは **AWS 外**であり VPC Endpoint では到達できず、NAT 系の汎用 egress が必須になる。

### 参照ドキュメント

- `docs/architecture/infrastructure-plan.md` v5 §3.1 / §3.5 / §6.1
- `infra/docs/adr/0001-private-dns-for-rds.md`(Route53 PHZ)/ `infra/docs/adr/0002-terraform-project-structure.md`(network module)
- S0Infra-4(#240): VPC + Subnets + Route Tables(本 ADR が前提)

---

## 2. 検討した選択肢(Options Considered)

価格は ap-northeast-1(東京)の実値。NAT Gateway = $0.062/時(≈ $45/月)+ データ処理 $0.062/GB。Interface Endpoint = 約 $7.3/月/AZ + データ処理 約 $0.01/GB。S3 Gateway Endpoint = 無料。

### 選択肢 (a): 単一 NAT Gateway + S3 Gateway Endpoint(採用案)

Public Subnet に NAT Gateway を 1 つ(single-AZ)置き、Private Subnet の default route(`0.0.0.0/0`)を向ける。S3 向けは無料の Gateway Endpoint で NAT を迂回する。

- **利点**:
  - すべての outbound(AWS マネージド + 外部)を 1 リソースでカバー、Phase 2 の外部用途も最初から対応
  - S3 Gateway Endpoint(無料)が ECR レイヤ pull と一般 S3 を NAT から迂回させ、データ処理費の主因を消す
  - 構成がシンプルで単独運用の保守負荷が最小
- **コスト**: NAT ≈ $45/月 + データ処理 $0.062/GB(主因は S3 GW 迂回で僅少)、S3 GW 無料
- **欠点**:
  - single-AZ のため当該 AZ 障害で Private egress が断たれる(dev は非 HA 許容)
  - AWS API 呼び出しもインターネット往復になる
  - NAT データ処理 $0.062/GB は Interface Endpoint($0.01/GB)より割高
- **リスク・未知数**:
  - 同一宛先 IP への 55,000 同時接続上限(dev では非該当)

### 選択肢 (b): VPC Interface Endpoints のみ(NAT なし)

各 AWS サービスに Interface Endpoint、S3 は Gateway Endpoint。インターネット egress を一切持たない。

- **利点**:
  - 最高のセキュリティ(通信が AWS バックボーン内で完結、インターネット非経由)
  - データ処理 $0.01/GB と安く、レイテンシも僅少
- **欠点**:
  - MVP で必要な約 7 種(`ecr.api` / `ecr.dkr` / `logs` / `ssm` / `kms` / `email-smtp` / `sts`)で single-AZ ≈ $51/月、2-AZ ≈ $102/月 と NAT より高い
  - 外部(Phase 2 の federation / webhook)に到達できず、結局 NAT を追加する手戻りが発生
  - 新規 AWS サービス利用のたびに Endpoint 追加が必要
- **リスク・未知数**:
  - Phase 2 で NAT 追加の手戻りコスト

### 選択肢 (c): S3 Gateway Endpoint のみ

S3 以外に到達できず単独では成立しない。本 ADR では (a) の補助として常時併用する位置づけ。

### 選択肢 (d): ハイブリッド(NAT + 高頻度サービスの Interface Endpoint)

NAT Gateway に加え、ECR / CloudWatch Logs など高頻度サービスだけ Interface Endpoint 化してデータ処理費を圧縮する。

- **利点**:
  - 大容量・定常トラフィック時に per-GB を削減しつつ外部到達も維持
- **欠点**:
  - dev の低トラフィックでは Endpoint の固定費($7.3/月/AZ)が NAT データ処理費の削減分を上回り、オーバーエンジニアリングになる
  - 構成が複雑化する
- **リスク・未知数**:
  - prd の定常トラフィック量が読めるまで損益分岐が確定しない

### 選択肢 (e): NAT instance(fck-nat 等の EC2)

EC2 上で NAT を自前運用する。最安(t4g.nano で ≈ $3/月)。

- **利点**:
  - コスト最小
- **欠点**:
  - 確定前提の **EC2 不使用原則**(ECS on Fargate)に反する
  - 単独運用でパッチ適用・監視・単一障害点のお守りが増える
- **リスク・未知数**:
  - 障害時の手動復旧が必要

---

## 3. 決定(Decision)

**採用**: 選択肢 (a) — dev = 単一 NAT Gateway(single-AZ)+ S3 Gateway Endpoint(無料)。

Private Subnet の default route(`0.0.0.0/0`)を NAT Gateway に向け、S3 向けトラフィックは Gateway Endpoint で NAT を迂回させる。Interface Endpoint(選択肢 d)は **prd 構築時(Post-Sprint-0)に定常トラフィック量 × セキュリティ要件で再評価**する。NAT instance(選択肢 e)は EC2 不使用原則により不採用とする。

---

## 4. 理由(Rationale)

1. **コスト(a vs b)**: dev の低トラフィックでは単一 NAT(≈ $45/月 + 僅少なデータ処理)が、MVP 用途を賄うのに必要な約 7 個の Interface Endpoint(≈ $51〜102/月)より安い。S3 Gateway Endpoint(無料)が最大のデータ主因(ECR レイヤ pull)を NAT から迂回させ、データ処理費を僅少に抑える。
2. **将来透過性**: NAT は Phase 2 の外部 outbound(federation / webhook / 外部 API)を最初からカバーし、endpoint-only 構成で生じる NAT 追加の手戻りを回避する。
3. **シンプルさ**: 1 リソースで全用途をカバーでき、単独運用の保守負荷が最小。
4. **EC2 不使用原則**: NAT instance を退け、Fargate 一貫の構成を維持する。
5. **ユーザ体感への無影響**: NAT は outbound 経路にのみ存在し、エンドユーザのリクエスト経路(Internet → ALB → ECS)は NAT を通らないため、レスポンスのレイテンシ / スループットに影響しない。NAT Gateway 自体も 5 Gbps〜100 Gbps に自動スケールし、dev 規模ではボトルネックにならない。

捨てた利点として、選択肢 (b) の最高セキュリティと安い per-GB は魅力だが、dev では固定費の高さ・外部不達・Phase 2 の手戻りがそれを上回る。prd で再評価する。

---

## 5. 影響(Consequences)

### 良い影響(Positive)

- すべての outbound を最小構成でカバーし、Phase 2 の外部用途にも即対応できる
- S3 Gateway Endpoint(無料)が ECR レイヤ pull / S3 を NAT から迂回させ、NAT データ処理費を圧縮する
- ユーザのリクエスト経路は NAT を通らないため、体感レイテンシ / スループットに影響しない

### 悪い影響・制約(Negative)

- single-AZ NAT のため、当該 AZ 障害時に Private Subnet の egress が断たれる(dev は非 HA 許容、prd で multi-AZ を再評価)
- NAT を置かない AZ の Private Subnet からの outbound は cross-AZ データ転送 $0.01/GB が加わる(dev 規模では僅少)
- NAT データ処理 $0.062/GB は Interface Endpoint の $0.01/GB より割高(ただし dev は低トラフィック + S3 GW 迂回で僅少)
- AWS API 呼び出しがインターネット往復になる(セキュリティ・レイテンシとも dev は許容、prd で Interface Endpoint を再評価)
- 同一宛先 IP への 55,000 同時接続上限(dev では非該当だが、将来単一外部 API を高頻度に叩く場合は注意)

### 既存ドキュメント・規約への波及

- S0Infra-4(#240)は本 ADR 確定後に、Private Subnet の Route Table へ default route(`0.0.0.0/0` → NAT Gateway)と S3 Gateway Endpoint の関連付けを追加する
- `network` module(ADR-0002 §3.D)が NAT Gateway + EIP + S3 Gateway Endpoint を内包する
- `infrastructure-plan.md` v5 §3.1 の dev 構成図に NAT Gateway + S3 Gateway Endpoint を明記するのが望ましい(軽微な doc 同期、別途実施可)

---

## 6. 実装メモ(Implementation Notes)

### outbound 用途と経路の対応

| 用途 | 経路 |
|---|---|
| ECR レイヤ(S3 実体) | S3 Gateway Endpoint(無料) |
| ECR API(`ecr.api` / `ecr.dkr`) | NAT Gateway |
| CloudWatch Logs | NAT Gateway |
| Parameter Store(`ssm`)/ KMS | NAT Gateway |
| SES SMTP(Keycloak メール) | NAT Gateway |
| 一般 S3 | S3 Gateway Endpoint(無料) |
| X-Ray OTLP(`xray.ap-northeast-1.amazonaws.com`) | NAT Gateway |
| 外部 IdP / Webhook / API(Phase 2) | NAT Gateway |

### Terraform 雛形(`network` module 内、例)

```hcl
resource "aws_eip" "nat" {
  domain = "vpc"
}

resource "aws_nat_gateway" "main" {
  allocation_id = aws_eip.nat.id
  subnet_id     = aws_subnet.public[0].id # single-AZ
}

# Private Subnet の default route を NAT へ
resource "aws_route" "private_default" {
  route_table_id         = aws_route_table.private.id
  destination_cidr_block = "0.0.0.0/0"
  nat_gateway_id         = aws_nat_gateway.main.id
}

# S3 は Gateway Endpoint で NAT を迂回(無料)
resource "aws_vpc_endpoint" "s3" {
  vpc_id            = aws_vpc.main.id
  service_name      = "com.amazonaws.${var.region}.s3"
  vpc_endpoint_type = "Gateway"
  route_table_ids   = [aws_route_table.private.id]
}
```

### prd 再評価の判定基準(Post-Sprint-0)

- 月間 NAT データ処理量が、対象サービスの Interface Endpoint 固定費を上回る水準に達したか
- インターネット egress を排除するセキュリティ要件の有無
- multi-AZ NAT による HA 要否

これらに応じて、選択肢 (d)(NAT + 高頻度サービスの Interface Endpoint)への移行を検討する。

---

## 7. 参考リンク(References)

- `docs/architecture/infrastructure-plan.md` v5 §3.1 / §3.5 / §6.1
- `infra/docs/adr/0001-private-dns-for-rds.md`(Route53 PHZ)
- `infra/docs/adr/0002-terraform-project-structure.md`(`network` module・module 粒度)
- [GitHub Issue #245](https://github.com/win2cot/tasks-webapi/issues/245): 本 ADR の起票 Issue(S0Infra-9)
- [GitHub Issue #240](https://github.com/win2cot/tasks-webapi/issues/240): S0Infra-4 VPC + Subnets + Route Tables
- [GitHub Issue #206](https://github.com/win2cot/tasks-webapi/issues/206): 親 tracker Issue
- [NAT gateway basics (AWS docs)](https://docs.aws.amazon.com/vpc/latest/userguide/nat-gateway-basics.html)
- [NAT Gateway pricing (AWS docs)](https://docs.aws.amazon.com/vpc/latest/userguide/nat-gateway-pricing.html)
- [AWS PrivateLink Pricing](https://aws.amazon.com/privatelink/pricing/)
