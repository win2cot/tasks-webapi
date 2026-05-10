# 未登録Issueテンプレート(PR #76 第4回レビュー・推奨分)

**作成日**: 2026-05-10
**対象レビュー**: PR #76 上の Claude 自動レビュー第4回(2026-05-10T13:20:40Z)
**前提**: 第4回レビューの **必須3件**(#1 PATCH /tasks/{id}/status 404 / #2 POST /tasks/{id}/stakeholders 404 / #3 /auth/me と /auth/logout の security オーバーライド)は PR #76 に追加コミット済(基本設計書 v1.3.2、OpenAPI v1.3.1)

**Issue 件数**: 4件(#7 一時ファイル方針は議論項目で別扱い)

利用方法:
- **方法 A**: 同フォルダの `create-issues-pr76-r2.sh` を `gh` CLI で実行(一括作成)
- **方法 B**: 下記テンプレートを GitHub UI に手動貼り付け

ラベル `pr-review/76` は前回作成済(なければ `gh label create pr-review/76 --color BFD4F2`)。

---

## Issue 23: api(openapi): PATCH /api/tasks/{id}/visibility の 200 レスポンスにボディスキーマを追加 (R2#4)

**Labels**: `pr-review/76`, `priority/p2`, `area/openapi`

**Body**:
```
## 背景

`PATCH /api/tasks/{id}/visibility` の `200` レスポンスは現在 `{ description: OK }` のみで、
ボディスキーマが未定義。同じ PATCH 系の `/api/tasks/{id}/status` は `Task` を返すため、一貫性のない設計になっている。

## やること

下記いずれかの方針で `api/openapi.yaml` を更新:

1. **更新後の Task を返す方針**:
   ```yaml
   "200":
     description: OK
     content:
       application/json:
         schema: { $ref: "#/components/schemas/Task" }
   ```
2. **No Content にする方針**: `200` を `204` に変更し description のみ。

`PATCH /api/tasks/{id}/status` との一貫性から方針 1 を推奨。

## 参照

PR #76 Claude review (2026-05-10T13:20:40Z) 推奨 #4
```

---

## Issue 24: docs(diagrams): シーケンス図03 に N+1 クエリ回避(関係者一括フェッチ)を明示 (R2#5)

**Labels**: `pr-review/76`, `priority/p1`, `area/docs`

**Body**:
```
## 背景

`docs/diagrams/sequence-03-task-list-authz.mmd` の現状フローでは、
visibility=STAKEHOLDERS のタスクに対する `assertViewable` 判定がループ内で行われており、
タスクごとに関係者リストの DB クエリが発生する N+1 リスクがある。
30件タスクで最大30件の追加クエリ。

## やること

シーケンス図を修正し、ループ前に「自分が関係者となっているタスクIDの一括取得」ステップを追加:

```
UC->>SHRepo: findTaskIdsByUser(currentUserId, tenantId)
SHRepo->>DB: SELECT task_id FROM task_stakeholders WHERE user_id=:me AND tenant_id=:t
SHRepo-->>UC: Set<TaskId>
loop 各 task について
    UC->>AuthZ: assertViewable(task, currentUserId, role, stakeholdedTaskIds)
end
```

加えて、実装ガイドラインとして基本設計書 §6.2.1 の末尾に「STAKEHOLDERS 認可判定は事前一括フェッチ必須(N+1回避)」を追記。

## 受け入れ条件

- [ ] sequence-03 が一括フェッチ → ループの2段構成
- [ ] 基本設計書 §6.2.1 に N+1 注意書き

## 参照

PR #76 Claude review (2026-05-10T13:20:40Z) 推奨 #5
```

---

## Issue 25: api(openapi): GET /api/audit-logs の size パラメータに maximum 制約を追加 (R2#6)

**Labels**: `pr-review/76`, `priority/p2`, `area/openapi`

**Body**:
```
## 背景

`GET /api/tasks` の size パラメータには `maximum: 100` があるが、
`GET /api/audit-logs` には上限が無い。監査ログは長期蓄積されるため、
無制限取得はメモリ枯渇・タイムアウトのリスクがある。

## やること

`api/openapi.yaml` の `listAuditLogs.parameters[size]` に `maximum: 100` を追加:

```yaml
- { name: size, in: query, schema: { type: integer, default: 50, maximum: 100 } }
```

エクスポート用途では別エンドポイント(将来 `GET /api/audit-logs/export?from=...&to=...` 等のストリーミング)を検討。

## 参照

PR #76 Claude review (2026-05-10T13:20:40Z) 推奨 #6
```

---

## Issue 26: chore: docs/reviews 配下の運用スクリプト・一時メモの取り扱いを整理 (R2#7 / R3#2 統合)

**Labels**: `pr-review/76`, `priority/p2`, `area/docs`

**Body**:
```
## 背景

下記ファイルは PR #76 作業中に生まれた運用ファイル/一時メモで、レビュアーから2回(R3 #2 / R4 #7)指摘された:

| ファイル | 種類 |
|---|---|
| `docs/reviews/create-issues.sh` | gh CLI 一括 Issue 作成スクリプト(初回ぶん) |
| `docs/reviews/create-issues-pr76.sh` | 同(PR #76 推奨7件ぶん) |
| `docs/reviews/create-issues-pr76-r2.sh` | 同(PR #76 第4回推奨4件ぶん) |
| `docs/reviews/issues-to-create.md` | Issue化候補メモ(初回ぶん) |
| `docs/reviews/issues-to-create-pr76.md` | 同(PR #76 推奨ぶん) |
| `docs/reviews/issues-to-create-pr76-r2.md` | 同(PR #76 第4回ぶん) |
| `docs/reviews/pr-76-review.md` | gh pr view 出力のミラー |

## 方針案

### 案 A(推奨): 運用スクリプトのみ削除、メモは保持
- `*.sh`(3本)→ 実行手順を `issues-to-create-*.md` の末尾にコマンド例として記載 → スクリプト削除
- `issues-to-create-*.md` → トレーサビリティとして保持
- `pr-76-review.md` → GitHub コメントが消えた場合の保険として保持(PRマージ時にアーカイブとしてリネーム)

### 案 B: スクリプトもメモも全削除
- マージ後は GitHub Issues / PR コメントが正本のため、ローカルファイルは不要

### 案 C: docs/reviews/ 配下を .gitignore で除外
- 完成成果物のみ git管理対象とする方針

## 受け入れ条件

- [ ] 方針が議論・確定されている
- [ ] 実行スクリプト(.sh)が「コミットから外す」or「リポジトリから削除」のいずれかになっている

## 参照

PR #76 Claude review (2026-05-10T07:46:18Z) R-2 / (2026-05-10T13:20:40Z) 推奨 #7
```
