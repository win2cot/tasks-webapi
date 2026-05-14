# 未登録Issueテンプレート(PR #76 第10回レビュー)

**作成日**: 2026-05-14
**対象レビュー**: PR #76 上の Claude 自動レビュー第10回(2026-05-14T06:45:47Z)
**マージ判定**: 必須相当の指摘3件はすべて既存Issue(#23/#30/#32)で追跡済のため、本PRはマージ実施可能と判断。残課題は Sprint 0 着手前にクローズする。

**Issue 件数**: 2件(新規指摘 #9, #10。#7/#8 は既存 Issue 33/26 と重複)

---

## Issue 36: docs(reviews): gap-analysis 内「API実装(22 endpoints)」を 24 に更新 (R10#9)

**Labels**: `pr-review/76`, `priority/p2`, `area/docs`

**Body**:
```
## 背景

`docs/reviews/2026-05-10-scaffold-vs-design-gap-analysis.md` 内の「API実装(22 endpoints)」記述が、
通知設定API 2件(A-23/A-24)追加後の実態と不整合(実際は 24件)。

`docs/README.md` は既に 24 に更新済、OpenAPI も 24 ops。
本ドキュメントだけ古い表記が残存。

## やること

`docs/reviews/2026-05-10-scaffold-vs-design-gap-analysis.md` の該当行(line 21 付近):

```diff
- | API実装(22 endpoints) | 🔴 未実装 | Controller + UseCase + Repository |
+ | API実装(24 endpoints) | 🔴 未実装 | Controller + UseCase + Repository |
```

## 受け入れ条件

- [ ] 該当箇所が 24 に更新されている
- [ ] 他のドキュメント横断検索で「22 endpoints」「22エンドポイント」が残っていないことを確認

## 参照

PR #76 Claude review (2026-05-14T06:45:47Z) 新規 #9
```

---

## Issue 37: api(openapi): 404 レスポンス定義を $ref 共通化(PATCH /tasks/{id}/status / POST /tasks/{id}/stakeholders) (R10#10)

**Labels**: `pr-review/76`, `priority/p3`, `area/openapi`

**Body**:
```
## 背景

OpenAPI 内で 404 レスポンスは通常 `$ref: "#/components/responses/NotFound"` を使用しているが、
以下2エンドポイントだけはインライン定義になっており、定義のスタイルに一貫性がない。

- `PATCH /api/tasks/{id}/status` の 404
- `POST /api/tasks/{id}/stakeholders` の 404

インライン定義は description で「タスク不在 or 参照権限なし」「タスク不在 or 参照権限なし」と
情報量が `NotFound` より多いため、敢えて使っている。

## 選択肢

### 案 A: `$ref` に統一(他エンドポイントに揃える)

```yaml
"404": { $ref: "#/components/responses/NotFound" }
```

シンプルだが情報量が減る。「タスク不在 or 参照権限なし」ニュアンスを伝えるには
`components.responses.NotFound` 側を強化(description で本ポリシー明記)。

### 案 B: インライン定義をすべて統一(現状維持)

「特定のエンドポイントだけ詳細情報を持たせる」スタイルが意図的なら維持。
他にも詳細情報を持たせる必要があるエンドポイントを洗い出す。

### 推奨

案 A + `components.responses.NotFound` の description を強化:

```yaml
components:
  responses:
    NotFound:
      description: |
        リソース不在、または参照系で参照権限不足(NIST AC-4 / §6.2.3 — リソース存在を漏らさない方針)。
        Tenant 越境アクセスもこれに含まれる。
      content:
        application/json:
          schema: { $ref: "#/components/schemas/Error" }
```

これで個別の inline 定義を削除し、`$ref` に統一可能。

## 受け入れ条件

- [ ] `NotFound` 共通レスポンスを強化 or 案B採用
- [ ] 全 ops の 404 レスポンス定義スタイルが統一されている

## 参照

PR #76 Claude review (2026-05-14T06:45:47Z) 新規 #10
```
