# ADR-0026: デプロイ tag のコンポーネント別 prefix 化と infra apply の承認ゲート

- **Status**: Accepted
- **Date**: 2026-06-12
- **Deciders**: win2cot, 開発チーム
- **Tags**: ci-cd, infra, release

## 目次

- [1. コンテキスト(Context)](#1-コンテキストcontext)
- [2. 検討した選択肢(Options Considered)](#2-検討した選択肢options-considered)
  - [論点 1: デプロイ粒度(tag 命名)](#論点-1-デプロイ粒度tag-命名)
  - [論点 2: infra(terraform apply)のトリガと安全弁](#論点-2-infraterraform-applyのトリガと安全弁)
- [3. 決定(Decision)](#3-決定decision)
- [4. 理由(Rationale)](#4-理由rationale)
- [5. 影響(Consequences)](#5-影響consequences)
  - [良い影響(Positive)](#良い影響positive)
  - [悪い影響・制約(Negative)](#悪い影響制約negative)
  - [既存ドキュメント・規約への波及](#既存ドキュメント規約への波及)
- [6. 実装メモ(Implementation Notes)](#6-実装メモimplementation-notes)
- [7. 参考リンク(References)](#7-参考リンクreferences)

## 1. コンテキスト(Context)

`docs/architecture/infrastructure-plan.md` v5 §1 で CI/CD トリガは **tag 駆動**(項目 8)、tag 命名は
**SemVer + 環境 suffix**(`vX.Y.Z-dev` / `vX.Y.Z-stg` / `vX.Y.Z`、項目 10)と確定済みである。
一方、本リポジトリは monorepo(`webapi/` / `keycloak/` / `web/` / `infra/` の 4 subdir)であり、
デプロイ workflow は 4 ストリーム(S2Infra-4〜7 = Issue #481〜#484)に分かれる。

ここに矛盾がある。tag 形式にコンポーネント識別が無いにもかかわらず、infrastructure-plan §6 の
受入基準および Issue #481 / #484 本文は「tasks-webapi の `v0.1.0-dev` tag push で…」
「infra の `v0.1.0-dev` tag push で…」と、**コンポーネントごとに同名 tag を打つ前提**で記述されている。
git の tag はリポジトリ全体で一意であるため、この前提はそのままでは実現不能である。
さらに `docs/specs/開発計画書.md` §11.2 には `infra/v*-*` というスラッシュ形式のコンポーネント
prefix 表記が部分的に混在しており(同節内の `vX.Y.Z-dev` 表記と不整合)、tag 形式の正本が
定まっていない。

また、infra(terraform apply)は現行 `terraform-apply.yml` が `workflow_dispatch` +
GitHub Environments(`platform-apply` / `tasks-apply`)で構成されており、tag 駆動化する場合に
「plan を確認してから apply する」安全弁をどう維持するかも決める必要がある。

S2Infra-4〜7(#481〜#484)は全件未着手であり、実装着手前の今、デプロイ粒度・tag 命名・
infra の承認フローを確定する。

## 2. 検討した選択肢(Options Considered)

### 論点 1: デプロイ粒度(tag 命名)

#### 選択肢 A: コンポーネント別 prefix tag

- 概要: `<component>-vX.Y.Z[-dev|-stg]`(例: `webapi-v1.2.0-dev`)。component ∈
  {`webapi`, `keycloak`, `web`, `infra`}。各コンポーネントが独立した SemVer を持ち、
  workflow は `on.push.tags` のパターンマッチ(`webapi-v*` 等)で振り分ける
- 利点: monorepo の定石。tag 名前空間の衝突が自然解消。変更したコンポーネントだけを
  デプロイでき、無駄な build(Native Image は数分〜十数分)・無駄な ECS rolling update を回避
- 欠点: 「製品リリース一式」を表す単一バージョンが無くなる
- リスク・未知数: コンポーネント間に依存がある変更(例: infra の Listener Rule 変更 + webapi の
  API 追加)は tag を打つ順序を運用で管理する必要がある

#### 選択肢 B: 単一 tag + 変更検知

- 概要: `vX.Y.Z-dev` 1 本で 4 workflow が起動し、前回 tag との diff で変更があった subdir
  だけ実デプロイする
- 利点: 製品バージョンが一意でわかりやすい
- 欠点: 変更検知ロジック(前回 tag の特定 + paths diff)の実装・保守コストが 4 workflow 分かかる。
  infra だけ apply したい場合も全体バージョンが進む
- リスク・未知数: 変更検知の誤判定(検知漏れ・過剰検知)がデプロイ抜け・無駄デプロイに直結する

#### 選択肢 C: 単一 tag で 4 ストリーム全部実行

- 概要: 現仕様の文言どおり、`vX.Y.Z-dev` 1 本で 4 workflow すべてが無条件に実行される
- 利点: 最も単純。変更検知も prefix 振り分けも不要
- 欠点: webapi 1 行修正の tag でも keycloak イメージ再 build・terraform apply・Native Image build が
  毎回走る。再 build はイメージ digest が変わるため、変更が無いコンポーネントにも無駄な
  ECS rolling update が発生する
- リスク・未知数: CI 時間・コスト(GitHub Actions 分・ECR push)が tag のたびに 4 ストリーム分かかる

### 論点 2: infra(terraform apply)のトリガと安全弁

#### 選択肢 D: tag 駆動 + plan→承認→apply の 2 段 job

- 概要: `infra-vX.Y.Z-dev` tag push で起動。plan job が `terraform plan -out=tfplan` を実行して
  内容を job summary に出力 + tfplan を artifact 保存。apply job は GitHub Environments の
  required reviewers で**一時停止**し、承認後に保存済み tfplan をそのまま apply する
- 利点: アプリと運用(tag 駆動)が揃う。レビューした plan と適用内容の一致が tfplan artifact で
  保証される。既存の `platform-apply` / `tasks-apply` environment をそのまま流用できる
- 欠点: workflow が 2 段構成になり、environment への required reviewers 設定が前提になる
- リスク・未知数: artifact 経由の tfplan は provider バージョン・state の不一致で apply 時に
  失敗し得る(同一 workflow run 内で完結させることで最小化)

#### 選択肢 E: tag 駆動に含めない(workflow_dispatch 維持)

- 概要: 現行 `terraform-apply.yml` の手動起動を維持する
- 利点: 現状のまま。誤 tag による意図しない apply が起きない
- 欠点: アプリは tag 駆動・infra は手動と運用が分裂する。infrastructure-plan §1 項目 8 の
  「tag 駆動」確定事項と矛盾したままになる
- リスク・未知数: 特になし

## 3. 決定(Decision)

**採用**: 論点 1 = 選択肢 A、論点 2 = 選択肢 D

1. デプロイ tag は **`<component>-vX.Y.Z[-dev|-stg]`** とする。component は
   `webapi` / `keycloak` / `web` / `infra` の 4 値。環境 suffix は従来どおり
   `-dev` / `-stg` / suffix なし(= prd)で、各コンポーネントは独立した SemVer を持つ。
2. デプロイ workflow(S2Infra-4〜7)は `on.push.tags` の prefix パターン
   (`webapi-v*` / `keycloak-v*` / `web-v*` / `infra-v*`)で 1 コンポーネント = 1 workflow に振り分ける。
3. infra も tag 駆動に**含める**。ただし plan job(summary 出力 + tfplan artifact 保存)→
   GitHub Environments required reviewers による承認 → apply job(保存済み tfplan を適用)の
   2 段構成とし、「plan を見てから apply」の安全弁を維持する。

## 4. 理由(Rationale)

- 受入基準が前提としていた「コンポーネントごとの tag push」は、prefix 無しの tag 形式では
  git tag の一意性と矛盾し実現不能だった。prefix 化は当初意図を実現する最小の修正である
- monorepo のコンポーネント別 prefix tag は広く使われる定石であり、変更検知ロジック(選択肢 B)の
  ような独自実装の保守を持ち込まない
- 不要な再 build・再デプロイ(選択肢 C)を構造的に排除できる。特に Native Image build(ADR-0018 /
  #478 系)は長時間であり、毎 tag 全実行はコスト・リードタイムの両面で重い
- infra を tag 駆動に揃えることで「デプロイはすべて tag」という単一の運用モデルになる。
  Environments required reviewers は public リポジトリで追加コストなく利用でき、
  tfplan artifact の引き継ぎにより承認対象と適用内容の一致を保証できる
- 捨てた利点: 製品全体を指す単一バージョン(選択肢 B / C)。リリースの束は GitHub Release の
  release note(各コンポーネント tag の組を記載)で代替する

## 5. 影響(Consequences)

### 良い影響(Positive)

- 4 コンポーネントが独立にリリース可能になり、tag 名前空間の衝突が解消される
- 変更の無いコンポーネントの build・ECS rolling update・terraform apply が走らなくなる
- infra apply に plan レビューの承認ゲートが明文化される

### 悪い影響・制約(Negative)

- 「製品リリース一式」を 1 つの tag で表せない(release note 運用で補う)
- tag の本数が増える(コンポーネント数 × 環境数)
- コンポーネント間に依存がある変更は tag を打つ順序(例: infra → webapi)を運用で管理する

### 既存ドキュメント・規約への波及

- `docs/architecture/infrastructure-plan.md` §1 項目 8 / 項目 10、§6 の S2Infra-4〜7 受入基準
  (tag 例の表記)— 本 ADR と同一 PR で改訂する
- `docs/specs/開発計画書.md` §5.2「リリース時にタグ(v1.0.0等)を付与」および §11.2 の
  tag 形式(`vX.Y.Z-dev` 系 + `infra/v*-*` 旧表記の混在)— コンポーネント別 prefix tag の
  形式に追従(同一 PR で改訂する)
- Issue #481〜#484 本文の tag 表記 — 起票済み Issue の本文を edit で整合(#484 には
  plan→承認→apply の 2 段構成を設計要件として追記)

## 6. 実装メモ(Implementation Notes)

- workflow トリガ例:

  ```yaml
  on:
    push:
      tags:
        - 'webapi-v*'
  ```

- terraform workflow は plan job と apply job を分離し、`needs:` + actions/upload-artifact /
  download-artifact で tfplan を引き継ぐ。apply job に `environment:`(required reviewers 設定済みの
  `platform-apply` / `tasks-apply`)を指定する。concurrency group は現行設定を維持する
- 既存 environment に required reviewers(win2cot)が未設定なら設定する(repo Settings →
  Environments)
- ECR のイメージ tag はリポジトリ名(`tasks-webapi` / `keycloak-custom`)がコンポーネントを
  識別するため、`vX.Y.Z-dev` 形式(git tag から prefix を除いた部分)のままでよい
- stg / prd への昇格は同一 commit に環境 suffix 違いの tag(例: `webapi-v1.2.0-stg`)を追加付与する。
  MVP 期間は dev のみ構築のため当面 `-dev` のみ使用する
- 検証は S2Infra-4(#481)の受入基準(`webapi-v0.1.0-dev` tag push で ECR push + ECS update)を
  最初の実機確認とする

## 7. 参考リンク(References)

- `docs/architecture/infrastructure-plan.md` v5 §1(確定事項 8 / 10)・§6(S2Infra-4〜7)
- Issue #481 / #482 / #483 / #484(S2Infra-4〜7)、親 tracker #206
- [ADR-0018](0018-container-image-build-with-boot-build-image.md) — コンテナイメージ作成
  (bootBuildImage)
- `.github/workflows/terraform-apply.yml` — 現行の workflow_dispatch + Environments 構成
- [GitHub Docs: Reviewing deployments](https://docs.github.com/en/actions/managing-workflow-runs/reviewing-deployments) — Environments required reviewers
