# 未登録Issueテンプレート(PR #76 自動レビュー指摘・推奨/軽微分)

**作成日**: 2026-05-10
**対象レビュー**: PR #76 上の Claude 自動レビュー × 2 回(2026-05-09 / 2026-05-10)
**前提**: 「要確認」「必須」(計6件) は既に PR #76 に追加コミット済(基本設計書 v1.3.1、OpenAPI v1.3.0)

**Issue 件数**: 7件(Stakeholder.addedBy 指摘は既存 Issue m-4 ≡ 必須R2-M3 で対応済のため除外)

利用方法:
- **方法 A**: 同フォルダの `create-issues-pr76.sh` を `gh` CLI で実行(一括作成・推奨)
- **方法 B**: 下記テンプレートを GitHub UI に手動貼り付け

ラベルは `docs/reviews/issues-to-create.md`(初回ぶん)で定義したものを再利用します。新規ラベル `pr-review/76` を加えると追跡しやすいです。

---

## Issue 15: api(openapi): GET /api/tasks の sort パラメータに有効値を明示 (Review1 #5)

**Labels**: `pr-review/76`, `priority/p2`, `area/openapi`

**Body**:
```
## 背景

`GET /api/tasks` の `sort` クエリパラメータは現状 `default: dueDate,asc` のみ示唆されており、
有効なソートフィールドの一覧が API 仕様書に記載されていない。

## やること

`api/openapi.yaml` の `listTasks.parameters[sort]` に description / enum パターンを明記:

```yaml
- name: sort
  in: query
  schema:
    type: string
    default: "dueDate,asc"
    pattern: "^(dueDate|priority|createdAt|updatedAt|title)(,(asc|desc))?$"
  description: |
    並び順。形式 `<field>,<asc|desc>`。有効フィールド: dueDate / priority / createdAt / updatedAt / title。
    省略時は `dueDate,asc`。
```

## 受け入れ条件

- [ ] OpenAPI lint 通過
- [ ] 設計書 §3.3.1 タスク一覧画面の「並び替え」記述と整合

## 参照

PR #76 Claude review (2026-05-09) 軽微 #5
```

---

## Issue 16: docs(spec)+api: visibility=TENANT に戻した時の関係者リスト挙動を明記 (Review1 #6)

**Labels**: `pr-review/76`, `priority/p1`, `area/docs`, `area/openapi`

**Body**:
```
## 背景

シーケンス図 `sequence-05-stakeholder-add.mmd` では、関係者追加時に visibility が STAKEHOLDERS に自動昇格する旨を明示している。
逆方向、すなわち `PATCH /api/tasks/{id}/visibility` で visibility を `TENANT` または `PRIVATE` に戻した場合の
`task_stakeholders` レコードの扱いが、設計書および OpenAPI のどちらにも記載されていない。

## やること

仕様判断:
1. visibility=TENANT に戻したとき: 関係者レコードは保持(再昇格時に流用)/ 削除のいずれか
2. visibility=PRIVATE に戻したとき: 関係者レコードは削除(参照不要のため)が自然
3. 上記方針を **基本設計書 §3.4.4 公開範囲設定** および **OpenAPI `PATCH /api/tasks/{id}/visibility` description** に明記

## 推奨方針(レビュー時の議論用たたき台)

- TENANT 戻し: 関係者レコードを保持(将来 STAKEHOLDERS に戻すと前回登録者がそのまま関係者となる)
- PRIVATE 戻し: 関係者レコードを CASCADE 削除(audit_logs に DELETE_STAKEHOLDER として記録)

## 受け入れ条件

- [ ] 設計書に方針が明示されている
- [ ] OpenAPI description に挙動説明
- [ ] シーケンス図 sequence-05 を visibility 変更フローに対応するよう拡張、または新規 sequence-06 を追加

## 参照

PR #76 Claude review (2026-05-09) 軽微 #6
```

---

## Issue 17: docs(spec): 要件定義書を v1.3 へ更新 — 改訂履歴整合 (Review2 R-1)

**Labels**: `pr-review/76`, `design-review`, `priority/p2`, `area/docs`

**Body**:
```
## 背景

基本設計書は v1.3.1(2026-05-10 時点)まで改訂されているが、要件定義書は v1.2 のまま。
両文書の版番号の対応関係が追跡困難になっている。

## やること

`docs/specs/要件定義書.md` を v1.3 に更新し、以下を改訂履歴に追記:

| 1.3 | 2026-05-10 | 基本設計書 v1.3 / v1.3.1 反映に伴うクロスリファレンス更新。Viewerロール削除を §2.3 で再強調(基本設計書 §4.2.3 の VIEWER 表記削除と整合) | 開発チーム |

実質的な内容変更は無くても、版番号同期の観点で更新する。

## 受け入れ条件

- [ ] 要件定義書 Version が 1.3 に
- [ ] 改訂履歴に v1.3 行が追加されている
- [ ] 基本設計書の "関連文書" セクションが要件定義書 v1.3 を参照している

## 参照

PR #76 Claude review (2026-05-10) 推奨 R-1
```

---

## Issue 18: docs(spec): HTTP 409 の使用シナリオを §5.4 に詳細化 (Review2 R-2)

**Labels**: `pr-review/76`, `design-review`, `priority/p2`, `area/docs`

**Body**:
```
## 背景

OpenAPI では `POST /api/tenant/users/invite` のみが 409 を定義しているが、
基本設計書 §5.4 のエラー応答表では「409 E_CONFLICT: 一意制約違反など」と曖昧。
他の CRUD 操作(重複関係者追加、テナント code 重複等)で 409 が返るケースの方針が不明確。

## やること

`docs/specs/基本設計書.md` § 5.4 に「409 が返却される具体的シナリオ」を追記:

| シナリオ | 該当エンドポイント |
|---|---|
| ユーザー招待時に既に user_tenants に存在 | POST /api/tenant/users/invite |
| ユーザー招待時に oidc_sub が未登録 | POST /api/tenant/users/invite |
| 関係者追加時に既存登録 | POST /api/tasks/{id}/stakeholders |
| テナント code が一意制約違反 | POST /api/tenants |

## 受け入れ条件

- [ ] 基本設計書 §5.4 に上記表が追加されている
- [ ] OpenAPI 該当エンドポイントの 409 description が表と一致

## 参照

PR #76 Claude review (2026-05-10) 推奨 R-2
```

---

## Issue 19: docs/code: 認可ロジックの Single Source of Truth を Domain 層に集約 (Review2 R-3)

**Labels**: `pr-review/76`, `design-review`, `priority/p1`, `area/docs`, `area/security`

**Body**:
```
## 背景

シーケンス図(`sequence-03-task-list-authz.mmd`)と OpenAPI 仕様の両方に
visibility による認可フィルタの詳細が記述されており、仕様変更時の更新漏れリスクがある。

## やること

1. **設計書/シーケンス図側**: 認可フィルタの詳細記述を簡潔化し、「実装は `TaskAuthorizationDomainService` を Single Source of Truth とする」と注記
2. **OpenAPI側**: `description` から認可ロジックの**詳細**を削減し、「詳細は基本設計書 §6.2.1 を参照」とする
3. **実装フェーズで**: `TaskAuthorizationDomainService` のメソッド定義(`canBeViewedBy(...)`, `canBeEditedBy(...)`)を Domain 層に置く

## 受け入れ条件

- [ ] OpenAPI description が短く・参照型に変更
- [ ] 設計書 §6.2.1 が単一の正本として明示
- [ ] 実装着手時に該当 DomainService のシグネチャを別 PR で先行コミット

## 参照

PR #76 Claude review (2026-05-10) 推奨 R-3
```

---

## Issue 20: docs(diagrams): Tenant Admin の visibility フィルタ免除挙動をシーケンス図に明示 (Review2 R-4)

**Labels**: `pr-review/76`, `design-review`, `priority/p1`, `area/docs`

**Body**:
```
## 背景

基本設計書 §6.2.1 では「Tenant Admin は常に参照可」とあるが、
シーケンス図 `sequence-03-task-list-authz.mmd` では Tenant Admin による全件返却フローが明記されていない。
実装者が visibility フィルタを Tenant Admin にも適用するかどうか判断できない状態。

## やること

`sequence-03-task-list-authz.mmd` を更新:

1. ListTasksUseCase が `currentRole == TENANT_ADMIN` の分岐を持つ
2. TENANT_ADMIN の場合、visibility フィルタをスキップ(全件返却、ただし tenant_id 絞込は維持)
3. 監査ログに `LIST_TASKS_AS_ADMIN` 等のアクションを記録(任意)

## 受け入れ条件

- [ ] シーケンス図に Tenant Admin 分岐が描画されている
- [ ] 基本設計書 §6.2.1 / §6.2.2 と挙動が一致

## 参照

PR #76 Claude review (2026-05-10) 推奨 R-4
```

---

## Issue 21: docs(spec): セキュリティ検証の層別責務を §6.2 に明記 (Review2 R-5)

**Labels**: `pr-review/76`, `design-review`, `priority/p2`, `area/docs`, `area/security`

**Body**:
```
## 背景

JWT 検証と `X-Tenant-Id` 検証がどの層で行われるかが設計書に明記されていない。
実装者が同じ検証を複数箇所で重複実装するリスクがある。

## やること

`docs/specs/基本設計書.md` § 6.2 に以下表を追記:

| 検証項目 | 担当 | 詳細 |
|---|---|---|
| JWT 署名検証 | Spring Security フィルタ(`OAuth2ResourceServerConfigurer`) | issuer-uri から JWK セット取得・検証。失敗時 401 |
| JWT クレーム抽出(sub等) | `TasksJwtAuthenticationConverter` | sub → users.oidc_sub と突合 |
| アクティブテナント解決 | `TenantContextFilter`(独自) | `X-Tenant-Id` ヘッダ → user_tenants 照合 |
| テナント越境検出 | Hibernate Filter `tenant_filter` | 全SQLに `WHERE tenant_id = :ctx` 自動付与 |
| メソッド単位ロール認可 | `@PreAuthorize` | `hasRole('TENANT_ADMIN')` 等 |
| タスクごと認可 | `TaskAuthorizationDomainService`(Domain層) | visibility/owner/assignee/stakeholders で参照可否判定 |

## 受け入れ条件

- [ ] 設計書 §6.2 に層別責務表が追加されている

## 参照

PR #76 Claude review (2026-05-10) 推奨 R-5
```

---

## Issue 22: docs(diagrams): Mermaid 図の言語スタイル統一 (Review2 N-1)

**Labels**: `pr-review/76`, `priority/p2`, `area/docs`

**Body**:
```
## 背景

`docs/diagrams/*.mmd` ではクラス名は英語、コメント(`%%` 部分)・ノードラベル・説明は日本語。
Mermaid レンダラーによっては日本語フォント対応が異なり、出力品質にばらつきが出る可能性がある。

## やること

選択肢のいずれか:

1. **Markdown 並行説明方式**: Mermaid 内は技術用語(英語)中心、説明は外側の Markdown に書く
2. **コメント英語化方式**: `%%` ヘッダコメントを英語に統一(本文の日本語ラベルは保持)
3. **そのまま方針確定**: 日本語混在のまま、レビュー対象レンダラー(GitHub / VS Code Mermaid Preview)で目視検証して許容判断

## 推奨

**選択肢 2(コメント英語化)** が変更コスト最小・国際化耐性あり。

## 受け入れ条件

- [ ] 8つの .mmd ファイルでコメント記述が統一されている
- [ ] GitHub と VS Code 上で表示崩れがないことを目視確認

## 参照

PR #76 Claude review (2026-05-10) 軽微 N-1
```
