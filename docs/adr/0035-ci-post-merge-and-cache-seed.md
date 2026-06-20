# ADR-0035: CI の post-merge(push:main)実行と main キャッシュ seed 方針

- **Status**: Accepted
- **Date**: 2026-06-21
- **Deciders**: win2cot
- **Tags**: ci, infra

## 1. コンテキスト(Context)

一部の重い CI ワークフローが `pull_request` のみをトリガとし、main への push 時には走らない(`e2e-test.yml`、`webapi-native-build.yml`)。これにより以下の問題がある。

- PR チェックは `refs/pull/N/merge`(マージシミュレーション ref)で走るため「単独 PR をマージした直後の main」は実質検証済みだが、**同時期にマージされた複数 PR の組み合わせ(意味的衝突)で生じる main 実体の破壊は再検証されない**。
- **native ビルドが次に走るのは release タグ(`v*`)**。壊れた main の native ビルドが、最もまずいリリース時まで発覚しない。
- GitHub Actions のキャッシュ復元スコープは「自 ref + デフォルトブランチ」のみ。`pull_request` only のワークフローは main で走らず **main スコープのキャッシュを seed できない**ため、新規 PR は毎回 cold になる。キャッシュ実体監査(`GET /actions/caches`)で `setup-graalvm`(約 6.2GB)と `playwright`(約 1.3GB)が main キャッシュ無し・PR ref に分散していることを確認した。一方 npm / setup-gradle / trivy / tf-plugins は `push:main` を持つ姉妹ワークフローが seed しており健全。
- main の健全性シグナル(「main は green」)が無い。

本質的な解は GitHub merge queue だが、**本 repo は user アカウント `win2cot` 所有**であり、merge queue は Organization 所有 public リポジトリが前提のため利用できない。Organization への移管は行わない方針。

## 2. 検討した選択肢(Options Considered)

### 選択肢 A: GitHub merge queue + キャッシュ seed

- 概要: merge queue を有効化し、キュー内 PR を最新 main と組み合わせてマージ前に再検証。
- 利点: 同時マージの意味的衝突をマージ前にブロックできる本質解。squash も維持可能。
- 欠点・リスク: **user アカウント所有 repo では利用不可**(Org 所有が前提)。Org 移管は連携・secret・Project の貼り直しコストが大きく非採用。merge queue の検証 run は `gh-readonly-queue/*` ref で走るため main キャッシュ seed は別途必要。

### 選択肢 B: push:main で post-merge 実行(採用)

- 概要: 重い PR-only ワークフローに `push: branches: [main]` を追加し、main で post-merge 検証を走らせる。
- 利点: main 実体検証・native の release 前検知・main スコープのキャッシュ seed・健全性シグナルを一括取得。変更は各ワークフロー1行。
- 欠点・リスク: マージごとに e2e / native のフル実行コスト。`concurrency: cancel-in-progress` で直列化して緩和。同時マージの衝突は「事後検知」(マージ前ブロックは不可)。

### 選択肢 C: nightly スケジュール + 軽量キャッシュ温め

- 概要: main の nightly でフル e2e+native を回し、別途軽量ジョブでキャッシュを seed。
- 利点: マージごとのコストなし。
- 欠点: 検知が最大 ~24h 遅延。キャッシュ seed と検証を別ジョブに分けるぶん構成が増える。

## 3. 決定(Decision)

**採用**: 選択肢 B(push:main で post-merge 実行)。

`e2e-test.yml` および `webapi-native-build.yml` の `on:` に `push: branches: [main]` を追加する。merge queue は採用しない(可用性制約・Org 非移管方針)。

## 4. 理由(Rationale)

- merge queue は user repo で利用不可、Org 移管はコスト過大で不採用。
- B は1行追加で main 実体検証・native の release 前検知・キャッシュ seed・健全性シグナルを同時に満たす。
- 単独開発で同時マージ頻度が低く、同時マージ衝突を「事後検知」に留めるリスクは許容範囲。per-merge のフル実行コストも頻度が低いため許容。
- nightly(C)は検知遅延と構成増の割に B 比のコスト優位が単独開発では小さい。

## 5. 影響(Consequences)

### 良い影響(Positive)

- 新規 PR の初回 run から playwright / graalvm キャッシュがヒットし、cold 取得(各 PR で 261MB+ のダウンロード/再保存)が解消される。
- native ビルドの破壊が main マージ直後に検知され、release タグ時の発覚を防げる。
- main の post-merge グリーン/レッドが可視化される。

### 悪い影響・制約(Negative)

- main マージごとに e2e(フルスタック起動)/ native(低速コンパイル)が走り、CI 実行時間・リソースを消費する。
- 同時マージの意味的衝突はマージ前にブロックできず、post-merge での事後検知に留まる。

### 既存ドキュメント・規約への波及

- 対象外の明記: `terraform-plan.yml` は plan/apply 分離の設計どおりで変更しない。`webapi-ci.yml` / `web-ci.yml` / `e2e-lint.yml` / `keycloak-ci.yml` は既に `push:main` を持ち main seed 済みのため対象外。
- ADR-0027(CI 命名規約)と整合。新規ワークフローは追加しないため命名規約の変更は不要。

## 6. 実装メモ(Implementation Notes)

- 実装は #718(`e2e-test.yml` / `webapi-native-build.yml` に `push: branches: [main]` 追加)。case B 人手 PR。
- 検証: main マージ後に `GET /repos/win2cot/tasks-webapi/actions/caches` で `ref=refs/heads/main` の playwright / setup-graalvm キャッシュが生成されること、以降の新規 PR 初回 run でヒットすることを確認。
- `concurrency: cancel-in-progress` が main の連続マージで過剰に run を積み上げないことを確認。

## 7. 参考リンク(References)

- ADR-0023(最小 E2E 基盤)
- ADR-0027(CI 命名規約)
- ADR-0008(GraalVM native image)
- Issue #717(本 ADR 採択)/ #718(実装)/ #712 / #714(e2e-test.yml キャッシュ・警告対応)
- GitHub Docs: Managing a merge queue / About merge methods on GitHub
