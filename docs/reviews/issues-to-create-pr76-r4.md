# 未登録Issueテンプレート(PR #76 第6回レビュー)

**作成日**: 2026-05-10
**対象レビュー**: PR #76 上の Claude 自動レビュー第6回(2026-05-10T23:37:20Z)
**前提**: 必須#1(エンドポイント数 22→24)、推奨#2(sed パターン堅牢化) は PR #76 に追加コミット予定

**Issue 件数**: 1件(他の指摘 #3〜#6 は既存 Issue と重複のため除外)

---

## Issue 31: api(openapi): AuditLog スキーマの tenantId 扱いを明示 (R6#7)

**Labels**: `pr-review/76`, `priority/p2`, `area/openapi`

**Body**:
```
## 背景

DDL の `audit_logs` テーブルには `tenant_id` カラムが定義されている(`tenant_id BIGINT NULL`、システム横断ログでは NULL 許可)。
一方、OpenAPI の `AuditLog` スキーマには対応する `tenantId` プロパティが存在しない。
意図不明のため、設計意図を明示する必要がある。

## 方針候補

| 方針 | 説明 |
|---|---|
| A | レスポンスに `tenantId` を返す → スキーマに追加 |
| B | レスポンスには返さない(API 利用者が見るのは「自分のテナント内ログ」だけだから) → コメントで明示 |

## 推奨: 方針 B

- 監査ログ API (`GET /api/audit-logs`) は Tenant Admin が自テナントの監査ログを参照する想定
- `tenant_id` 列は内部的なテナント分離キーであり、レスポンスでクライアントに返す必要は無い
- スキーマに以下のコメントを追記:

```yaml
AuditLog:
  type: object
  description: |
    監査ログ。Tenant Admin が自テナントのログを参照する用。
    DDL の audit_logs.tenant_id 列はテナント分離キーだが、レスポンスには含めない
    (利用者は常に「自分のテナント内ログ」しか参照しないため意味を持たない)。
  properties:
    ...
```

## 受け入れ条件

- [ ] AuditLog スキーマに方針(A or B)が明示されている
- [ ] 方針B採用なら description にその旨記載
- [ ] 方針A採用なら properties に tenantId 追加(int64 nullable)

## 参照

PR #76 Claude review (2026-05-10T23:37:20Z) 軽微 #7
```
