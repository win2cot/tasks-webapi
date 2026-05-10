# 未登録Issueテンプレート(PR #76 第5回レビュー・推奨/軽微分)

**作成日**: 2026-05-10
**対象レビュー**: PR #76 上の Claude 自動レビュー第5回(2026-05-10T17:54:47Z)
**前提**: 第5回レビューの **必須3件**(#1 AuditLog 重複削除 / #2 audit-logs size maximum / #3 仕様書3ファイルの実行権限) は PR #76 に追加コミット予定

**Issue 件数**: 4件(うち #5・#9 は既存Issueと重複のため除外)

---

## Issue 27: docs+api: エンドポイント件数の不整合 (22 vs 24) を是正 (R5#4)

**Labels**: `pr-review/76`, `priority/p2`, `area/docs`, `area/openapi`

**Body**:
```
## 背景

通知設定 API 2件 (A-23, A-24) を後から追加したため、複数箇所で件数が不一致:

- `api/openapi.yaml` `info.description`: 「22エンドポイント」と記載
- `docs/README.md`: 「OpenAPI 3.1 (22 endpoints)」と記載
- `docs/specs/基本設計書.md` §5.1: A-01〜A-24(24件)
- 実際の operations 数: 24件

## やること

1. `api/openapi.yaml` の info.description を「24 エンドポイント」に修正(該当箇所が存在する場合)
2. `docs/README.md` の「(22 endpoints)」を「(24 endpoints)」に修正
3. PR description「OpenAPI 3.1 (22 endpoints)」も同様に更新

## 受け入れ条件

- [ ] 設計書 / OpenAPI / README で件数表記が「24」で統一
- [ ] エンドポイント数の整合性スクリプト(将来 CI 化)で検証

## 参照

PR #76 Claude review (2026-05-10T17:54:47Z) 推奨 #4
```

---

## Issue 28: api(openapi): DELETE /api/tasks/{id}/stakeholders/{userId} に 404 を追加 (R5#6)

**Labels**: `pr-review/76`, `priority/p2`, `area/openapi`

**Body**:
```
## 背景

`DELETE /api/tasks/{id}/stakeholders/{userId}` のレスポンスは現状 `204 / 401 / 403` のみで、
タスク不在 or 指定ユーザーが関係者でない場合の `404` が未定義。

## やること

`api/openapi.yaml` 該当エンドポイントの responses に追加:

```yaml
"404": { $ref: "#/components/responses/NotFound" }
```

## 受け入れ条件

- [ ] DELETE 関係者削除に 404 が定義されている
- [ ] 全 ops で削除エンドポイントの 404 統一

## 参照

PR #76 Claude review (2026-05-10T17:54:47Z) 推奨 #6
```

---

## Issue 29: api(openapi): TenantSummary / MeResponse / Tenant に required を追加 (R5#7)

**Labels**: `pr-review/76`, `priority/p2`, `area/openapi`

**Body**:
```
## 背景

OpenAPI Generator でクライアントコード生成時、`required` 配列が無いスキーマは全フィールドが Optional になる。
`UserProfile` や `TaskCreateRequest` には required があるのに、テナント関連3スキーマには未定義。

## やること

`api/openapi.yaml` に下記の `required:` を追加:

```yaml
TenantSummary:
  required: [id, code, name, role]
MeResponse:
  required: [user, tenants]
Tenant:
  required: [id, code, name, plan, status, createdAt, updatedAt]
```

## 受け入れ条件

- [ ] 上記3スキーマに required 配列が追加されている
- [ ] 他スキーマ(Task / TenantUser 等)も併せて整合性確認

## 参照

PR #76 Claude review (2026-05-10T17:54:47Z) 推奨 #7
```

---

## Issue 30: api(openapi): PUT /api/tenant/users/{userId}/role の 200 にスキーマを追加 (R5#8)

**Labels**: `pr-review/76`, `priority/p2`, `area/openapi`

**Body**:
```
## 背景

`PUT /api/tenant/users/{userId}/role` のレスポンスは `"200": { description: OK }` のみで、ボディスキーマが不明。
他のロール変更系エンドポイントとの一貫性のため、明示すべき。

## やること

下記いずれかの方針で更新:

1. **更新後の TenantUser を返す方針**:
   ```yaml
   "200":
     description: OK
     content:
       application/json:
         schema: { $ref: "#/components/schemas/TenantUser" }
   ```
2. **No Content にする方針**: `"204": { description: No Content }` に変更

`PATCH /tasks/{id}/visibility` (Issue 23) も同様に判断。両者で方針統一。

## 受け入れ条件

- [ ] 200 のスキーマ or 204 への変更が反映されている
- [ ] Issue 23 と方針が一致

## 参照

PR #76 Claude review (2026-05-10T17:54:47Z) 軽微 #8
```
