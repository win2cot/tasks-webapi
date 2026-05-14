#!/usr/bin/env bash
# =============================================================================
# PR #76 自動レビューの推奨/軽微7件を GitHub Issue として一括作成するスクリプト
#
# 前提:
#   - gh CLI がインストール済 (https://cli.github.com/)
#   - gh auth login 実行済
#   - 必須6件は既に PR #76 に追加コミット済 (基本設計書 v1.3.1, OpenAPI v1.3.0)
#
# 使い方:
#   bash docs/reviews/create-issues-pr76.sh
#
# 新規ラベル(初回のみ):
#   gh label create pr-review/76 --color BFD4F2
# =============================================================================

set -euo pipefail
cd "$(dirname "$0")/../.."

REPO=$(git config --get remote.origin.url | sed -E 's|.*github.com[:/]([^/]+/[^.]+)\.git|\1|')
echo "Target repo: ${REPO}"

create_issue() {
  local title="$1"
  local labels="$2"
  local body="$3"
  echo ""
  echo "→ creating: ${title}"
  gh issue create --repo "${REPO}" \
    --title "${title}" \
    --label "${labels}" \
    --body "${body}"
}

# ----- 推奨 / 軽微 (PR #76 由来 7件) -----

create_issue \
  "api(openapi): GET /api/tasks の sort パラメータに有効値を明示 (Review1 #5)" \
  "pr-review/76,priority/p2,area/openapi" \
  "## 背景

\`GET /api/tasks\` の \`sort\` クエリパラメータの有効値・パターンが API 仕様書に未記載。

## やること

\`api/openapi.yaml\` の \`listTasks.parameters[sort]\` に description / enum パターン明記。
有効フィールド: dueDate / priority / createdAt / updatedAt / title。形式 \`<field>,<asc|desc>\`。

## 参照

PR #76 Claude review (2026-05-09) 軽微 #5"

create_issue \
  "docs(spec)+api: visibility=TENANT/PRIVATE 戻し時の関係者リスト挙動を明記 (Review1 #6)" \
  "pr-review/76,priority/p1,area/docs,area/openapi" \
  "## 背景

シーケンス図 sequence-05 では関係者追加で visibility が STAKEHOLDERS に自動昇格する旨を明示しているが、
逆方向(visibility を TENANT/PRIVATE に戻した場合)の task_stakeholders レコード扱いが未定義。

## 推奨方針

- TENANT 戻し: 関係者レコードを保持(将来 STAKEHOLDERS に再昇格時に流用)
- PRIVATE 戻し: 関係者レコードを CASCADE 削除(audit_logs に記録)

基本設計書 §3.4.4 と OpenAPI \`PATCH /api/tasks/{id}/visibility\` description に明記し、
シーケンス図 sequence-05 を拡張または新規 sequence-06 を追加。

## 参照

PR #76 Claude review (2026-05-09) 軽微 #6"

create_issue \
  "docs(spec): 要件定義書を v1.3 へ更新 — 改訂履歴整合 (Review2 R-1)" \
  "pr-review/76,design-review,priority/p2,area/docs" \
  "## 背景

基本設計書は v1.3.1(2026-05-10)まで改訂済だが、要件定義書は v1.2 のまま。
両文書の版番号対応関係が追跡困難。

## やること

\`docs/specs/要件定義書.md\` を v1.3 に更新し、改訂履歴に「2026-05-10: 基本設計書 v1.3 / v1.3.1 反映に伴うクロスリファレンス更新」を追記。
基本設計書 \"関連文書\" セクションも要件定義書 v1.3 を参照するよう更新。

## 参照

PR #76 Claude review (2026-05-10) 推奨 R-1"

create_issue \
  "docs(spec): HTTP 409 の使用シナリオを §5.4 に詳細化 (Review2 R-2)" \
  "pr-review/76,design-review,priority/p2,area/docs" \
  "## 背景

OpenAPI では \`POST /api/tenant/users/invite\` のみが 409 定義済だが、
基本設計書 §5.4 のエラー応答表で 409 シナリオが「一意制約違反など」と曖昧。

## やること

§5.4 に 409 が返却される具体的シナリオ表を追記:
- ユーザー招待時に既に user_tenants に存在
- ユーザー招待時に oidc_sub が未登録
- 関係者追加時に既存登録
- テナント code が一意制約違反

OpenAPI 該当エンドポイントの 409 description も表と一致させる。

## 参照

PR #76 Claude review (2026-05-10) 推奨 R-2"

create_issue \
  "docs/code: 認可ロジックの SSOT を Domain 層 (TaskAuthorizationDomainService) に集約 (Review2 R-3)" \
  "pr-review/76,design-review,priority/p1,area/docs,area/security" \
  "## 背景

シーケンス図と OpenAPI 双方に visibility 認可フィルタの詳細が記述され、仕様変更時の更新漏れリスクあり。

## やること

1. 設計書/シーケンス図で認可フィルタ詳細を簡潔化、「実装は TaskAuthorizationDomainService が SSOT」と注記
2. OpenAPI description を「詳細は基本設計書 §6.2.1 参照」型に変更
3. 実装フェーズで TaskAuthorizationDomainService のメソッド定義を Domain 層に配置

## 参照

PR #76 Claude review (2026-05-10) 推奨 R-3"

create_issue \
  "docs(diagrams): Tenant Admin の visibility フィルタ免除挙動をシーケンス図に明示 (Review2 R-4)" \
  "pr-review/76,design-review,priority/p1,area/docs" \
  "## 背景

基本設計書 §6.2.1 で「Tenant Admin は常に参照可」と明記しているが、シーケンス図 sequence-03 にこの分岐が無い。
実装者が visibility フィルタを Tenant Admin にも適用するか判断できない。

## やること

\`sequence-03-task-list-authz.mmd\` に Tenant Admin 分岐を追加:
- currentRole == TENANT_ADMIN なら visibility フィルタをスキップ(全件返却、tenant_id 絞込は維持)
- 監査ログに LIST_TASKS_AS_ADMIN を記録(任意)

## 参照

PR #76 Claude review (2026-05-10) 推奨 R-4"

create_issue \
  "docs(spec): セキュリティ検証の層別責務表を §6.2 に追記 (Review2 R-5)" \
  "pr-review/76,design-review,priority/p2,area/docs,area/security" \
  "## 背景

JWT 検証と X-Tenant-Id 検証がどの層で行われるか設計書に未記載。実装者が重複実装するリスク。

## やること

§6.2 に層別責務表を追記:
- JWT 署名検証: Spring Security フィルタ
- JWT クレーム抽出: TasksJwtAuthenticationConverter
- アクティブテナント解決: TenantContextFilter (X-Tenant-Id ↔ user_tenants 照合)
- テナント越境検出: Hibernate Filter
- メソッドロール認可: @PreAuthorize
- タスクごと認可: TaskAuthorizationDomainService(Domain層)

## 参照

PR #76 Claude review (2026-05-10) 推奨 R-5"

create_issue \
  "docs(diagrams): Mermaid 図の言語スタイル統一 (Review2 N-1)" \
  "pr-review/76,priority/p2,area/docs" \
  "## 背景

\`docs/diagrams/*.mmd\` のクラス名は英語、コメント・ラベル・説明は日本語。
レンダラー(GitHub / VS Code Mermaid Preview)によって表示品質にばらつきがある。

## 推奨方針

\`%%\` ヘッダコメントを英語に統一(本文の日本語ラベルは保持)が変更コスト最小・国際化耐性あり。

## やること

8つの .mmd ファイルでコメント記述を統一(GitHub / VS Code 上で目視検証)。

## 参照

PR #76 Claude review (2026-05-10) 軽微 N-1"

echo ""
echo "✅ 全7件のIssue作成リクエスト送信完了"
