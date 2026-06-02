# OpenAPI × 画面項目 突き合わせ(field-mapping)

Version 1.0 / 2026-06-02 / Issue #153 / 親 Issue #149(Sprint 0 画面設計)

## 改訂履歴

| Version | 日付 | 変更内容 | 著者 |
|---|---|---|---|
| 1.0 | 2026-06-02 | Issue #153 に基づき新規作成。コア 4 画面(S-03 / S-04 / S-05・S-06 / S-13・S-14)の表示・入力項目を `api/openapi.yaml` のスキーマと一対一で突き合わせ、不足項目 5 件・過剰項目を洗い出し。突き合わせ対象は main 現行の **OpenAPI v1.5.0**(Issue 本文の v1.3.1 から進行済み、§0.1 参照) | 開発チーム |

## 0. 本書の位置付け

- 本書は Issue #153 の成果物。`docs/specs/ui/screens/*/decision.md`(#152 で確定したコア 4 画面の採用案)が表示・入力する項目を、`api/openapi.yaml` のスキーマ / operation と **一対一で突き合わせ**、「画面に出したい項目が API に無い(不足)」「API にあるが画面で使わない(過剰)」を網羅的に検出する。
- 本書は実装着手ドキュメントではなく、Sprint 1 で API 実装と画面実装を同時着手するための **項目契約の確定** が目的。
- 不足項目は本書で記録した上で、OpenAPI 更新が必要なものを別 Issue として起票し、Sprint 1 着手前に消化する(§5)。

### 0.1 突き合わせ対象バージョンの注記

- Issue #153 本文は `OpenAPI v1.3.1`(24 operations / 23 schemas)を前提に書かれているが、起票後に main は **v1.5.0**(28 operations / 27 schemas、PR #349 / Issue #334)へ進行している(`PATCH /api/tasks/{id}` 新設 + A-14 PUT 廃止 + `editable`/`deletable`/`completedAt` 追加等)。
- #152 の 4 画面 decision.md はいずれも **v1.5.0 を前提に #153 引き継ぎ表を整備済み**。よって本書も **現行 main の v1.5.0 を正本** として突き合わせる。
- 各 decision.md §3「#153 OpenAPI 突き合わせに引き継ぐ」表が本書の入力。本書はそれらを横断統合し、結論(不足 / 過剰 / 起票要否)を確定する。

### 0.2 突き合わせ対象スキーマ・operation

| 画面 | 主な operation(operationId) | 主な schema |
|---|---|---|
| S-03 ダッシュボード | `getDashboardSummary` / `listTasks` / `createTask` / `changeStatus` / `patchTask` | `DashboardSummary` / `Task` / `TaskPage` / `TaskCreateRequest` / `TaskPatchRequest` |
| S-04 タスク一覧 | `listTasks` / `createTask` / 行内編集系 | `Task` / `TaskPage` / `TaskCreateRequest` |
| S-05・S-06 タスク詳細・編集 | `getTask` / `patchTask` / `changeStatus` / `changeVisibility` / `listStakeholders` / `addStakeholder` / `removeStakeholder` / `deleteTask` | `TaskDetail` / `TaskPatchRequest` / `Stakeholder` / `UserSummary` |
| S-13・S-14 テナント管理 | `listTenants` / `getTenant` / `updateTenant` / `updateTenantStatus` / `getPlatformMetrics` | `Tenant` / `TenantUpdateRequest` / `TenantStatusUpdateRequest` / `PlatformMetrics` |

### 0.3 enum 一致確認(共通)

| enum | OpenAPI v1.5.0 の値 | 画面側で参照する値 | 一致 |
|---|---|---|---|
| `TaskStatus` | `NOT_STARTED` / `IN_PROGRESS` / `DONE` / `ON_HOLD` | 同左(ステータスプルダウン) | ✓ |
| `Priority` | `HIGH` / `MEDIUM` / `LOW` | 同左(優先度別カード / 行内編集) | ✓ |
| `Visibility` | `TENANT` / `STAKEHOLDERS` / `PRIVATE` | 同左(visibility バッジ 3 色) | ✓ |
| テナント状態 | `ACTIVE` / `SUSPENDED` | 同左(状態バッジ緑 / 赤) | ✓ |

## 1. S-03 ダッシュボード

SSOT: `docs/specs/ui/screens/dashboard/decision.md`(案 A 集約縦積み)/ `screen-flow.md` §5.1

### 1.1 数値カード(5 枚)→ `getDashboardSummary` → `DashboardSummary`

| 画面の表示項目 | OpenAPI フィールド | 充足 |
|---|---|---|
| 当日の期限件数 | `DashboardSummary.todayDueCount` | ✓ |
| 期限超過(未完了)件数 | `DashboardSummary.overdueCount` | ✓ |
| 本日完了件数 | `DashboardSummary.completedTodayCount` | ✓ |
| 自分の進捗(完了 / 進行中 / 未着手) | `DashboardSummary.myOpenCount` + `statusBreakdown`(object) | ✓ |
| 優先度別件数(高 / 中 / 低) | `DashboardSummary.priorityBreakdown`(object) | ✓ |

### 1.2 「当日対応 + 振り返り」リスト 4 セクション → `listTasks` → `Task`

行に表示する項目はすべて `Task` で充足する。一方、**4 セクションを 1 回で取得するためのクエリ** は不足(→ FM-G4)。

| 画面の表示項目(各行) | OpenAPI フィールド(`Task`) | 充足 |
|---|---|---|
| タイトル | `title` | ✓ |
| 期限 | `dueDate`(date) | ✓ |
| 担当者(名前) | `assignee`(`UserSummary` = id, fullName、nullable) | ✓ |
| 優先度 | `priority` | ✓ |
| ステータス | `status` | ✓ |
| 公開範囲バッジ | `visibility` | ✓ |
| 説明(ポップオーバー) | `description`(nullable) | ✓ |
| (4) 今日やったこと の完了時刻 | `completedAt`(date-time, nullable) | ✓ |
| 行内編集の可否(`cell-locked`) | `editable`(boolean) | ✓ |
| 4 セクション一括取得クエリ(`?ownerOrAssignee=me&dashboardScope=today` 相当) | **無し** | ✗ → **FM-G4** |

### 1.3 + 新規ドロワー(7 フィールド)→ `createTask` → `TaskCreateRequest`

| 画面の入力項目 | OpenAPI フィールド(`TaskCreateRequest`) | 充足 |
|---|---|---|
| タイトル | `title`(required) | ✓ |
| 説明 | `description`(nullable) | ✓ |
| 期限 | `dueDate`(required) | ✓ |
| 担当者 | `assigneeId`(nullable) | ✓ |
| 優先度 | `priority`(required) | ✓ |
| 公開範囲 | `visibility`(required) | ✓ |
| 関係者 | `stakeholderUserIds`(array) | ✓ |

### 1.4 行内編集 6 項目 → `patchTask`(A-29)/ `changeStatus`(A-16)

| 行内編集項目 | API | リクエストフィールド | 充足 |
|---|---|---|---|
| ステータス | `changeStatus`(A-16) | `status` | ✓ |
| 期限 | `patchTask`(A-29) | `TaskPatchRequest.dueDate` | ✓ |
| 担当者 | `patchTask`(A-29) | `TaskPatchRequest.assigneeId` | ✓ |
| 優先度 | `patchTask`(A-29) | `TaskPatchRequest.priority` | ✓ |
| タイトル | `patchTask`(A-29) | `TaskPatchRequest.title` | ✓ |
| 説明 | `patchTask`(A-29) | `TaskPatchRequest.description` | ✓ |

> 担当者プルダウンの候補は `listTenantUsers`(`TenantUser`)で取得可能。

## 2. S-04 タスク一覧

SSOT: `docs/specs/ui/screens/task-list/decision.md`(案 B 左サイドカレンダー)/ `screen-flow.md` §5.2

### 2.1 行表示・新規ドロワー・行内編集

S-03 と同じ `Task` / `TaskCreateRequest` / `TaskPatchRequest` を共有し、**項目はすべて充足**(§1.2 / §1.3 / §1.4 と同一)。一覧は `TaskPage`(`content` 配列 + `totalElements` / `totalPages` / `number` / `size` + `overdueCount`)でページング・期限切れ件数も充足。

### 2.2 上部フィルタバー・ソート → `listTasks` parameters

| 画面のフィルタ / ソート | `listTasks` parameter | 充足 |
|---|---|---|
| 表示対象日(カレンダー選択日) | `targetDate`(date) | ✓ |
| 期限切れ常時表示 | `includeOverdue`(boolean, default true) | ✓ |
| ステータス(複数選択) | `status`(array, カンマ区切り) | ✓ |
| 所有者 | `ownerId`(integer) | ✓ |
| 担当者 | `assigneeId`(integer) | ✓ |
| 優先度 | `priority` | ✓ |
| 公開範囲 | `visibility` | ✓ |
| キーワード(タイトル / 説明部分一致) | `keyword`(string) | ✓ |
| ページング | `page`(0 始まり) / `size`(default 50, max 100) | ✓ |
| ソート | `sort`(`<field>,<asc\|desc>`, default `dueDate,asc`) | ✓ |

### 2.3 左サイドカレンダーの日別件数バッジ

| 画面の表示項目 | OpenAPI | 充足 |
|---|---|---|
| 月単位の日別タスク件数(期限切れ / 当日内訳) | 月別集計 endpoint 無し | ✗ → **FM-G5** |

> 注: `sort` は単一キーのみ(複合ソート未対応)。S-04 で「優先度 DESC → 期限 ASC」の複合ソートが必要かは Phase 2 で再評価(本書では不足扱いにしない=MVP は単一キーで足りる)。

## 3. S-05・S-06 タスク詳細・編集

SSOT: `docs/specs/ui/screens/task-detail/decision.md`(案 B 2 カラム)/ `screen-flow.md` §5.3

### 3.1 表示項目 → `getTask`(A-12)→ `TaskDetail`

| 画面の表示項目 | OpenAPI フィールド(`TaskDetail`) | 充足 |
|---|---|---|
| タイトル | `title` | ✓ |
| 説明 | `description`(nullable) | ✓ |
| ステータス | `status` | ✓ |
| 優先度 | `priority` | ✓ |
| 期限 | `dueDate` | ✓ |
| 公開範囲バッジ | `visibility` | ✓ |
| 作成日 / 更新日 | `createdAt` / `updatedAt` | ✓ |
| 完了日時(完了時のみ) | `completedAt`(nullable) | ✓ |
| 楽観ロック用 ETag | レスポンスヘッダ `ETag`(200) | ✓ |
| 所有者(名前 + canX 認可判定の基点) | `owner`(`UserSummary` = id, fullName) | ✓ |
| 担当者(名前) | `assignee`(`UserSummary` nullable) | ✓ |
| 編集可否フラグ `editable` | `editable`(boolean、所有者のみ true、ADR-0005) | ✓ |
| 削除可否フラグ `deletable` | `deletable`(boolean、所有者のみ true、ADR-0005) | ✓ |

### 3.2 関係者リスト → `listStakeholders` → `Stakeholder`

| 画面の表示項目 | OpenAPI フィールド(`Stakeholder`) | 充足 |
|---|---|---|
| 関係者の名前 chip | `fullName` | ✓ |
| 関係者 id(削除操作用) | `userId` | ✓ |
| アバター画像 | 無し(Phase 2、#154 でトークン化) | 対象外(MVP は頭文字アバター) |

### 3.3 編集・操作 → `patchTask` / `changeStatus` / `changeVisibility` / stakeholders / `deleteTask`

| 操作 | API | 認可 | 充足 |
|---|---|---|---|
| 部分更新(タイトル / 説明 / 期限 / 担当者 / 優先度) | `patchTask`(A-29, `If-Match` 必須) | 所有者 | ✓ |
| ステータス変更 | `changeStatus`(A-16) | 所有者 ∪ 担当者 | ✓ |
| 公開範囲変更 | `changeVisibility`(A-17) | 所有者 | ✓ |
| 関係者追加 / 削除 | `addStakeholder`(A-19)/ `removeStakeholder`(A-20) | 所有者 ∪ 担当者 | ✓ |
| 論理削除 | `deleteTask`(A-15) | 所有者 | ✓ |

## 4. S-13・S-14 テナント管理

SSOT: `docs/specs/ui/screens/tenant-admin/decision.md`(案 B 管理コンソール風)/ `screen-flow.md` §3

### 4.1 KPI ストリップ → `getPlatformMetrics`(A-27)→ `PlatformMetrics`

| 画面の表示項目 | OpenAPI フィールド | 充足 |
|---|---|---|
| 総テナント数 | `PlatformMetrics.totalTenants` | ✓ |
| ACTIVE 数 | `PlatformMetrics.activeTenants` | ✓ |
| SUSPENDED 数 | `PlatformMetrics.suspendedTenants` | ✓ |
| 直近 24h 新規 | `PlatformMetrics.newTenantsLast24h` | ✓ |

### 4.2 S-13 テナント一覧 → `listTenants`(A-04)→ `Tenant`

| 画面の表示項目 / 機能 | OpenAPI | 充足 |
|---|---|---|
| テナント名 | `Tenant.name` | ✓ |
| 状態バッジ(ACTIVE / SUSPENDED) | `Tenant.status` | ✓ |
| 作成日 | `Tenant.createdAt` | ✓ |
| **所属ユーザー数** | **無し**(`Tenant` に `userCount` 不在) | ✗ → **FM-G3** |
| **タスク数** | **無し**(`Tenant` に `taskCount` 不在) | ✗ → **FM-G3** |
| **状態フィルタ(ACTIVE / SUSPENDED / すべて)** | **無し**(`listTenants` に parameter 不在) | ✗ → **FM-G2** |
| **名前検索(部分一致)** | **無し**(同上) | ✗ → **FM-G2** |
| **ページング(50 件 / ページ)** | **無し**(`listTenants` は `Tenant` 配列を直返し、Page ラッパー無し) | ✗ → **FM-G2** |

### 4.3 S-14 テナント詳細・状態切替 → `getTenant`(A-25)/ `updateTenant`(A-06)/ `updateTenantStatus`(A-26)

| 画面の表示項目 / 操作 | OpenAPI | 充足 |
|---|---|---|
| テナント名(大表示 + inline 編集) | `Tenant.name` / `updateTenant`(`TenantUpdateRequest`) | ✓ |
| 状態バッジ | `Tenant.status` | ✓ |
| 作成日 / 更新日 | `Tenant.createdAt` / `Tenant.updatedAt` | ✓ |
| 状態切替(ACTIVE ↔ SUSPENDED) | `updateTenantStatus`(`TenantStatusUpdateRequest`) | ✓ |
| **所属ユーザー数** | **無し** | ✗ → **FM-G3** |
| **タスク数** | **無し** | ✗ → **FM-G3** |

## 5. 不足項目リスト(画面の要望 → OpenAPI 追加・変更案)

| ID | 重要度 | 対象画面 | 不足内容 | 追加・変更案 | client 回避可否 |
|---|---|---|---|---|---|
| ~~**FM-G1**~~ | ~~必須~~ | ~~S-05 / S-06~~ | ~~`getTask`(`TaskDetailPartial`)に `owner` / `assignee` / `editable` / `deletable` が無い~~ | `TaskDetailPartial` を `TaskDetail` に昇格。`owner`(UserSummary)/ `assignee`(UserSummary nullable)/ `editable` / `deletable` を追加 (#354) | **解消済み** |
| **FM-G2** | 必須 | S-13 | `listTenants`(A-04)に **状態フィルタ / 名前検索 / ページング** の parameter が無く、レスポンスも `Tenant` 配列直返しで Page 構造が無い | `status` / `keyword` / `page` / `size` parameter を追加し、レスポンスを `TenantPage`(`content` + `totalElements` + `totalPages` + `number` + `size`)に変更 | 不可(全件取得 + client 絞り込みはテナント数百規模で非現実的) |
| **FM-G3** | 必須 | S-13 / S-14 | `Tenant` スキーマに **`userCount` / `taskCount`** が無く、一覧の所属ユーザー数 / タスク数列・詳細メトリクスが取得不可 | `listTenants` / `getTenant` 用に集計列付きスキーマ(例 `TenantSummary` に `userCount` / `taskCount`)を新設、または `Tenant` に両フィールドを追加 | 不可(集計は server 側が効率的) |
| **FM-G4** | 要方針 | S-03 | ダッシュボード 4 セクションを 1 回で取るための **`ownerOrAssignee=me`(owner OR assignee の論理和)/ これから(+N 日範囲)/ 本日完了(`completedAt` = 当日)** が `listTasks` に無い(現状は `ownerId` と `assigneeId` が AND、`targetDate` は単日、完了は `status=DONE` 全件)。`screen-flow.md` §5.1 が要求する `?ownerOrAssignee=me&dashboardScope=today` 相当が表現不能 | `listTasks` に `dashboardScope` / `ownerOrAssignee` / `dueWithinDays` を追加、または専用 `GET /api/dashboard/tasks` を新設(4 セクション分を 1 レスポンスで返す) | (3) これからは複数回呼びで代替可だが、(4) 本日完了は `status=DONE` 全件取得になりコスト過大 → 実質不可 |
| **FM-G5** | 要方針 | S-04 | 案 B 左サイドカレンダーの **月単位日別件数バッジ** を生成する集計 endpoint が無い | `GET /api/tasks/calendar?month=YYYY-MM` 相当の日別件数集計を新設。MVP では当月 `listTasks` から client 派生集計で妥協する選択肢もあり(件数規模次第) | 当月分の全件取得 + client 集計で暫定可(件数次第で要再評価) |

### 5.1 不足ではないが Sprint 1 で要確認の観察事項

- **O1(楽観ロックの一貫性)**: `changeStatus`(A-16)/ `changeVisibility`(A-17)には `If-Match` / `ETag` が無く、楽観ロックは `patchTask`(A-29)のみ。複数経路(行内編集 / ステータスのみ変更 / ドロワー編集)が同一タスクへ並行する際の衝突一貫性を Sprint 1 実装時に再確認(ADR-0012 適用範囲の明確化)。本書では不足扱いにしない。
- **O2(`sort` 単一キー)**: S-04 の複合ソート要件は MVP では発生しない判断(§2.3 注)。Phase 2 候補。

## 6. 過剰項目リスト(API にあるが画面未使用)

コア 4 画面で使用しないフィールド。削除提案ではなく「画面が使わない」ことの明示(将来画面 / 内部用途で必要なものを含む)。

| schema.field | 状況 |
|---|---|
| `Tenant.code` | S-13 / S-14 で未表示(内部識別子、表示不要) |
| `Tenant.plan`(FREE / STANDARD / ENTERPRISE) | プラン管理は Phase 2、コア 4 画面で未使用 |
| `TaskDetail.tenantId` | 内部識別、画面未表示 |
| `Stakeholder.email` / `addedBy` / `addedAt` | 関係者 chip は名前のみ(MVP)。誰がいつ追加したかは未表示 |
| `PlatformMetrics.totalUsers` / `totalTasks` | KPI ストリップ 4 枚に未使用(S-12 プラットフォーム監視で再利用予定) |
| `TenantUser.departmentName` | コア 4 画面外(S-08 ユーザー管理) |

### 6.1 コア 4 画面の対象外スキーマ(突き合わせ対象外)

以下は #149 のコア 4 画面スコープ外の画面に属するため本書では突き合わせない: `MeResponse` / `UserProfile`(S-01・S-02 認証)、`NotificationSettings`(通知設定)、`AuditLog`(監査ログ参照画面)、`TenantDashboardSummary`(S-15 テナント運営者ダッシュボード)、`TenantUser` / `UserInviteRequest`(S-08 ユーザー管理)、`TenantCreateRequest` / `TenantCreatedResponse`(S-11 セルフサインアップ)、`Role`(認可内部 enum)。

## 7. 完了の定義との対応

| Issue #153 完了の定義 | 状況 |
|---|---|
| `docs/specs/ui/field-mapping.md` を PR で追加、マージ | 本書(本 PR で追加) |
| コア 4 画面すべてマッピング済み | §1〜§4 で 4 画面完了 |
| 不足項目が 0 件 or OpenAPI 更新の追加 Issue として起票済み | 不足 4 件(FM-G2〜G5)。FM-G1 は #354 で解消済み。残りは OpenAPI 更新 Issue として起票(§8) |
| OpenAPI 側更新が必要なら別 Issue を起票し Sprint 0 Readiness 系の枠で消化 | §8 の起票で対応(#120 は close 済のため親 #149 系に紐付け) |

## 8. 派生 Issue(OpenAPI 更新)

不足項目 FM-G1〜G5 を OpenAPI 更新 Issue として起票済み(親トラッカ **#359** 配下、Milestone: Phase 1、Sprint 1 の API 実装着手前に消化)。

| ID | Issue | 重要度 |
|---|---|---|
| ~~FM-G1~~ | ~~#354 getTask レスポンス拡張(owner/assignee/editable/deletable)~~ | ~~必須~~ → **解消済み** |
| FM-G2 | #357 listTenants フィルタ/検索/ページング + TenantPage | 必須 |
| FM-G3 | #358 Tenant に userCount/taskCount | 必須 |
| FM-G4 | #355 ダッシュボード4セクション取得クエリ(両論併記) | 要方針 |
| FM-G5 | #356 カレンダー日別件数バッジ(両論併記) | 要方針 |

## 9. 関連

- 親 Issue: #153(本書)/ #149(Sprint 0 画面設計)
- 入力 SSOT: `docs/specs/ui/screens/{dashboard,task-list,task-detail,tenant-admin}/decision.md`(#152)/ `docs/specs/ui/screen-flow.md` §5(#150)/ `docs/specs/ui/access-matrix.md`(#151)
- API 契約: `api/openapi.yaml` v1.5.0
- 関連 ADR: ADR-0005(タスク認可 3 役割)/ ADR-0012(楽観ロック ETag / If-Match)/ ADR-0014(JsonNullable for PATCH)
- 関連 Issue: #279(`created_by` / `updated_by` DB 列、FM-G1 とは別)/ #334(OpenAPI v1.5.0)
