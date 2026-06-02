# ADR-0013: 監査ログ差分粒度は UseCase 層で field-by-field diff を計算し既存 `audit_logs.detail` に記録する

- **Status**: Accepted
- **Date**: 2026-06-01
- **Deciders**: win2cot
- **Tags**: audit, persistence, security

## 目次

- [1. コンテキスト(Context)](#1-コンテキストcontext)
- [2. 検討した選択肢(Options Considered)](#2-検討した選択肢options-considered)
  - [選択肢 A: Hibernate Envers(エンティティ単位の自動 version 化テーブル)](#選択肢-a-hibernate-enversエンティティ単位の自動-version-化テーブル)
  - [選択肢 B: 自前 audit_logs + UseCase 層で field-by-field diff を計算](#選択肢-b-自前-audit_logs--usecase-層で-field-by-field-diff-を計算)
  - [選択肢 C: Spring AOP + アノテーションで diff 計算を declarative 化](#選択肢-c-spring-aop--アノテーションで-diff-計算を-declarative-化)
  - [選択肢 D: アプリケーションログ(構造化ログ)に diff を出力するのみ](#選択肢-d-アプリケーションログ構造化ログに-diff-を出力するのみ専用テーブルは持たない)
- [3. 決定(Decision)](#3-決定decision)
- [4. 理由(Rationale)](#4-理由rationale)
- [5. 影響(Consequences)](#5-影響consequences)
  - [良い影響(Positive)](#良い影響positive)
  - [悪い影響・制約(Negative)](#悪い影響制約negative)
  - [既存ドキュメント・規約への波及](#既存ドキュメント規約への波及)
- [6. 実装メモ(Implementation Notes)](#6-実装メモimplementation-notes)
- [7. 参考リンク(References)](#7-参考リンクreferences)

## 1. コンテキスト(Context)

Issue #329 で `PATCH /api/tasks/{id}` 新設 + A-14 `PUT` 廃止が確定し、行内編集 6 項目(ステータス / 期限 / 担当者 / 優先度 / タイトル / 説明)を任意組み合わせで部分更新できる仕様に切り替わる。PUT 全項目置換と異なり、リクエストペイロードだけでは「変更があった項目」を判定できない(クライアントは `JsonNullable<T>` で "undefined(未指定)" と "null(明示的に空値設定)" を区別して送るため、ADR-0014 候補・Issue #331)。**サーバ側で旧値と新値を field 単位で比較し、変更があった項目だけを監査ログに記録する必要がある**。

監査ログの基盤は既に確定済みであり、本 ADR で **新規に設計するのは差分計算と書き込みの仕組みのみ**:

- 基本設計書 v1.4.x §4.2.6 `audit_logs` テーブル定義: `id` / `tenant_id` / `user_id` / `actor_sub` / `action` / `entity_type` / `entity_id` / **`detail JSON`(変更内容 = 差分)** / `ip_address` / `hash_chain` / `created_at`(11 列)
- 基本設計書 §6.7 / 設計規約 §4.3: `audit_logs.hash_chain` は前レコードの `id + detail + created_at` の SHA-256。日次整合性チェックバッチ(B-05)で改ざん検知
- 基本設計書 §7.2 / §8.1: 監査ログは **DB(`audit_logs` テーブル)**保管 1 年、B-03 日次バッチで 1 年超のレコードを削除(NIST AU-2 / AU-10 / AU-11 整合)
- `action` 標準値は 20 種以上が確定済(`CREATE` / `UPDATE` / `DELETE` / `VISIBILITY_CHANGED` / `STAKEHOLDER_PURGED` / 認可違反系 8 値 + ログイン系 + テナント運営系)
- ADR-0004(`tenants` の `created_by` / `updated_by` 例外)で「誰が何をしたか」は `actor_sub` で追跡する方針が確定済

つまり「監査ログを記録するか否か」「保存先 DB vs S3 vs CloudWatch」「保管期間」「改ざん検知」「テーブル定義」は全て決着済で、**本 ADR が決めるのは "PATCH リクエストから `detail JSON` に入れる差分の生成方法と粒度"** に絞られる。Sprint 0 着手(2026-07-14 予定)前に方式確定し、Issue #329 派生 4(#334 OpenAPI 反映 + 実装)で実コードを書く前提を整えたい。

関連: Issue #333(本 ADR 起票元) / Issue #329(PATCH 化確定) / Issue #331(`JsonNullable<T>` ADR 候補) / Issue #332(ETag / If-Match ADR、ADR-0012) / ADR-0008(GraalVM Native Image) / ADR-0010(Hibernate Filter) / ADR-0011(独自 `record ErrorResponse`)。

## 2. 検討した選択肢(Options Considered)

### 選択肢 A: Hibernate Envers(エンティティ単位の自動 version 化テーブル)

- 概要: JPA エンティティに `@Audited` を付与し、Hibernate Envers が `<entity>_AUD` テーブル + `revinfo` テーブルを自動生成して、エンティティ全体のスナップショットを revision 単位で保存する。差分は revision 間の比較で取得。
- 利点:
  - Entity に `@Audited` を貼るだけで自動記録される(コード量最小)
  - Envers の `AuditQuery` で「revision 間の項目差分」「特定 revision のエンティティ全体復元」が API で得られる
  - revision メタデータ(タイムスタンプ・ユーザー)は別テーブル(`revinfo`)で正規化される
- 欠点:
  - **既存の `audit_logs` テーブル(11 列確定済 + hash_chain 改ざん検知 + 1 年保管バッチ B-03/B-05)と二重管理**になる。Envers の `<entity>_AUD` は別スキーマで、`detail` JSON への落とし込みが必要(結局自前変換が要る)
  - **エンティティ全体のスナップショットを毎 revision で複製**する。`tasks` テーブルで 1 項目だけ変えた行内編集でも、`title` / `description` / `status` 等の全カラムが `tasks_AUD` に複製され、ストレージコストが PATCH 差分の数倍〜数十倍に膨らむ
  - **Hibernate Filter(ADR-0010 `tenantFilter`)が `<entity>_AUD` テーブルに自動適用されない**(Envers は filter 非対応の経路で query を組み立てる)。テナント越境の自動絞り込み(NIST AC-4)を独自に補強する必要があり、ADR-0010 の前提が崩れる
  - **改ざん検知ハッシュチェーン(§4.3)が Envers では未提供**。`revinfo` に hash 列を後付けする実装コストは「自前テーブル + hash チェーン」とほぼ同じ
  - GDPR / テナント解約(#167 Phase 2)時の cascade 削除は、`<entity>_AUD` 各テーブル + `revinfo` 全てから対象 tenant_id 行を物理削除する後処理が要る
  - `action` 標準値(`VIEW_DENIED` / `EDIT_DENIED` 等の認可違反系 8 値)は Envers のモデルに乗らない(Envers は「成功した CUD 操作」のみ対象)
- リスク・未知数: GraalVM Native Image(ADR-0008)下での Envers リフレクションヒント整備の作業量は未検証(Envers は proxy / reflection を多用)。

### 選択肢 B: 自前 audit_logs + UseCase 層で field-by-field diff を計算

- 概要: 既存 `audit_logs` テーブル(基本設計書 §4.2.6 確定済)をそのまま使い、各 write 系 UseCase(`UpdateTaskUseCase` 等)で「旧エンティティを読み込み → DTO の `JsonNullable<T>` から PATCH 対象項目を抽出 → 旧値と新値を比較 → 変更があった項目のみ `detail JSON` に `[{ "field": "due_date", "old": "2026-07-01", "new": "2026-07-05" }, ...]` の配列で記録 → `audit_logs` に INSERT」をトランザクション内で実施。差分計算は Domain 層に純粋関数として置く(例: `TaskAuditDiffDomainService.diff(Task old, TaskPatchCommand cmd) : List<FieldChange>`)。
- 利点:
  - **既存 `audit_logs` テーブル / `hash_chain` / B-03 / B-05 バッチをそのまま流用**できる。新規 DDL 不要。SSOT が `audit_logs` 1 箇所に集約される
  - **PATCH 差分のみが記録される**ため、ストレージコストが最小。`detail JSON` の各エントリは変更があった項目分のみ
  - Hibernate Filter(ADR-0010)は `audit_logs` テーブルにも自然に適用され、テナント越境(NIST AC-4)が一律で機械的に保証される(`audit_logs.tenant_id IS NULL` の例外 = SaaS Admin 操作・テナント横断バッチは個別 disableFilter)
  - **GraalVM Native Image(ADR-0008)親和性が最も高い**。reflection は Jackson の JSON シリアライズのみで、proxy / AOP 不要
  - **認可違反系 `*_DENIED` action(8 値)も同じ仕組みで記録**できる(`detail` に拒否理由 + 試行された変更内容)。Envers / AOP の枠組みでは扱えない
  - GDPR / テナント解約時の cascade 削除は `audit_logs.tenant_id = ?` の単一 DELETE で完結(#167 Phase 2 設計と整合)
  - 差分計算ロジックが Domain 層の純粋関数として独立し、ユニットテストで網羅検証できる
- 欠点:
  - 各 write 系 UseCase で「旧値読込 → diff 計算 → 監査ログ書込」のテンプレートが必要(declarative 化なし)。コード量は AOP 案より増える
  - 差分計算ロジックは entity ごとに個別実装(Task 用 / Tenant 用 / UserTenant 用)。共通基盤化(リフレクションでフィールド走査)を入れると ADR-0008 制約と再衝突するため、手書きの diff 関数を許容する
  - `detail JSON` のスキーマ規約(`field` 名は DB カラム名 vs DTO プロパティ名のどちらに揃えるか、`old` / `new` の型表現)を別途明文化が要る → 設計規約 §4.3 への追記項目
- リスク・未知数: なし。

### 選択肢 C: Spring AOP + アノテーションで diff 計算を declarative 化

- 概要: `@Auditable(entity = "task", action = "UPDATE")` のような独自アノテーションを UseCase メソッドに付与し、`@Around` Aspect が引数とリポジトリの旧値を読んで自動で差分計算 + `audit_logs` INSERT を行う。Reflection で entity のフィールドを走査して比較。
- 利点:
  - UseCase 側のコードは `@Auditable` 1 行のみで済む。declarative
  - 横断的関心(監査ログ)を AOP で集約できる
- 欠点:
  - **GraalVM Native Image(ADR-0008)との相性が悪い**。Spring AOP は CGLIB / JDK proxy 依存で、`reflect-config.json` / `proxy-config.json` の追加運用と debug 困難さが Sprint 0 早期に必ず障害となる(ADR-0010 で同じ理由により Spring AOP を退けた経緯あり)
  - **Hibernate Filter(ADR-0010)で SELECT 経路を自動絞り込みする方針と非対称**。同じ「横断的にデータアクセスに挿す」関心を、片や Hibernate Filter / 片や Spring AOP と 2 系統で持つことは設計上の一貫性を損なう
  - **エンティティ関連ナビゲーション(lazy loading)時の旧値取得経路を AOP は触れない**。`task.subTasks` のような派生フィールドの差分は AOP 単独では検出できず、UseCase 側で別途取得が必要 → 結局 B 案と同じテンプレートが必要
  - リフレクションでのフィールド走査は、JPA `@Transient` / `@JsonIgnore` / 計算プロパティの扱いを個別判定する分岐が増え、メンテ困難
  - `@Auditable` の挙動(成功時のみ記録 / 失敗時の `*_DENIED` 記録)を annotation 属性で表現すると、宣言と実行時挙動の乖離が大きく、レビュー時に「実際に何が記録されるか」がコードから即座に読めない
- リスク・未知数: Native Image 下で AOP proxy が動作する設定例は Spring Framework の公式ドキュメントでも限定的(主に `@Transactional` レベル)。独自 Aspect の Native Image 互換性は要検証で、検証コストが高い。

### 選択肢 D: アプリケーションログ(構造化ログ)に diff を出力するのみ、専用テーブルは持たない

- 概要: `audit_logs` テーブルへの記録を止め、SLF4J + Logback の構造化 JSON ログに `{ "event": "TASK_UPDATE", "diff": [...] }` 形式で出力。CloudWatch Logs → S3 / DataDog に流す。Phase 2 以降で必要なら DB / S3 への永続化を別途検討。
- 利点:
  - DDL / Repository / UseCase 側の監査ログ書き込みコード不要
  - スループットは DB INSERT より高い(同期 I/O を回避できる)
- 欠点:
  - **基本設計書 §4.2.6(`audit_logs` テーブル定義 11 列確定)/ §6.7(`hash_chain` 改ざん検知)/ §7.2(DB 1 年保管)/ §8.1(B-03 削除バッチ / B-05 整合性検証バッチ)に対する直接の否定**になる。本 ADR は実装方式の決定であって、既決定の監査ログ仕様を覆すスコープではない
  - **NIST AU-2 / AU-10 / AU-11 整合性が崩れる**。CloudWatch Logs は改ざん不可性を契約レベルでは保証せず、`hash_chain` 相当の検証も難しい
  - **テナント越境制御(NIST AC-4)が外部システム側(CloudWatch / DataDog)に依存**することになり、Hibernate Filter / Spring Security の認可境界を超える。SaaS 顧客から「自テナント分の監査ログだけを抽出して提供してほしい」要請に応える経路が DB クエリより複雑化
  - 認可違反系 `*_DENIED` 8 値 / ログイン失敗(`LOGIN_FAILED`)等の「DB に書く前提で確定済の action」群と整合しない
  - Tenant Admin 向け S-15 ダッシュボード(基本設計書 §247 / `GET /api/audit-logs` A-22)が DB クエリでは作れなくなる
- リスク・未知数: なし(採用条件が既決定の設計に矛盾するため詳細検討は不要)。

## 3. 決定(Decision)

**採用**: 選択肢 B(自前 `audit_logs` + UseCase 層で field-by-field diff を計算)

- 既存 `audit_logs` テーブル(基本設計書 §4.2.6 / 11 列)をそのまま使用し、`detail JSON` 列に **変更があった項目だけ** を `[{ "field": "<dto_property>", "old": <旧値 or null>, "new": <新値 or null> }, ...]` 形式の配列で記録する
- 差分計算は feature の **`domain` 層に純粋関数**(例: `TaskAuditDiffDomainService.diff(Task previous, TaskPatchCommand command) : List<FieldChange>`)として実装。Spring 非依存
- write 系 UseCase(`UpdateTaskUseCase` / `DeleteTaskUseCase` / `ChangeTaskStatusUseCase` / `ChangeTaskVisibilityUseCase` 等)は以下の順序を **同一トランザクション内** で行う:
  1. 旧エンティティを `findByIdForUpdate`(または通常の `findById`)で読み込む
  2. PATCH コマンド(`JsonNullable<T>` 解決済)と旧エンティティを `<Feature>AuditDiffDomainService` で diff
  3. diff が空でなければ entity を更新 → 保存
  4. `AuditLog` を `action = UPDATE`(または相応の値)+ `detail = <diff の JSON>` で INSERT
- diff が空(全項目が undefined or 旧値と一致)の場合は **更新も監査ログ INSERT も行わない**(冪等性確保 + ストレージ節約)
- `field` 名は **DB カラム名ではなく DTO プロパティ名(camelCase)** に揃える(API 表現と監査ログ表現を一致させ、tenant admin S-15 ダッシュボードでの表示と integrate しやすくする)
- 列挙型 / 日付 / null の表現は JSON 文字列化(`OffsetDateTime` は ADR-0009 JST 全層統一に従い `2026-07-05T15:30:00+09:00` 形式、列挙型は `name()`、null は JSON `null`)
- 認可違反系 `*_DENIED` action(8 値)も同じテンプレートで記録(`detail` に拒否理由 + 試行された変更内容を入れる)
- `actor_sub` は `TasksPrincipal` から取得(ADR-0004 / 設計規約 §3.5 の方針継承)、`tenant_id` は `TenantContext` から取得(ADR-0010 連動)

## 4. 理由(Rationale)

- 監査ログの **保存先・スキーマ・保管期間・改ざん検知バッチは既決定**(基本設計書 §4.2.6 / §6.7 / §7.2 / §8.1)であり、これを覆さない案は実質 B のみ。A / C は既存テーブルと併存する二重管理を生み、D は既決定を否定するスコープ越境となる
- **PATCH 差分のみを記録する**ことで、ストレージコストが Envers のエンティティ全体スナップショットより 1〜2 桁小さくなる。1 年保管 × 1000 tenants × 想定 PATCH 頻度を考えると無視できない差
- ADR-0010(Hibernate Filter)が SELECT 経路を機械的に絞る方針と **同じ思想で書込経路も Domain + UseCase で明示する** ことで、設計の一貫性が保たれる(Spring AOP / Envers は思想がずれる)
- ADR-0008(GraalVM Native Image)制約下で reflection / proxy 依存を最小化できる(C 案を退ける決定的理由でもある)
- `*_DENIED` 系の認可違反記録(設計書 §6.2.3 マッピング表で確定済)は CUD 操作以外も対象なので、Envers の「成功 CUD のみ」モデルでは扱えず、AOP の `@Auditable` モデルでも宣言が肥大化する。B 案の「UseCase 側で明示記録」が最も素直
- 差分計算ロジックが Domain 層純粋関数として独立 → ユニットテストで `null vs undefined vs 同値` の 3 条件を完全に網羅でき、`JsonNullable<T>` 採用(ADR-0014 候補・Issue #331)とも整合

## 5. 影響(Consequences)

### 良い影響(Positive)

- PATCH 差分のみ記録の方針が確定し、`detail JSON` のスキーマが他 feature(Tenant 編集・UserTenant ロール変更 等)でも横展開可能になる
- 監査ログ書き込みが **トランザクション内** に閉じるため、DB コミット成功と監査ログ記録の atomic が `@Transactional` で自動保証される(CloudWatch Logs 非同期書込の遅延・欠落リスクを回避)
- 差分計算ロジックが Domain 層に集約 → 設計規約 §4.3 / コーディング規約 §6.x への追記で一意のルールにできる
- `tenant_id IS NULL` 例外(SaaS Admin 操作)も同じテーブルで扱え、Tenant Admin / SaaS Admin の S-14 / S-15 監査参照画面(`GET /api/audit-logs` A-22)が DB クエリ 1 本で組める

### 悪い影響・制約(Negative)

- 各 entity ごとに `<Feature>AuditDiffDomainService` を手書きする必要がある(`tasks` / `tenants` / `user_tenants` で 3 系統。MVP スコープでは 3 で完結)
- `detail JSON` のスキーマを設計規約 §4.3 で明文化し、PR レビュー時に逸脱を検知する運用負荷が増える
- B-05 ハッシュチェーン整合性バッチが `detail JSON` の内容を含めて SHA-256 を計算するため、`detail` の JSON 表現(空白・キー順)が決定的でないと整合性が崩れる → Jackson の `SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS` 設定 + コーディング規約での明文化が要る

### 既存ドキュメント・規約への波及

- `docs/specs/設計規約.md` §4.3(監査ログ):
  - `detail JSON` のスキーマ規約(`[{ "field": "<dto_property_camelCase>", "old": <値 or null>, "new": <値 or null> }, ...]`)を追記
  - `OffsetDateTime` 値の JSON 表現は ADR-0009 JST 形式に従う点を明示
  - Jackson `ORDER_MAP_ENTRIES_BY_KEYS` 必須を明文化(B-05 ハッシュチェーン整合性のため)
  - 差分計算は Domain 層 `<Feature>AuditDiffDomainService` に SSOT 集約する旨を明示
- `docs/specs/コーディング規約.md`:
  - write 系 UseCase の標準テンプレート(旧値読込 → diff → 更新 → 監査ログ INSERT を `@Transactional` 内で実行)を追記(§6.x の例外設計 / 監査ログ書込節)
- `docs/specs/基本設計書.md`: §4.2.6 `audit_logs.detail` の備考に「差分は ADR-0013 のスキーマ規約に従う」と一行追記
- ADR-0011(独自 `record ErrorResponse`): 影響なし(ErrorCode enum は不変)
- ADR-0010(Hibernate Filter): 影響なし(`audit_logs` テーブルにも `tenantFilter` が自然適用される旨を §6.x コメントで補足)

## 6. 実装メモ(Implementation Notes)

着手順序(派生 Issue 想定、本 ADR PR とは別):

1. **設計規約 §4.3 / コーディング規約 / 基本設計書 §4.2.6 への追記 PR**(本 ADR Accepted 後、Sprint 0 N4 系で合流)
2. **`AuditLog` JPA Entity + Repository + DomainModel + Port 定義**(Sprint 0 infra タスクとして起票)
3. **`TaskAuditDiffDomainService` 実装 + ユニットテスト**(Sprint 0 N5 系 `UpdateTaskUseCase` 実装と並行、Issue #334 OpenAPI 反映の依存元)
4. **B-05 ハッシュチェーン整合性検証バッチ実装**(Phase 1 後半 / Phase 2 着手前)
5. **B-03 1 年超レコード削除バッチ実装**(Phase 2 / 運用開始前)
6. **Tenant Admin 向け `GET /api/audit-logs`(A-22)実装**(Phase 1 / Phase 2)
7. **GDPR / テナント解約(#167 Phase 2)時の cascade 削除設計**(本 ADR スコープ外、#167 で別途決定)

`detail JSON` のスキーマ例:

```json
[
  { "field": "title", "old": "旧タイトル", "new": "新タイトル" },
  { "field": "dueDate", "old": "2026-07-01T00:00:00+09:00", "new": "2026-07-05T00:00:00+09:00" },
  { "field": "assigneeUserId", "old": 12, "new": null },
  { "field": "status", "old": "TODO", "new": "IN_PROGRESS" }
]
```

検証(`<Feature>AuditDiffDomainService` ユニットテスト網羅項目):

- `JsonNullable.undefined()`(クライアント未指定)→ diff 不在
- `JsonNullable.of(null)`(明示 null 設定)→ 旧値が non-null なら `{ old: <旧値>, new: null }`、旧値も null なら diff 不在
- `JsonNullable.of(<旧値と同値>)` → diff 不在
- `JsonNullable.of(<旧値と異なる値>)` → `{ old: <旧値>, new: <新値> }`
- 列挙型・日付・boolean・整数の各型で同上ケース
- diff 配列が空の場合は監査ログ INSERT を行わない(冪等性 IT で検証)

統合 IT(`UpdateTaskIT`):

- 1 項目のみ PATCH → `audit_logs` に 1 行 INSERT、`detail` には 1 件のみ
- 同値 PATCH(全項目が旧値と一致)→ `audit_logs` INSERT なし
- 全項目 PATCH(行内編集 6 項目同時)→ `audit_logs` に 1 行 INSERT、`detail` に 6 件
- `*_DENIED` ケース(認可違反)→ `audit_logs` に `EDIT_DENIED` で INSERT、`detail` に試行内容と拒否理由

## 7. 参考リンク(References)

- Issue #333(本 ADR 起票元)
- Issue #329(PATCH /api/tasks/{id} 化確定)
- Issue #331(`JsonNullable<T>` ADR 候補、議論先行)
- Issue #332(ETag / If-Match ADR、ADR-0012 で Accepted)
- Issue #334(OpenAPI v1.5.x 反映、本 ADR 採用結論の利用先)
- Issue #167(テナント解約 Phase 2、cascade 削除設計の連動先)
- ADR-0004(`tenants` の `created_by` / `updated_by` 例外、`actor_sub` 方針)
- ADR-0008(GraalVM Native Image)
- ADR-0009(JST 全層統一)
- ADR-0010(Hibernate Filter)
- ADR-0011(独自 `record ErrorResponse`)
- ADR-0012(ETag / If-Match 楽観ロック)
- 基本設計書 §4.2.6 / §6.2.3 / §6.7 / §7.2 / §8.1
- 設計規約 §3.5 / §4.3
- NIST SP 800-53 AU-2 / AU-10 / AU-11(監査要件) / AC-4(情報フロー制御)
