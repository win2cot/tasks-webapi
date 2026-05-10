# 詳細設計 レビュー報告書

**レビュー日**: 2026-05-06
**対象**: 詳細設計成果物一式(v1.2 ベース)
**レビュアー**: 開発チーム(セルフレビュー)
**目的**: GitHub PR 提出前の整合性・完全性・正確性の検証

---

## サマリ

| 区分 | 件数 |
|---|---|
| ✅ 良好 | 6 |
| 🔴 Critical | 0 |
| 🟠 Major | 3 |
| 🟡 Minor | 5 |
| 🔵 Nit | 2 |
| **合計指摘** | **10** |

**判定**: マージ前に Major × 3 の修正を必須。Minor / Nit は別Issue化して順次対応。

---

## ✅ 良好だった点

| # | 項目 | 確認結果 |
|---|---|---|
| G-1 | API一覧と OpenAPI operations の一致 | 22件 ↔ 22件、Method/Path 完全一致 |
| G-2 | enum値の3点一貫性 | Visibility / Priority / Role の値が OpenAPI / DDL / 設計書で一致 |
| G-3 | Collation 一貫性 | 全7テーブルが `utf8mb4_unicode_ci` で統一 |
| G-4 | FK の ON DELETE 設計 | `task_stakeholders.task_id` CASCADE、その他 RESTRICT 妥当 |
| G-5 | 認可ルールの三点照合 | 設計書 6.2.1 / OpenAPI description / シーケンス図 03〜05 で整合 |
| G-6 | クリーンアーキ層分離 | クラス図と UseCase / Adapter 命名が一貫(`adapter.web` 等のパッケージ命名) |

---

## 🟠 Major 指摘(マージ前に修正必須)

### M-1. DDL に存在するが設計書に記載のないテーブル

**ファイル**: `docs/specs/基本設計書.md` § 4.2、`db/migrations/V1__create_initial_schema.sql`

**現状**:
- DDLに以下の2テーブルがあるが、基本設計書 § 4.2 のテーブル定義に記載なし
  - `user_notification_settings`(ユーザー通知設定)
  - `shedlock`(バッチ排他制御)

**懸念**: 本番運用時の通知設定UIが未設計のまま実装着手するリスク。

**提案**: 設計書 § 4.2 に以下を追記。
- 4.2.7 `user_notification_settings`(PK: user_id+tenant_id、`email_due_today` / `email_overdue` / `email_stakeholder` の3フラグ)
- 4.2.8 `shedlock`(運用テーブル、ShedLock標準スキーマ)

または DDL から `shedlock` を運用マイグレーション(`V99__shedlock.sql` 等)に分離。

---

### M-2. ダッシュボード集計の認可スコープが設計書に未記載

**ファイル**: `docs/specs/基本設計書.md` § 5.x、`api/openapi.yaml`(`GET /api/dashboard/summary`)

**現状**:
- OpenAPI には `description: "認可: テナント内ユーザー(自分が参照可能なタスク範囲で集計)"` と記載
- 設計書には集計範囲の明示がない

**懸念**: 「他人のPRIVATEタスクを件数だけでも数えてしまうか?」という実装判断が分かれる。NIST AC-4(情報フロー)観点で重要。

**提案**: 基本設計書 § 6.2.1 直後に追記。
> ダッシュボード集計は visibility 認可フィルタを適用した後のタスク集合に対して行う。
> 即ち、PRIVATE タスクは所有者のダッシュボードでのみ件数加算される。

---

### M-3. 認可違反時の HTTPステータス戦略(404 vs 403)が設計書に未記載

**ファイル**: `docs/specs/基本設計書.md` § 6.2.1、`api/openapi.yaml`

**現状**:
- OpenAPI 上では実装ずみ:
  - `GET /api/tasks/{id}` 違反時は **404**(リソース存在を漏らさない)
  - `PUT /api/tasks/{id}` 違反時は **403**(編集権限欠如を明示)
  - `DELETE /api/tasks/{id}` 同上 403
- 設計書 § 6.2.1 ではこの戦略が言語化されていない

**懸念**: 実装者が独自判断で 403 一律に倒す可能性。情報漏洩(リソース存在の有無)の経路となる。

**提案**: § 6.2.1 に節を追加:
> ### 認可違反時の応答ポリシー
> - **参照系(GET)で参照不可**: 404 を返す。リソースの存在自体を漏らさない(情報フロー保護)
> - **更新系(PUT/PATCH/DELETE)で権限不足**: 403 を返す。リソース存在は明示しても良い
> - **未認証**: 401
> - **テナント越境アクセス**: 403(必ずログイン中の `X-Tenant-Id` 内で完結することが前提)

---

## 🟡 Minor 指摘(別Issueで段階対応可)

### m-1. ステータス表記マッピング表が無い

設計書では `未着手 / 進行中 / 完了 / 保留`、OpenAPI/DDL では `NOT_STARTED / IN_PROGRESS / DONE / ON_HOLD`。
**提案**: § 4.2.4 直下、または用語集にマッピング表を追記。
| 内部値 | UI日本語 |
|---|---|
| NOT_STARTED | 未着手 |
| IN_PROGRESS | 進行中 |
| DONE | 完了 |
| ON_HOLD | 保留 |

### m-2. ロール表記マッピング表が無い

設計書: `Tenant Admin / Member`、OpenAPI/DDL: `TENANT_ADMIN / MEMBER`。
**提案**: 同様にマッピング表を追記。

### m-3. SaaS Admin の管理方式が設計書に未明示

設計書では `SaaS Admin` を3階層の最上位として扱うが、本システムDBの `user_tenants.role` には存在しない(`TENANT_ADMIN / MEMBER` のみ)。
**実装方式**: Keycloak realm role `APP_ADMIN` で管理、Spring Security で `hasRole('APP_ADMIN')` チェック。
**提案**: 設計書 § 6.2 に「SaaS Admin は Keycloak realm role で管理し、本システムDBには持たない」を追記。

### m-4. OpenAPI `Stakeholder` schema に `addedBy` が無い

DDL の `task_stakeholders` には `added_by`(誰が追加したか)があるが、OpenAPI のレスポンスでは隠蔽されている。
**懸念**: UI で「○○さんが追加」と表示したい場合に不足。
**提案**: `addedBy: { type: object, $ref: "#/components/schemas/UserSummary" }` を追加するか、明示的に「内部用、APIでは返さない」とコメント。

### m-5. `GET /api/tasks/{id}/stakeholders` の認可ルールが設計書未記載

OpenAPI では「タスク参照可能なユーザー」と記載済み。設計書 § 6.2.1 に追記すべき。
**提案**: § 6.2.1 末尾に「関係者一覧の参照: タスク参照可能なユーザー全員」を追加。

---

## 🔵 Nit(些細・任意)

### n-1. FK `fk_tasks_tenant` の ON DELETE 明示なし

`db/migrations/V1__create_initial_schema.sql` の `tasks` テーブルの FK は ON DELETE 未指定(MySQLデフォルト RESTRICT)。
**提案**: 明示的に `ON DELETE RESTRICT` を記述して意図を明確化(運用ミスでテナント物理削除→タスク孤立を防止)。

### n-2. `tasks.status` の DEFAULT が DDL のみで規定されている

DDL: `DEFAULT 'NOT_STARTED'`。OpenAPI / 設計書では「初期ステータスは未着手」と日本語で記述あり。
DDLとOpenAPIで初期値の意味は同じだが、`POST /api/tasks` で `status` を省略可とする旨をOpenAPIで明示してもよい(現状は `TaskCreateRequest.required` に `status` がないので暗黙的に省略可)。

---

## 🔧 修正計画案

| 指摘 | 修正対象ファイル | 工数(目安) |
|---|---|---|
| M-1 | `docs/specs/基本設計書.md` | 30分 |
| M-2 | `docs/specs/基本設計書.md` | 15分 |
| M-3 | `docs/specs/基本設計書.md` | 15分 |
| m-1〜m-5 | `docs/specs/基本設計書.md` / `api/openapi.yaml` | 1時間 |
| n-1, n-2 | `db/migrations/V1__create_initial_schema.sql` / `api/openapi.yaml` | 15分 |

**推奨**: M-1〜M-3 を本PRで修正、Minor / Nit は別Issueで翌スプリントに送る(計画書のレビュープロセス §5.3 に準拠)。

---

## 検証方法

本レポートは下記スクリプトで自動再現可能(整合性チェック):

```bash
# API一覧 ↔ OpenAPI 突合
python3 -c "<上述のscript>"

# DDL ↔ 設計書 § 4.2 テーブル突合
# Collation / FK ON DELETE 一覧
```

将来的には GitHub Actions で本チェックを実行し、PR の CI で自動指摘とすることを推奨(計画書 § 11.2 のCI/CD強化)。

---

## 次アクション

1. 本レビューレポートを GitHub Issue 化(ラベル: `design-review`、`v1.2`)
2. M-1〜M-3 の修正PRを作成 → セルフマージ可
3. m-1〜m-5、n-1〜n-2 を Backlog Issue 化
4. 上記が完了後 Sprint 0 着手判定
