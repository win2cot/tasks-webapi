# 未登録Issueテンプレート(PR #76 第7回レビュー)

**作成日**: 2026-05-14
**対象レビュー**: PR #76 上の Claude 自動レビュー第7回(2026-05-14T00:21:20Z)
**前提**: 必須#1(`selectTenant` の `tenantHeader` オーバーライド) は PR #76 に追加コミット予定

**Issue 件数**: 4件(#5 既存Issue 23、#7 既存Issue 26 のため除外)

---

## Issue 32: api(openapi): 全スキーマの required 配列を整備 (R7#2、Issue 29 拡張)

**Labels**: `pr-review/76`, `priority/p2`, `area/openapi`

**Body**:
```
## 背景

OpenAPI Generator でクライアントコード生成時、`required` 未定義スキーマは全フィールド optional/nullable 扱いになる。
`UserProfile` / `UserSummary` には対応済だが、下記スキーマには未定義(レビュー継続指摘)。

## やること

`api/openapi.yaml` の各スキーマに `required:` を追加:

| スキーマ | required 推奨フィールド |
|---|---|
| `Tenant` | `id`, `code`, `name`, `plan`, `status`, `createdAt`, `updatedAt` |
| `TenantSummary` | `id`, `code`, `name`, `role` |
| `MeResponse` | `user`, `tenants` |
| `TenantUser` | `userId`, `email`, `fullName`, `role`, `status` |
| `Task` | `id`, `title`, `status`, `priority`, `visibility`, `owner`, `dueDate`, `createdAt`, `updatedAt`, `editable`, `deletable` |
| `TaskPage` | `content`, `totalElements`, `totalPages`, `number`, `size` |
| `Stakeholder` | `userId`, `fullName`, `email` |
| `DashboardSummary` | `todayDueCount`, `overdueCount`, `completedTodayCount`, `myOpenCount` |
| `AuditLog` | `id`, `action`, `createdAt` |

## 受け入れ条件

- [ ] 上記9スキーマに required 配列が追加されている
- [ ] OpenAPI Lint(spectral)を通過

## 注記

Issue 29(TenantSummary/MeResponse/Tenant のみ)は本Issueに統合してクローズ。

## 参照

PR #76 Claude review (2026-05-14T00:21:20Z) 推奨 #2
```

---

## Issue 33: docs/PR: PR #76 説明文の「22 endpoints」を「24 endpoints」に更新 (R7#3)

**Labels**: `pr-review/76`, `priority/p2`, `area/docs`

**Body**:
```
## 背景

PR #76 の説明本文に「22 endpoints」と記載されているが、実際は通知設定 API 2件追加後 24件。
`docs/README.md` は既に「24」に更新済だが、PR本文だけ古い。

## やること

GitHub UI で PR #76 を編集し、本文内の以下を更新:

- `OpenAPI 3.1 (22 endpoints)` → `OpenAPI 3.1 (24 endpoints)`

または `gh` CLI で:

```bash
gh pr edit 76 --body-file - << 'EOF'
(更新後の本文)
EOF
```

## 受け入れ条件

- [ ] PR #76 description が「24 endpoints」に修正されている

## 参照

PR #76 Claude review (2026-05-14T00:21:20Z) 推奨 #3
```

---

## Issue 34: api(openapi): GET /api/tasks の status クエリパラメータに enum 値を明示 (R7#4)

**Labels**: `pr-review/76`, `priority/p2`, `area/openapi`

**Body**:
```
## 背景

`GET /api/tasks` の `status` クエリパラメータは:

```yaml
- { name: status, in: query, schema: { type: string }, description: "カンマ区切りで複数指定可" }
```

有効値(`NOT_STARTED`, `IN_PROGRESS`, `DONE`, `ON_HOLD`)が記載されていない。
クライアントが値を推測する必要があり、補完機能のあるツールでも候補表示されない。

## やること

カンマ区切り対応のため pattern を明示:

```yaml
- name: status
  in: query
  description: |
    ステータスのカンマ区切り(複数指定可)。有効値: NOT_STARTED / IN_PROGRESS / DONE / ON_HOLD
    例: `NOT_STARTED,IN_PROGRESS`
  schema:
    type: string
    pattern: "^(NOT_STARTED|IN_PROGRESS|DONE|ON_HOLD)(,(NOT_STARTED|IN_PROGRESS|DONE|ON_HOLD))*$"
    example: "NOT_STARTED,IN_PROGRESS"
```

または素直に配列化(より OpenAPI らしい):

```yaml
- name: status
  in: query
  description: ステータス(複数指定可)
  schema:
    type: array
    items: { $ref: "#/components/schemas/TaskStatus" }
  style: form
  explode: false
```

## 受け入れ条件

- [ ] 有効値が API 仕様で機械的に読める形(enum/pattern)になっている

## 参照

PR #76 Claude review (2026-05-14T00:21:20Z) 推奨 #4
```

---

## Issue 35: docs(spec)+api: TenantUser.status = DISABLED への変更手段を仕様化 (R7#6)

**Labels**: `pr-review/76`, `priority/p1`, `area/docs`, `area/openapi`

**Body**:
```
## 背景

`TenantUser` スキーマには `status: [ACTIVE, INVITED, DISABLED]` が定義されているが、
ACTIVE → DISABLED への変更手段が API 仕様に存在しない。
`PUT /api/tenant/users/{userId}/role` はロール変更のみが対象。

## 選択肢

### 方針 A: 専用エンドポイント追加(Tenant Admin によるユーザー無効化)

```yaml
/api/tenant/users/{userId}/status:
  patch:
    tags: [User]
    summary: テナント内ユーザーのステータス変更 (Tenant Admin)
    description: ACTIVE / DISABLED のトグル。所属解除ではなく一時無効化用途
    requestBody:
      required: true
      content:
        application/json:
          schema:
            type: object
            required: [status]
            properties:
              status: { type: string, enum: [ACTIVE, DISABLED] }
    responses:
      "200": { description: OK }
      ...
```

### 方針 B: 本フェーズではスコープ外と明記

要件定義書 §3.3 ユーザー管理機能の F-06 ロール管理に「無効化機能は将来フェーズ」を明記し、
TenantUser スキーマの `status` description に「本フェーズで変更可能なのは ACTIVE/INVITED のみ。DISABLED は将来フェーズで対応」を追記。

## 推奨方針

ユーザー無効化は監査・コンプライアンス上の重要機能だが、要件定義書 §3.3 では明示的に求められていない。
**方針B(スコープ外明記)** で v1.3 まで進め、将来フェーズで方針A を追加する。

## 受け入れ条件

- [ ] 方針が選択され、設計書/OpenAPI に反映されている

## 参照

PR #76 Claude review (2026-05-14T00:21:20Z) 設計整合 #6
```
