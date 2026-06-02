# claude-automation 連携メモ

tasks-webapi における [claude-automation](https://github.com/win2cot/claude-automation) 統合の経緯・仕組み・運用方法をまとめる。

## 概要

**claude-automation** は、GitHub リポジトリへの AI 駆動の自動化（実装・レビュー・マージ・エスカレーション）を提供する汎用基盤リポジトリ。
Reusable Workflow として公開されており、利用者リポジトリからは `@v1`（major version tag）で参照するだけで導入できる。

tasks-webapi では 2026-05 に導入し、以前の `claude.yml` / `claude-code-review.yml` を shim 5 本に置き換えた（PR [#195](https://github.com/win2cot/tasks-webapi/pull/195) / [#196](https://github.com/win2cot/tasks-webapi/pull/196)）。

## shim workflow 一覧

tasks-webapi の `.github/workflows/` に配置された shim は以下の 5 本。
いずれも本体ロジックは claude-automation 側の reusable workflow に委譲し、shim はトリガー条件・権限・シークレット転送のみを担う。

| ファイル | workflow 名 | 役割 |
|---|---|---|
| `claude-impl.yml` | Claude Impl | Issue に `claude:ready` ラベルが付与されると**実装 Claude** を起動し、ブランチ作成・実装・PR 作成を自動化する |
| `claude-impl-fix.yml` | Claude Impl Fix | レビュー指摘（`changes_requested` / review コメント）を受けて実装 Claude が修正コミットと対応完了レポートを投稿する |
| `claude-review.yml` | Claude Review | PR が `ready_for_review` になるか、実装 Claude の対応完了シグナル（`signal: claude-impl-done`）を検知すると**レビュワ Claude** を起動する |
| `claude-auto-merge.yml` | Claude Auto Merge | `claude-automation-review[bot]` が approve した PR に auto-merge を有効化する（`needs-human-decision` ラベル付き PR は除外） |
| `claude-notify-human.yml` | Claude Notify Human | `needs-human-decision` ラベル付与でリポジトリオーナー（`@win2cot`）をメンションし、人の判断を促す |

## 起動方法（ラベルベースのフロー）

通常フローは以下の順序で進む:

```text
[人] Issue に claude:ready ラベル付与
        ↓
[claude-impl] 実装 Claude 起動
  → ブランチ作成・実装コミット・draft PR 作成 → ready PR 化
        ↓
[claude-review] レビュワ Claude 起動（PR ready 化を検知）
  → コードレビュー・承認 or changes_requested
        ↓ （changes_requested の場合）
[claude-impl-fix] 実装 Claude 起動（レビュー指摘を検知）
  → 修正コミット・対応完了レポート投稿（signal: claude-impl-done）
        ↓
[claude-review] レビュワ Claude 再起動（シグナルを検知）
  → 再レビュー・承認
        ↓ （approve の場合）
[claude-auto-merge] auto-merge 有効化 → CI 通過後マージ
```

**人の判断が必要なケース**:
実装 Claude または レビュワ Claude が設計判断・スコープ外の変更・破壊的変更を検知した場合、`needs-human-decision` ラベルを付与する。
これにより `claude-notify-human` が `@win2cot` をメンションし、auto-merge は実行されない。

## 利用バージョン

tasks-webapi は claude-automation を **`@v1`**（major version tag）で参照する。

```yaml
uses: win2cot/claude-automation/.github/workflows/reusable-impl.yml@v1
```

`@v1` は後方互換性を維持したまま更新される。破壊的変更時は major version が上がるため、tasks-webapi 側での明示的な更新作業が発生する。

## 関連リンク

- claude-automation リポジトリ: <https://github.com/win2cot/claude-automation>
- PR #195（shim workflow 追加）: <https://github.com/win2cot/tasks-webapi/pull/195>
- PR #196（legacy workflow 削除）: <https://github.com/win2cot/tasks-webapi/pull/196>
