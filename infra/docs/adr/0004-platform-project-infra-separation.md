# ADR-0004: 共有(platform)インフラと プロジェクト(tasks)インフラの分離 — stack/state 分割・命名空間・クロススタック参照

- **Status**: Accepted
- **Date**: 2026-06-03
- **Deciders**: win2cot (Masayuki Ishikawa)
- **Tags**: infra, terraform, structure, multi-project, state, ssm

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

dev 環境を tasks 専用ではなく**複数プロジェクト兼用**にする方針を 2026-06-03 の議論で決めた。固定費のかかる基盤レイヤーを共有してコストと運用を最適化する狙いである。

ADR-0002 は dev = tasks 単一 stack(`tasks/<env>/terraform.tfstate` の単一 state、`tasks-*` / `Project=tasks` 命名、monorepo `infra/`)を前提にしていた。兼用化に伴い、stack 分割・命名空間・クロススタック参照方式を本 ADR で確定し、ADR-0002 の該当箇所を改訂する。

前提として次が確定している。

- ADR-0002: env-per-directory / module-per-concern / version 固定 / `default_tags`。本 ADR はこれを引き継ぎ「stack 分割」を上乗せする。
- ADR-0003: dev = 単一 NAT Gateway + S3 Gateway Endpoint(技術決定は不変、所有が `network` module ごと platform に移るのみ)。
- ADR-0001: env 毎独立 PHZ `tasks.internal`。
- MVP 稼働は local + gha + dev のみ。運用は win2cot 単独。

### 仕分け結果(2026-06-03 議論で確定)

判定原則: **固定費がかかる or アカウント・ネットワーク単位のリソースは共有、従量(per-task)課金・テナントデータは専用。**

共有(platform stack):

- VPC / Subnet(public・private) / IGW / Route Table
- NAT Gateway + EIP + S3 Gateway Endpoint
- ALB + HTTPS Listener + SG-ALB + base wildcard 証明書(`*.dgz48.xyz`)
- SES(ドメイン identity / DKIM / Config Set)。ただし**送信認証情報(SMTP 用 IAM user + `smtp-password`)は revoke 単位を分けるためプロジェクト別に発行**し、tasks 側 `/tasks/*` Parameter Store に置く(ADR-0006(予定)派生、S0Infra-7)
- Route53 Public Hosted Zone `dgz48.xyz`(既存のアカウント共有)
- **Keycloak(共有 IdP runtime)**: ECS Service + Custom Image + Keycloak 専用 DB + auth ホストの listener rule(2026-06-04 追補)。realm を分ければ複数プロジェクトを 1 ランタイムで賄える。**`users` 表などの user データと realm 毎の SPI 設定は tasks 所有**で、SPI は profile を read-only federate(email のみ writable)。identity 所有モデルの詳細は ADR-0006(改訂)参照

専用(tasks stack):

- ECS Cluster / Service / Task Definition
- RDS(+停止スケジュール、§3.G)
- ECR / Frontend(S3 + CloudFront)
- `users` 表(user データの SoT)+ realm 毎の User Storage SPI 設定(Keycloak runtime は platform だが user データは tasks、ADR-0006 改訂)
- ACM 深い証明書(`*.tasks.dgz48.xyz`)/ listener rule / target group / Route53 alias レコード
- SG-ECS / SG-RDS / `/tasks/*` Parameter Store / PHZ `tasks.internal`

判断保留だった候補の結論:

- **ALB は共有**。リスナーの時間課金(固定費)を台数分削減できる。listener rule / target group / 深い証明書 / alias は専用で共有リスナーにぶら下げる。
- **ECS Cluster は専用**。Cluster / Service / Task Definition のオブジェクト自体は無料で、Fargate は per-task 課金。共有しても bin-packing による集約効果がなく、コストメリットが皆無のため専用に保つ。
- **RDS は専用**。課金の固定単位はインスタンスなので共有にコスト利はあるが、(1) IAM 認証がインスタンス属性、(2) `db.tasks.internal` PHZ 命名が tasks 固有、(3) dev≈prod 対称性(prod はテナントデータ分離で専用 RDS になる)の 3 点で結合が最も重い。さらに**共有インスタンスは相乗りする全プロジェクトが同時にアイドルのときしか停止できず**、停止スケジュール(S3Infra-4)による off 時間帯のコスト削減を取りこぼす。専用 + 停止のほうが安く、データ分離も保てる。
- **Keycloak は runtime を共有 / user データを専用に分割**(2026-06-04 追補、当初 §1 の専用仕分けを改訂)。Keycloak は常時稼働の IdP で、ECS Cluster と違い「realm 分割で複数プロジェクトを 1 ランタイムに集約」できるため共有の固定費削減メリットが実在する(認証は常時 up 必須で per-task 停止節約が効きにくいので、専用に保つ動機が ECS より弱い)。一方で user の SoT は tasks 専用 DB の `users` 表に残し、共有 Keycloak は **per-realm の User Storage SPI で profile を read-only federate(email のみ writable)** する。platform→tasks の結合は「検証済み email の書き戻し + profile read」に限定され、ADR-0004 の tasks 非依存(platform IaC は tasks state を参照しない。SPI の DB read creds / SG は tasks が払い出し SSM 公開)とも両立する。Custom Image は全プロジェクトの SPI を同梱する共有ビルド(プロジェクト追加時に再ビルド = 受容済みのリリース結合)。identity 所有モデル・SPI スコープの詳細は **ADR-0006(改訂)** が正本。

### 参照ドキュメント

- `infra/docs/adr/0002-terraform-project-structure.md`(本 ADR が改訂する上位 ADR)
- `infra/docs/adr/0003-private-subnet-outbound.md` / `infra/docs/adr/0001-private-dns-for-rds.md`
- `docs/architecture/infrastructure-plan.md` v5 §3
- 親 tracker [#206](https://github.com/win2cot/tasks-webapi/issues/206)、S0Infra-1〜9(#238-#246)

---

## 2. 検討した選択肢(Options Considered)

中心の分岐は (1) 共有インフラの置き場所、(2) クロススタック参照方式の 2 つ。仕分け(共有/専用の境界)は §1 で確定済み。

### 論点 1: 置き場所

#### (α) tasks-webapi/infra/shared に別 root + 別 state(採用案)

共有インフラを同一リポの `infra/shared/` に別 root module として置き、`tasks/dev` とは別 state を持つ。

- **利点**: 追加 repo / CI / OIDC を新設せず、既存 tracker / Project board / claude-automation をそのまま使える。state / 命名 / タグ / SSM パスを tasks 非依存にすれば、後の repo 抽出が機械的。
- **欠点**: 共有 TF が project repo に同居し、board に platform の issue が混在する。
- **リスク・未知数**: 2 つ目のプロジェクトが実体化したとき抽出の手間が発生するが、SSM 疎結合のため consumer 側は無変更で済む。

#### (β) 専用 repo `platform-infra`

共有インフラを独立リポに切り出す。

- **利点**: 所有が完全分離、他プロジェクトが tasks repo に依存しない。
- **欠点**: OIDC / CI / Project / claude-automation を一式新設する重さ。2nd プロジェクトが未実在の今は過剰(YAGNI)。

#### (γ) 現行の単一 state のまま

兼用が成立しない。不採用。

### 論点 2: クロススタック参照

#### (a) SSM Parameter Store publish(採用案)

platform stack が出力を `/platform/dev/<name>` に書き、projects は `aws_ssm_parameter` data source で read する。

- **利点**: 疎結合。共有 state への read 権限不要、出力スキーマへの依存も SSM パス名だけに局所化。
- **欠点**: publish するキーの命名規約を維持する必要がある。

#### (b) terraform_remote_state

各 project が共有 state バケットを直接 data source で読む。

- **欠点**: project ごとに共有 state バケットの read 権限 + 出力スキーマ知識が要り、マルチプロジェクトで権限と結合が増える。不採用。

#### (c) name / tag ベース data source

`aws_vpc` / `aws_subnets` を tag 一致で引く。state 非依存だが命名規約に脆く、タグ事故で誤参照のリスク。不採用。

---

## 3. 決定(Decision)

### A. stack 分割

dev を 2 stack に分割する: **platform stack**(共有)+ **tasks stack**(専用)。それぞれ独立した state を持つ。

### B. 置き場所: tasks-webapi/infra/shared(選択肢 α)

共有インフラは `infra/shared/environments/dev/` を root とし、`infra/shared/modules/` を呼ぶ。backend key = `platform/dev/terraform.tfstate`。専用 repo 化(β)は 2nd プロジェクト実体化時に再評価する。抽出容易性のため、命名・タグ・state key・SSM パス名はすべて tasks に依存させない。

### C. クロススタック参照: SSM Parameter Store(選択肢 a)

platform stack が出力を `/platform/dev/<name>` に publish する。tasks stack は `aws_ssm_parameter` data source で read する。`terraform_remote_state`(b)は密結合のため不採用。

### D. 命名・タグの分割

- 共有: リソース名 `platform-dev-<resource>`、タグ `Project=platform`
- 専用: 現行どおり `tasks-dev-<resource>`、タグ `Project=tasks`
- ADR-0002 §3.F の単一 `Project=tasks` 前提を上記に改訂。コスト集計は `Project` タグの 2 バケット(platform / tasks)で行う。

### E. module カタログの再配置

ADR-0002 §3.D の 11 module カタログを platform / tasks に振り分ける。

- **platform/modules**: `network`(VPC / Subnet / IGW / RT / NAT / EIP / S3 GW EP)/ `alb`(ALB / Listener / SG-ALB / base cert)/ `ses` / `keycloak`(共有 IdP runtime: ECS Service + Custom Image + Keycloak 専用 DB + auth listener rule、2026-06-04 追補、ADR-0006 改訂)
- **tasks/modules**: `security_group`(SG-ECS / SG-RDS)/ `route53`(PHZ + alias + 深い cert + listener rule + TG)/ `parameter_store` + Sprint 1 以降の `ecs_cluster` / `webapi_service` / `rds` / `frontend` / `ecr`(Keycloak runtime は platform へ移動。user データ `users` 表と per-realm SPI 設定のみ tasks 所有)

`network` / `alb` の所有が platform へ移る点が ADR-0002 からの主たる変更。

### F. apply 順序・state backend・OIDC

- 適用順: **platform → tasks**(tasks は SSM 経由で platform 出力に依存)。
- state backend(S0Infra-2): platform 用 key `platform/dev/...` と tasks 用 key `tasks/dev/...` の 2 本(S3 bucket / lock table は共用可、IAM はキー prefix で絞る)。
- OIDC IAM Role(S0Infra-3): platform apply 用と tasks apply 用を分離し、最小権限を別々に付与する。

### G. RDS は専用 + 停止スケジュール

RDS は tasks stack に置き、S3Infra-4(EventBridge Scheduler)の停止対象を「ECS のみ → ECS + RDS」に拡張する。`rds:StopDBInstance` / `rds:StartDBInstance` を追加し、起動は RDS → ECS、停止は ECS → RDS の順序制御を Lambda に持たせる。7 日自動再起動は日次運用のため非該当。

---

## 4. 理由(Rationale)

1. **兼用の前提(A / B)**: 共有レイヤーを別 stack/state に切ることで、他プロジェクトが tasks のリリースやコードに巻き込まれない。置き場所は YAGNI で当面同一リポに留め、SSM 疎結合により後の repo 抽出を低コストにする。
2. **疎結合(C)**: SSM 参照は共有 state の read 権限もスキーマ知識も不要で、project 追加時の権限設計が単純。`terraform_remote_state` の密結合を避ける。
3. **固定費のみ共有(§1 仕分け)**: VPC / NAT / ALB / SES のように時間課金 or アカウント単位のものだけ共有し、コスト効果を最大化する。
4. **ECS / RDS を専用に保つ(§1)**: ECS Cluster は共有してもコスト効果ゼロ、RDS は専用 + 停止のほうが安くデータ分離と dev≈prod 対称性も保てる。
5. **標準準拠**: Terraform 標準機能(別 root + S3 backend + SSM data source)のみで完結し、撤退 / 引継ぎコストが最小。

捨てた利点として、専用 repo(β)の完全な所有分離は魅力だが、2nd プロジェクト未実在の段階では新設コストが見合わない。

---

## 5. 影響(Consequences)

### 良い影響(Positive)

- 共有レイヤーが 1 度だけ構築され、将来のプロジェクトはネットワーク / ALB / SES を SSM 参照で再利用できる
- 固定費(NAT / ALB / VPC エンドポイント類)をプロジェクト横断で 1 本化し、dev のコストを抑えられる
- SSM 疎結合により、共有インフラの repo 抽出を consumer 無変更で行える

### 悪い影響・制約(Negative)

- platform stack の変更は全プロジェクトに影響する(blast radius が広い)。変更頻度は低く許容範囲だが、apply 時は注意が要る
- 適用順序の制約(platform → tasks)が増え、bootstrap 時の手順が一段増える
- 共有 TF が project repo に同居するため、board に platform issue が混じる(repo 抽出までの暫定)

### 既存ドキュメント・規約への波及

- **ADR-0002**: §3.C(単一 state → platform / tasks の 2 state)・§3.D(`network` / `alb` の所有が platform へ移動)・§3.F(命名・タグの platform / tasks 分割)を本 ADR で改訂。ADR-0002 にも本 ADR への cross-ref note を追加する。
- **ADR-0003**: NAT Gateway + EIP + S3 GW EP の所有が `platform/network` module に移る(技術決定は不変)。
- **ADR-0001**: PHZ `tasks.internal` は tasks 所有のまま、共有 VPC へ association(VPC ID は SSM 参照)。
- **infrastructure-plan.md** v5 §3.1 / §3.3 / §3.5 を platform / tasks 分割に同期する(別 PR フォローアップ)。
- **S0Infra 組み替え**: S0Infra-4(#240 VPC)・S0Infra-9(#245 NAT)・S0Infra-6(#242 ALB)→ platform 移設。S0Infra-5(#241 SG)→ SG-ALB は platform、SG-ECS / SG-RDS は tasks に分割。S0Infra-2(#239 state)・S0Infra-3(#246 OIDC)→ platform / tasks 分割。S0Infra-7(#243 PS)・S0Infra-8(#244 PHZ)→ SSM 参照に変更。platform 用の新規 issue(network / alb / ses / state / OIDC / SSM publish)を起票。**ブロッカー鎖が `platform(network + alb)→ tasks(SG-ECS / listener rule + TG / PHZ)` に変わる**ため Sprint 0 Infra の依存を再設定する。tasks 側の「共有 ALB へのぶら下げ(listener rule + TG + 深い cert + alias)」は新規 issue として切り出す。
- **S3Infra-4**(停止スケジュール)に RDS 停止 / 起動を追加(§3.G)。

---

## 6. 実装メモ(Implementation Notes)

### ディレクトリ構造(Sprint 0 時点)

```text
infra/
├─ shared/
│   ├─ environments/
│   │   └─ dev/            # backend key = platform/dev/terraform.tfstate
│   │       ├─ main.tf     # network / alb / ses 呼び出し + SSM 出力 publish
│   │       ├─ backend.tf
│   │       └─ versions.tf
│   └─ modules/
│       ├─ network/        # VPC / Subnet / IGW / RT / NAT / EIP / S3 GW EP
│       ├─ alb/            # ALB / HTTPS Listener / SG-ALB / base wildcard cert
│       └─ ses/            # domain identity / DKIM / Config Set
├─ environments/
│   └─ dev/                # backend key = tasks/dev/terraform.tfstate
│       # platform の値は aws_ssm_parameter data source で参照
├─ modules/
│   ├─ security_group/     # SG-ECS / SG-RDS
│   ├─ route53/            # PHZ tasks.internal / alias / 深い cert / listener rule / TG
│   └─ parameter_store/    # /tasks/* SecureString
└─ docs/
    └─ adr/
```

### SSM publish 一覧(platform → `/platform/dev/*`)

| キー | 値 |
|---|---|
| `/platform/dev/vpc-id` | VPC ID |
| `/platform/dev/vpc-cidr` | VPC CIDR |
| `/platform/dev/public-subnet-ids` | public subnet ID(カンマ区切り) |
| `/platform/dev/private-subnet-ids` | private subnet ID(カンマ区切り) |
| `/platform/dev/private-route-table-ids` | private RT ID |
| `/platform/dev/alb-arn` | ALB ARN |
| `/platform/dev/alb-https-listener-arn` | HTTPS Listener ARN |
| `/platform/dev/alb-sg-id` | SG-ALB の Security Group ID |
| `/platform/dev/alb-dns-name` | ALB DNS 名(alias レコード用) |
| `/platform/dev/alb-zone-id` | ALB の Hosted Zone ID(alias レコード用) |
| `/platform/dev/ses-config-set` | SES Config Set 名 |

### tasks 側の参照(例)

```hcl
data "aws_ssm_parameter" "vpc_id" {
  name = "/platform/dev/vpc-id"
}

data "aws_ssm_parameter" "alb_listener_arn" {
  name = "/platform/dev/alb-https-listener-arn"
}
```

### 共有 ALB へのぶら下げ

- 共有 Listener の default 証明書 = `*.dgz48.xyz`(platform、Route53 共有ゾーンで DNS 検証)。
- tasks は `*.tasks.dgz48.xyz` を `aws_lb_listener_certificate` で共有 Listener に SNI 追加。
- listener rule の priority はリスナー単位でユニークのため、**プロジェクトごとに priority レンジを割当**(例: tasks = 100-199)し衝突を防ぐ。
- SG-ECS は「共有 SG-ALB(`/platform/dev/alb-sg-id`)からの inbound 許可」を SSM 参照で設定する。

### repo 抽出時の手順(将来、2nd プロジェクト実体化時)

`infra/shared` を新リポへ移動(`git filter-repo` or 単純コピー)し、backend を新リポに repoint する。SSM パス名と命名が tasks 非依存のため、consumer(tasks)側の変更は不要。

---

## 7. 参考リンク(References)

- `infra/docs/adr/0002-terraform-project-structure.md`(本 ADR が改訂)
- `infra/docs/adr/0003-private-subnet-outbound.md`(NAT 所有が platform へ移動)
- `infra/docs/adr/0001-private-dns-for-rds.md`(PHZ は tasks 所有のまま共有 VPC へ association)
- `docs/architecture/infrastructure-plan.md` v5 §3.1 / §3.3 / §3.5
- [GitHub Issue #206](https://github.com/win2cot/tasks-webapi/issues/206): 親 tracker
- [Terraform: aws_ssm_parameter data source](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/data-sources/ssm_parameter)
- [Terraform: aws_lb_listener_certificate](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/lb_listener_certificate)
