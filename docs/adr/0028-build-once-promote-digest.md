# ADR-0028: コンテナイメージの build once / promote digest と Task Definition の責務分担

- **Status**: Accepted
- **Date**: 2026-06-12
- **Deciders**: win2cot, 開発チーム
- **Tags**: ci-cd, infra, release
- **Amends**: [ADR-0026](0026-component-scoped-deploy-tags.md)(§6 実装メモの ECR イメージ tag・stg/prd 昇格手順を修正・詳細化する。ADR-0026 §3 の決定(tag 形式・振り分け・infra 承認ゲート)は不変)

## 目次

- [1. コンテキスト(Context)](#1-コンテキストcontext)
- [2. 検討した選択肢(Options Considered)](#2-検討した選択肢options-considered)
  - [論点 1: イメージ build と昇格の方式](#論点-1-イメージ-build-と昇格の方式)
  - [論点 2: Task Definition の Terraform / CI 二重管理](#論点-2-task-definition-の-terraform--ci-二重管理)
- [3. 決定(Decision)](#3-決定decision)
- [4. 理由(Rationale)](#4-理由rationale)
- [5. 影響(Consequences)](#5-影響consequences)
  - [良い影響(Positive)](#良い影響positive)
  - [悪い影響・制約(Negative)](#悪い影響制約negative)
  - [既存ドキュメント・規約への波及](#既存ドキュメント規約への波及)
- [6. 実装メモ(Implementation Notes)](#6-実装メモimplementation-notes)
- [7. 参考リンク(References)](#7-参考リンクreferences)

## 1. コンテキスト(Context)

infrastructure-plan §1 確定前提 6 は「**コンテナイメージは全環境共通**、環境別ファイル同梱せず」
(環境差は env vars 注入、確定前提 5)である。一方 [ADR-0026](0026-component-scoped-deploy-tags.md)
はデプロイ tag を `<component>-vX.Y.Z[-dev|-stg]` と定め、環境 suffix 違いの tag を同一 commit に
追加付与して昇格する、とした。

ここで「環境 suffix tag = 再 build トリガ」と解釈して実装すると矛盾が生じる。同一 commit でも
`bootBuildImage` の再実行でイメージ digest は変わり得るため、`webapi-v1.2.0-stg` で再 build すると
**dev で検証した成果物と stg に配備される成果物が別物**になり、確定前提 6 の意図
(test what you ship)が崩れる。また ADR-0026 §6 は ECR イメージ tag を「git tag から prefix を
除いた `vX.Y.Z-dev` 形式のままでよい」としたが、イメージ識別子に環境が入ること自体が
確定前提 6 と不整合だった。

さらに、デプロイを「CI が Task Definition の新 revision を register して Service を更新する」方式に
すると、Terraform が管理する Task Definition と CI が作る revision の**二重管理**が生じる。既知の罠
として、Terraform 側で env vars 等を変更して apply した際、Terraform が知識として持つ古い image で
新 revision を作り、**稼働中イメージを巻き戻す**事故がある。

S2Infra-2 / 4 / 5 / 7(#479 / #481 / #482 / #484)の実装着手前に、build・昇格・Task Definition 管理の
セマンティクスを確定する。

## 2. 検討した選択肢(Options Considered)

### 論点 1: イメージ build と昇格の方式

#### 選択肢 A: 環境 suffix tag ごとに再 build

- 概要: `-dev` / `-stg` / suffix なしの各 tag push でそれぞれ build + push + deploy
- 利点: workflow が全環境で同一構造になり単純
- 欠点: 同一 commit でも環境ごとに digest が異なる成果物が配備され、確定前提 6 の意図が崩れる。
  build 時間(Native Image は数分〜十数分)が昇格のたびにかかる
- リスク・未知数: 「dev で検証した = prd で動く」が成立しない

#### 選択肢 B: build once / promote digest(dev tag = build + deploy 兼用)

- 概要: build は version につき 1 回だけ(`<component>-vX.Y.Z-dev` tag push 時)。ECR イメージ tag は
  環境を含まない `vX.Y.Z`。`-stg` / suffix なし tag push は再 build せず、ECR 上の `vX.Y.Z` の digest を
  解決して対象環境の ECS を更新する(promote)。workflow 内部は build job と deploy job に分離し、
  deploy job を reusable にして昇格 workflow から再利用する
- 利点: 全環境で同一 digest が配備され確定前提 6 と完全整合。昇格は秒単位。dev デプロイ = build 検証を
  兼ねるので操作は各環境とも tag push 1 回(+ stg/prd は Environments 承認)
- 欠点: dev だけ build が乗る非対称。promote 時のガード(後述)の実装が必要
- リスク・未知数: 同一 version で commit を打ち直す運用ミス(ガードで fail-closed にする)

#### 選択肢 C: build 専用トリガと deploy トリガの完全分離

- 概要: build 用 tag(または main merge)と環境別 deploy tag を別に設ける
- 利点: build と deploy の概念が完全に分離され対称
- 欠点: dev に出すだけで 2 操作必要。単独運用 + MVP は dev のみ構築の現状では過剰
- リスク・未知数: 特になし

### 論点 2: Task Definition の Terraform / CI 二重管理

#### 選択肢 a: Terraform を唯一のデプロイヤにする(image を tfvar 化、deploy = terraform apply)

- 概要: イメージ更新も infra tag → terraform apply で行う
- 利点: 二重管理が構造的に消える
- 欠点: アプリの deploy が infra パイプラインに結合し、ADR-0026 のコンポーネント分離と矛盾。
  毎デプロイに plan → 承認が挟まり重い
- リスク・未知数: 特になし

#### 選択肢 b: 責務分担 + 巻き戻し防止 3 点セット

- 概要: Task Definition の骨格(CPU / メモリ / Task Role / env vars / secrets / ログ設定)は
  **Terraform が正本**、稼働イメージは **CI(deploy workflow)が正本**。巻き戻しは
  (i) data source 注入、(ii) max(revision) パターン、(iii) apply ガード、の 3 点で防止(§3)
- 利点: アプリ deploy と infra 変更が独立。巻き戻り事故の全経路が「起きない」か
  「fail-closed(止まる)」に変換される
- 欠点: Terraform コードに data source / max() のボイラープレートが増える。初回 apply に
  bootstrap 用 placeholder image が必要
- リスク・未知数: plan 承認待ち中に deploy が割り込む race は残るが、(iii) で fail-closed 化

#### 選択肢 c: Task Definition を CI 完全所有(Terraform は初回生成のみ + ignore_changes)

- 概要: Terraform は Task Definition 全体を `ignore_changes` し、以後 CI が管理
- 利点: 二重管理は消える
- 欠点: env vars / Task Role 等のインフラ設定変更まで CI スクリプト管理になり、IaC の利点
  (plan で差分レビュー)を失う。`container_definitions` は単一属性のため部分 ignore は不可で、
  全部 ignore するしかない
- リスク・未知数: 設定変更の経路が二系統に割れて運用が複雑化

## 3. 決定(Decision)

**採用**: 論点 1 = 選択肢 B、論点 2 = 選択肢 b

1. **build once**: イメージ build は version につき 1 回、`<component>-vX.Y.Z-dev` tag push 時のみ。
   ECR イメージ tag は**環境を含まない `vX.Y.Z`**(ADR-0026 §6 の「`vX.Y.Z-dev` のまま」を本 ADR で
   修正)。対象は webapi / keycloak(イメージを持つ 2 コンポーネント。web / infra は対象外)
2. **promote digest**: `-stg` / suffix なし tag push は再 build せず、ECR `vX.Y.Z` の digest を解決して
   対象環境の ECS を更新する。**promote ガード**: (1) ECR に `vX.Y.Z` が存在すること、
   (2) 昇格 tag と同 version の `-dev` tag が同一 commit を指すこと。いずれか不成立なら fail-closed
3. **deploy 内部処理**: `describe-task-definition` で現行定義取得 → image を digest 指定
   (`@sha256:...`)に差し替え → `register-task-definition`(新 revision)→ `update-service`
4. **Task Definition の責務分担**: 骨格 = Terraform 正本(変更は infra tag → plan → 承認 → apply
   経路)、稼働イメージ = CI 正本(Terraform コードに稼働バージョンは残さない。稼働バージョンの
   記録は git tag + ECS + GitHub Release)
5. **巻き戻し防止 3 点セット**(Terraform / workflow 実装要件):
   - (i) Terraform の Task Definition は `data "aws_ecs_task_definition"` /
     `data "aws_ecs_container_definition"` で**現行稼働 image を読んで注入**する(初回 apply は
     bootstrap 用 placeholder image 変数で fallback)
   - (ii) ECS Service の向き先は
     `max(Terraform が作った revision, 稼働中最新 revision)`(HashiCorp 既知パターン)とし、
     巻き戻し方向への repoint を構造的に排除する
   - (iii) infra apply workflow(ADR-0026 の 2 段構成)の apply job 冒頭で、保存済み tfplan 内の
     image と稼働中 image を突合し、**不一致なら fail-closed で停止 → 再 plan**(plan 承認待ち中に
     アプリ deploy が割り込む race の吸収)

## 4. 理由(Rationale)

- 確定前提 6(イメージ全環境共通)の意図は「dev で検証した成果物がそのまま prd まで進む」ことで
  あり、digest 同一性まで含めて保証するには再 build を排除するしかない
- dev tag = build + deploy 兼用は、dev が最初の検証環境である以上「build できて起動するか」の確認と
  dev デプロイが同じ関心事であり、操作 1 回で済む。完全分離(選択肢 C)の対称性より運用の軽さを
  優先した(workflow 内部の job 分離で将来の昇格 workflow 再利用性は確保)
- 巻き戻し問題は「気を付ける」では防げない。3 点セットにより、巻き戻りが起きる全経路を
  「構造的に起きない」(i)(ii) か「止まる」(iii) に変換でき、失敗モードが安全側に倒れる
- 選択肢 a(Terraform 一本化)が捨てた「二重管理ゼロ」の利点は認めるが、アプリ deploy の
  リードタイムと ADR-0026 のコンポーネント独立デプロイを優先した

## 5. 影響(Consequences)

### 良い影響(Positive)

- 全環境で同一 digest が配備され、確定前提 6 が digest レベルで保証される
- 昇格が秒単位になり、stg/prd 昇格時の Native Image build 待ちが消える
- Terraform apply によるイメージ巻き戻し事故が構造的に排除される

### 悪い影響・制約(Negative)

- Terraform に data source / max() / placeholder 変数のボイラープレートが増える
- promote ガード・apply ガードの実装と、ガード発火時の再 plan 運用が必要
- ECR イメージ tag(`vX.Y.Z`)は immutable 前提の運用になる(同一 version の上書き push 禁止。
  打ち直しは patch version を上げる)

### 既存ドキュメント・規約への波及

- [ADR-0026](0026-component-scoped-deploy-tags.md) §6 — ECR イメージ tag と stg/prd 昇格の記述に
  本 ADR へのポインタ注記を追記(決定 §3 は不変。本 ADR と同一 PR)
- `docs/architecture/infrastructure-plan.md` — §3.7(keycloak ECR push の tag 表記)と
  §6.3 S2Infra-4〜7 注意書きの ECR tag 文言を修正(同一 PR、v5.5)
- `docs/specs/開発計画書.md` §11.2 — stg/prd は再 build せず digest promote である旨を追記
  (同一 PR、v1.5.2)
- Issue #479(ECS Service)— 巻き戻し防止 (i)(ii) を設計要件に追記
- Issue #481 / #482(webapi / keycloak deploy CI)— build job / deploy job 分離、ECR tag `vX.Y.Z`、
  promote ガードを設計要件に追記
- Issue #484(infra tag → apply)— apply ガード (iii) を設計要件に追記

## 6. 実装メモ(Implementation Notes)

- ECS Service の向き先(巻き戻し防止 (ii))の Terraform 例:

  ```hcl
  data "aws_ecs_task_definition" "webapi" {
    task_definition = aws_ecs_task_definition.webapi.family
  }

  resource "aws_ecs_service" "webapi" {
    task_definition = "${aws_ecs_task_definition.webapi.family}:${max(
      aws_ecs_task_definition.webapi.revision,
      data.aws_ecs_task_definition.webapi.revision,
    )}"
  }
  ```

- 巻き戻し防止 (i) の image 注入は `data "aws_ecs_container_definition"` の `image` を
  `aws_ecs_task_definition` の `container_definitions` に渡す。初回は当該 data source が存在しない
  ため、`var.bootstrap_image`(placeholder)へ `try()` で fallback する
- promote workflow の digest 解決は `aws ecr describe-images --repository-name tasks-webapi
  --image-ids imageTag=v1.2.0` で `imageDigest` を取得し、Task Definition には `@sha256:...` 形式で
  渡す(ECR tag の付け替えに依存しない)
- commit 一致検証は `git rev-list -n 1 webapi-v1.2.0-dev` と昇格 tag の commit を比較する
- MVP 期間は dev のみ構築のため、promote 経路(`-stg` / suffix なし workflow)の実装は stg 構築
  フェーズまで先送りしてよい。ただし #481 / #482 の build job / deploy job 分離と ECR tag `vX.Y.Z` は
  最初から本 ADR 準拠で実装する

## 7. 参考リンク(References)

- [ADR-0026](0026-component-scoped-deploy-tags.md) — デプロイ tag のコンポーネント別 prefix 化
  (本 ADR が §6 を一部修正)
- [ADR-0018](0018-container-image-build-with-boot-build-image.md) — bootBuildImage によるイメージ作成
- `docs/architecture/infrastructure-plan.md` §1 確定前提 5 / 6(env vars 注入・イメージ全環境共通)
- Issue #479 / #481 / #482 / #484(S2Infra-2 / 4 / 5 / 7)、tracker #206
- [Terraform AWS provider: aws_ecs_task_definition data source](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/data-sources/ecs_task_definition) — max(revision) パターンの公式例
