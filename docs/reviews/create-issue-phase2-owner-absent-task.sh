#!/usr/bin/env bash
# =============================================================================
# ADR-0005(PR #186)で受け入れた「所有者不在タスク」運用リスクの救済機構を
# Phase 2 トラッキング親 #162 配下のサブ Issue として起票する。
#
# 前提: gh CLI / jq インストール済、gh auth login 実行済
#
# 使い方:
#   bash docs/reviews/create-issue-phase2-owner-absent-task.sh
# =============================================================================

set -euo pipefail
cd "$(dirname "$0")/../.."

# gh repo view で確実に取得(`.git` サフィックスなしの HTTPS URL でも動作)
REPO=$(gh repo view --json nameWithOwner --jq '.nameWithOwner')
if [ -z "${REPO}" ]; then
  echo "ERROR: failed to detect repo via gh CLI. Run 'gh auth login' first." >&2
  exit 1
fi
PARENT_PHASE2=162

echo "Target repo: ${REPO}"
echo "Phase 2 parent: #${PARENT_PHASE2}"
echo ""

# -----------------------------------------------------------------------------
# Issue 本文
# -----------------------------------------------------------------------------

BODY=$(cat <<'EOF'
## 背景

ADR-0005(PR #186 / 2026-05-17)で、タスク認可を「所有者・担当者・関係者の 3 役割のみで評価する」方針が確定した。Tenant Admin の業務タスク特権(強制編集・常時参照・削除)はすべて撤廃された。

この方針の帰結として、**退職ユーザーが所有していたタスクが「所有者不在」状態になる運用リスク**が発生する:

- 編集 / 公開範囲変更 / 関係者編集 / 論理削除はすべて「所有者のみ」
- 担当者がいる場合は参照とステータス変更は可能(ADR-0005 §3.1)
- 担当者すらいない場合、タスクは凍結(誰も編集できない、論理削除もできない)

ADR-0005 §5 でこのリスクを **MVP では運用リスクとして許容**することを決定した。組織的には人事プロセス側で退職時のタスク移管・所有者付け替えを行う想定。

本 Issue は **Phase 2 で救済機構を実装するかを再判定する**ためのトラッキング Issue。

## やること(Phase 2 着手時)

### 1. MVP リリース後の運用実態を確認

- 「所有者不在タスク」がどれくらいの頻度で発生したか
- 人事プロセス側のタスク移管がワークしているか
- 利用者・運営者から救済要望が出ているか

### 2. 実装方針の検討(必要であれば)

候補案:

- **A. 所有者引継ぎ API**: Tenant Admin が `PATCH /api/tasks/{id}/owner` で所有者を変更できる API を追加
    - 認可: `hasRole('TENANT_ADMIN')` + 監査ログ記録(`OWNER_TRANSFERRED` action 等)
    - 注意: 「Tenant Admin の業務タスク特権撤廃」(ADR-0005)との緊張があるため、操作対象を「所有者不在状態のタスク」に限定する条件付き例外として位置付けるのが筋
- **B. 退職処理 API**: ユーザー無効化(#177)と連動して、当該ユーザーが所有するタスクを一括で別ユーザーに付け替える
- **C. 救済機構を実装しない(現状維持)**: 運用で吸収

### 3. 関連 Issue との整合

- **#177**(Phase 2: TenantUser.status DISABLED 遷移 API): ユーザー無効化フローと連動する場合は同期検討
- **#167**(Phase 2: テナント解約): テナント解約時の全タスク処理と整合
- **ADR-0005**: 本 ADR を Supersede する別 ADR が必要かは、選択肢 A 採用時に要判定

## 受け入れ条件

- MVP リリース後の運用実態が文書化されている
- 実装するかしないか(選択肢 A/B/C)の判断がされ、関連 ADR / 設計書に記録されている

## 参照

- 親 Issue: #162(Phase 2 トラッキング)
- 派生元: ADR-0005(PR #186)
- 関連: #177(ユーザー無効化)/ #167(テナント解約)
- 実現可能性は **低い**(組織的に人事プロセス側で対応する前提)が、運用リスク管理のため記録は残す
EOF
)

# -----------------------------------------------------------------------------
# Issue 起票 + Sub-issue 紐付け
# -----------------------------------------------------------------------------

echo "→ Phase 2 サブ Issue 起票"
URL=$(gh issue create --repo "${REPO}" \
  --title "feature: 所有者不在タスクの救済機構(所有者引継ぎ API 等)検討 [Phase 2]" \
  --label "enhancement,priority/p3,area/backend,area/openapi" \
  --body "${BODY}")
NUM=$(echo "${URL}" | grep -oE '[0-9]+$')
echo "  created: ${URL}"

echo ""
echo "→ Sub-issue 紐付け(#${PARENT_PHASE2} 配下)"
DB_ID=$(gh api "repos/${REPO}/issues/${NUM}" --jq '.id')
gh api "repos/${REPO}/issues/${PARENT_PHASE2}/sub_issues" \
  -X POST \
  -H "Accept: application/vnd.github+json" \
  -F sub_issue_id="${DB_ID}" \
  > /dev/null
echo "  └─ linked #${NUM} as sub-issue of #${PARENT_PHASE2}"

echo ""
echo "============================================================"
echo " 起票完了"
echo "============================================================"
echo ""
echo "新規 Phase 2 サブ Issue:"
echo "  #${NUM}  ${URL}"
echo ""
echo "次のステップ:"
echo "  ADR-0005 §6.2 Phase 2 検討項目に Issue #${NUM} を追記"
