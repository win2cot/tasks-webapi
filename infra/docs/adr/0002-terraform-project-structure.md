# ADR-0002: Terraform プロジェクト構造 — ディレクトリ分離・モジュール粒度・環境分離方式

- **Status**: Accepted
- **Date**: 2026-06-03
- **Deciders**: win2cot (Masayuki Ishikawa)
- **Tags**: infra, terraform, structure, iac, module

> **2026-06-03 改訂(ADR-0004)**: dev を複数プロジェクト兼用にする方針に伴い、本 ADR の §3.C(env あたり単一 state)・§3.D(`network` / `alb` module の所有)・§3.F(命名・タグの `Project=tasks` 単一前提)は `infra/docs/adr/0004-platform-project-infra-separation.md` で改訂された。env-per-directory / module-per-concern / version 固定 の決定は有効。

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

Phase 1 Sprint 0 〜 Sprint 5 で AWS リソース(VPC / RDS / ECS / ALB / Keycloak Custom / S3+CloudFront / Route53 + ACM 等)を段階的に Terraform で構築する。最初に構造を確定しないと、Sprint 1 以降の Issue が module 分割・環境差異・state 分離方式で迷走する。本 ADR は構造を確定し、後続 Infra Issue の判断基準を提供する。

前提として次が確定している(`docs/architecture/infrastructure-plan.md` v5 §2)。

- MVP リリース時の稼働環境は **local + gha + dev のみ**(確定前提 #1)。stg / prd は構築せず Post-Sprint-0 に延期する。
- コンテナイメージは全環境共通(確定前提 #6)。
- Terraform 実行は GitHub Actions OIDC 経由で、手元 apply しない(確定前提 #7)。
- `infra/` subdir 構造の 1 リポ monorepo(確定前提 #13 / #14)、IaC 固有 docs は `infra/docs/`(確定前提 #17)。
- 運用は win2cot 単独。

### 要求

- env 差を明示的に管理し、誤 apply 事故を構造的に防ぎたい。
- 単独運用・MVP 規模に見合うシンプルさ(過剰な抽象化を避ける)。
- 将来の stg / prd 追加を見越した拡張性。
- Sprint 単位で段階的にリソースを足せる module 構成。

### 参照ドキュメント

- `docs/architecture/infrastructure-plan.md` v5 §3.1 / §3.3 / §6.1(本 ADR の上位 SSOT)
- `infra/docs/adr/0001-private-dns-for-rds.md`(隣接 ADR、env 毎独立 PHZ)
- S0Infra-2(#239): state backend 構築 / S0Infra-9(#245): outbound 経路 ADR-0003

---

## 2. 検討した選択肢(Options Considered)

本 ADR の中心的な分岐は「環境分離の実装方式」である。その他の論点(state 粒度・module 粒度・version 固定・命名 / タグ)は §3 で個別決定として記録する。

### 選択肢 (α): ディレクトリ分離 — env ごとの root module(採用案)

各 env を `infra/environments/<env>/` の独立した root module とし、`infra/modules/` 配下の共通 module を呼び出す。env 差は tfvars と backend 設定で明示する。

- **利点**:
  - env ごとの backend / provider / tfvars が物理的に分離され、差分が一目で分かり誤 apply しにくい
  - Terraform 標準機能のみで完結、追加ツール不要
  - `infrastructure-plan.md` v5 §3.3 のディレクトリ図と整合
- **欠点**:
  - env 間で root module のボイラープレート(module 呼び出し)が重複する
- **リスク・未知数**:
  - 重複は env 数増加で保守負荷になるが、MVP は dev のみ・最大 3 env で許容範囲

### 選択肢 (β): Terraform workspaces

単一 root module + workspace で env を切り替える。state は workspace ごとに分離される。

- **利点**:
  - ディレクトリ重複がなくコード量が最小
- **欠点**:
  - backend / provider 設定が全 env 共有で env 差が暗黙的になる
  - `terraform workspace select` 忘れによる誤 apply の事故源
  - env 固有差(インスタンスサイズ等)が条件分岐だらけになりやすい
- **リスク・未知数**:
  - 単独運用で workspace 切替を見落とすと dev へ stg 設定を流す等の事故が起こりうる

### 選択肢 (γ): Terragrunt

Terragrunt で DRY に env を管理する。

- **利点**:
  - ボイラープレート削減、backend 自動生成、依存管理
- **欠点**:
  - サードパーティ依存と学習コスト、Terraform 単体にない概念の導入
  - CI(OIDC)に Terragrunt インストール手順を追加する必要がある
- **リスク・未知数**:
  - MVP・単独運用には過剰、撤退時の移行コストが発生する

---

## 3. 決定(Decision)

### A. 環境分離方式: ディレクトリ分離(選択肢 α)

`infra/environments/<env>/` を env ごとの root module とし、`infra/modules/` の共通 module を呼び出す。env 差は tfvars と backend 設定で明示する。

### B. Sprint 0 で実体化する環境: dev のみ

`infra/environments/dev/` のみ作成する。stg / prd は空ディレクトリを作らず、§6 に「dev を複製して tfvars と backend key を差し替える手順」を記録するに留める。実体化は Post-Sprint-0。

### C. state 分離: env あたり単一 state

env 毎に独立した state を持つ(layer 別分割はしない)。backend は S3 + DynamoDB(実装は S0Infra-2)、key 規約は `tasks/<env>/terraform.tfstate`。layer 分割は prd 構築時(Post-Sprint-0)に再評価する。

### D. module 粒度: per-concern、Sprint 0 は 5 module に限定

目標カタログ(将来含む): `network` / `security_group` / `alb` / `route53` / `parameter_store` / `ecs_cluster` / `webapi_service` / `keycloak_service` / `rds` / `frontend` / `ecr`。

1 module = 1 関心ごと(per-resource 化しない)。共通 module のネストは MVP では行わず flat 構成とする(議論ポイント4 の「ネスト可」は採用せず、必要が生じた時点で再評価)。Sprint 0 で実体化するのは S0Infra-4〜8 が触る `network` / `security_group` / `alb` / `route53` / `parameter_store` の 5 つのみ。残りは Sprint 1 以降の Infra Issue が必要時に追加する(空 module を先に作らない)。`network` module の outbound 経路(NAT GW vs VPC Endpoint)は ADR-0003(S0Infra-9)で別途確定する。

なお `security_group` は `infrastructure-plan.md` v5 §3.3 の module 一覧(10 個)には独立列挙されていないが、S0Infra-5 が独立 Issue であることを踏まえ本 ADR で独立 module(計 11 個)として切り出した。

### E. provider / version 固定

各 env root に `versions.tf` を置く。`required_version`(Terraform 本体)と `required_providers`(`hashicorp/aws ~> 5.x`)を悲観的制約 `~>` で固定し、`.terraform.lock.hcl` を env ごとにコミットする。module 側は `required_providers` で source 宣言のみ行い、version 制約は root に委譲する。具体バージョンは Sprint 0 着手時点の最新 stable で確定する。

### F. 命名・タグ規約

- リソース名: `tasks-<env>-<resource>`(例 `tasks-dev-vpc`)
- module 名: 単数・英小文字の per-concern(`network` / `alb` / `rds` …)
- 必須タグ: `Project=tasks` / `Env=<env>` / `ManagedBy=terraform` を provider の `default_tags` で一括付与
- 論理名前空間は `tasks` で統一する(リポジトリ名 `tasks-webapi` とは独立。リソース名プレフィックス・PHZ `tasks.internal`・ドメイン `tasks.dgz48.xyz` と整合)
- Owner / Sprint / コスト按分タグは単独運用のため付けない(`Component` タグは将来必要時に追加)

---

## 4. 理由(Rationale)

1. **誤 apply 防止(A / B)**: ディレクトリ分離は env 差を物理的に可視化し、workspace の暗黙切替による誤 apply 事故を構造的に排除する。
2. **シンプルさ優先(C / D)**: 単独運用・dev のみ・MVP 規模では、単一 state と最小 module が保守負荷を最小化する。blast radius は許容範囲で、必要になってから分割する。
3. **段階的拡張(D)**: Sprint Issue が触る分だけ module 化することで、空 module の塩漬けと先行設計の手戻りを避ける。
4. **将来透過性(B / E / F)**: stg / prd は dev 複製で追加でき、version は `~>` で安全に追従、`Project=tasks` で全コンポーネント横断のコスト集計が可能になる。
5. **標準準拠(α)**: Terraform 標準機能のみで完結し、撤退 / 引継ぎコストが最小。Terragrunt の DRY メリットは 3 env 程度では費用対効果が出ない。

捨てた利点として、workspaces のコード量最小・Terragrunt の DRY はいずれも魅力的だが、それぞれ誤操作リスク・依存コストとのトレードオフが MVP には見合わない。

---

## 5. 影響(Consequences)

### 良い影響(Positive)

- env 差が tfvars / backend に明示され、誤 apply 事故が構造的に起きにくい
- Sprint 単位で module を足せるため、Sprint 0 の作業範囲が 5 module に限定され見通しがよい
- `Project=tasks` タグで webapi / keycloak / web / infra 横断のコスト集計が可能

### 悪い影響・制約(Negative)

- env を増やすと root module のボイラープレートが env ごとに重複する(stg / prd 追加時にコピーが発生)
- 単一 state のため、1 env 内の apply は全リソースが blast radius に入る(MVP 規模では許容)
- module を後追いで足す運用のため、ADR のカタログと実体の差を Sprint ごとに意識する必要がある

### 既存ドキュメント・規約への波及

- `infrastructure-plan.md` v5 §3.3 のディレクトリ図と概ね整合。ただし §3.3 の module 一覧(10 個)は `security_group` を独立列挙していないため、本 ADR で追加した `security_group` を §3.3 にも追記するのが望ましい(軽微な doc 同期、別途実施可)
- `infrastructure-plan.md` v5 §3.3 の environments/ ディレクトリ図は stg/ / prd/ をエントリとして列挙しているが、本 ADR §3.B の「空ディレクトリを作らず」方針と相反する。§3.3 から stg/ / prd/ エントリを削除し、代わりに手順参照(§6 参照)を追記することが望ましい(軽微な doc 同期、別途実施可)
- S0Infra-2(state backend)は本 ADR の key 規約 `tasks/<env>/terraform.tfstate` を前提に実装する
- S0Infra-4〜8 は本 ADR の 5 module を作成し、S0Infra-9 / ADR-0003 が `network` の outbound 経路を確定する

---

## 6. 実装メモ(Implementation Notes)

### ディレクトリ構造(Sprint 0 時点)

```text
infra/
├─ environments/
│   └─ dev/
│       ├─ main.tf            # modules 呼び出し
│       ├─ variables.tf
│       ├─ versions.tf        # required_version + required_providers + default_tags
│       ├─ backend.tf         # S3 backend, key = tasks/dev/terraform.tfstate
│       ├─ terraform.tfvars
│       └─ .terraform.lock.hcl
├─ modules/
│   ├─ security_group/        # SG-ECS / SG-RDS(SG-ALB は platform/alb へ移動 — ADR-0004)
│   ├─ route53/               # PHZ tasks.internal(ADR-0001)
│   └─ parameter_store/       # SecureString
└─ docs/
    └─ adr/                   # 本 ADR 等
```

> **ADR-0004 改訂**: `network` / `alb` module は `infra/shared/modules/` へ移動済み。`infra/shared/` を含む最終ディレクトリ構造は `infra/docs/adr/0004-platform-project-infra-separation.md` §6 参照。

stg / prd 追加時(Post-Sprint-0): `cp -r environments/dev environments/stg` → backend key を `tasks/stg/terraform.tfstate` に、tfvars を stg 値に差し替える。

### versions.tf(env root、例)

```hcl
terraform {
  required_version = "~> 1.9"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

provider "aws" {
  region = var.region

  default_tags {
    tags = {
      Project   = "tasks"
      Env       = var.env
      ManagedBy = "terraform"
    }
  }
}
```

具体的な version 値は Sprint 0 着手時点の最新 stable で確定する。

### backend.tf(env root、例)

```hcl
terraform {
  backend "s3" {
    bucket         = "tasks-tfstate"   # 実名は S0Infra-2 で確定
    key            = "tasks/dev/terraform.tfstate"
    region         = "ap-northeast-1"
    dynamodb_table = "tasks-tflock"    # 実名は S0Infra-2 で確定
    encrypt        = true
  }
}
```

S3 bucket と DynamoDB table の実名は S0Infra-2 の bootstrap で確定する。Terraform 1.10+ では DynamoDB に代わる S3 ネイティブロック(`use_lockfile`)も選択肢になるが、採否は S0Infra-2 のスコープとする。

### module 呼び出し規約(main.tf、例)

```hcl
module "network" {
  source = "../../modules/network"
  env    = var.env
  # ...
}
```

module 側は `required_providers` で source のみ宣言し、version 制約は root に委譲する。

### Sprint 0 で作成しない module

`ecs_cluster` / `webapi_service` / `keycloak_service` / `rds` / `frontend` / `ecr` は Sprint 1 以降の Infra Issue で追加する。空ディレクトリは先に作らない。

---

## 7. 参考リンク(References)

- `docs/architecture/infrastructure-plan.md` v5 §3.1 / §3.3 / §6.1(上位 SSOT)
- `infra/docs/adr/0001-private-dns-for-rds.md`(隣接 ADR、env 毎独立 PHZ)
- `infra/docs/adr/0003-private-subnet-outbound.md`: Private Subnet outbound 経路選定(S0Infra-9 / #245)
- [GitHub Issue #238](https://github.com/win2cot/tasks-webapi/issues/238): 本 ADR の起票 Issue(S0Infra-1)
- [GitHub Issue #206](https://github.com/win2cot/tasks-webapi/issues/206): 親 tracker Issue
- [GitHub Issue #239](https://github.com/win2cot/tasks-webapi/issues/239): S0Infra-2 state backend
- [Terraform: Standard Module Structure](https://developer.hashicorp.com/terraform/language/modules/develop/structure)
- [Terraform: S3 Backend](https://developer.hashicorp.com/terraform/language/settings/backends/s3)
