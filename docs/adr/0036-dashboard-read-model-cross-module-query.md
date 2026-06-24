# ADR-0036: ダッシュボード集計のクロスモジュール読み取りモデル

- **Status**: Accepted
- **Date**: 2026-06-25
- **Deciders**: win2cot
- **Tags**: architecture, persistence, modulith

## 1. コンテキスト(Context)

S-03 個人視点ダッシュボード(`GET /api/dashboard/tasks` / `GET /api/dashboard/summary`、Issue #749)は、`tasks` / `task_stakeholders` テーブルに対し visibility 3 役割評価(ADR-0005)を適用したうえで 4 セクション抽出と件数集計を行う。これらのテーブルは `task` feature が所有しており、新設する `dashboard` feature からはモジュール境界(Spring Modulith、設計規約 §1)を越えて参照する必要がある。

制約:

- テナント分離は Hibernate Filter(ADR-0010)が担う。Filter は **エンティティ経由の JPQL/Criteria にのみ自動適用**され、native query には効かない(設計規約 §3.3 / §334)。
- 設計規約 §6 は「他者の PRIVATE が件数で漏れていないこと」を検証する結合テストを必須とする(NIST AC-4)。
- 設計規約 §3.3 native 許容カテゴリ(1) は「モジュール境界を越えて他モジュールのテーブルを参照する集計」を native query で書くことを認めている。

## 2. 検討した選択肢(Options Considered)

### 選択肢 A: `task` feature にダッシュボード用クエリを公開(`@NamedInterface`)

- 概要: `task.usecase` / `task.domain` を `@NamedInterface` で公開し、`dashboard` が `task` のクエリサービスを呼ぶ。
- 利点: visibility 述語の SSOT が `task` 側に一元化される。
- 欠点: 集計境界が `dashboard.usecase` であるべき(#749)という設計意図に反する。`task` の公開面が広がり、ドメインサービス等まで他 feature へ露出する。

### 選択肢 B: `dashboard` が native SQL で `tasks` を直接集計

- 概要: 設計規約 §3.3(1) に従い `dashboard.adapter.persistence` で native query を書く。
- 利点: `dashboard` が自己完結。`task` に一切変更不要。
- 欠点: **Hibernate Filter が効かないため `tenant_id` を手動絞り込みする必要があり**、テナント漏洩リスクが上がる(§6 の要請と相性が悪い)。native 行→ドメインのマッピングが煩雑。

### 選択肢 C: `dashboard` 所有の読み取り専用エンティティ + JPQL(採用)

- 概要: `dashboard.adapter.persistence` に `tasks` / `task_stakeholders` を参照する `@Immutable` 読み取りモデルエンティティ(`DashboardTaskView` / `DashboardStakeholderView`)を定義し、`TenantFilteredEntity` を継承する。集計は Spring Data JPA + JPQL で行う。
- 利点: `TenantFilteredEntity` 継承により **Hibernate Filter がテナント分離を自動付与**(native の手動絞り込み不要、漏洩リスク最小)。クエリ実装優先順位(設計規約 §3.3)で上位の JPQL を使える。`dashboard` が自己完結し `task` に変更不要。`HibernateFilterEntityAuditTest` も通過する。
- 欠点: `tasks` / `task_stakeholders` を 2 つ目のエンティティで二重マッピングする(読み取り専用に限定して緩和)。visibility 述語が `task` の Criteria 版と本 JPQL 版の 2 箇所に存在する(§6 の結合テストで漏れを担保)。

## 3. 決定(Decision)

**採用**: 選択肢 C(`dashboard` 所有の読み取り専用エンティティ + JPQL)。

`dashboard` feature は自己完結のリードモデルを持ち、`tasks` / `task_stakeholders` への参照は `TenantFilteredEntity` を継承した `@Immutable` ビューエンティティ経由の JPQL で行う。`task` feature のクラスは一切参照しない(共有するのは `shared`(OPEN モジュール)/ `user.adapter.persistence` / `security.adapter.web` の既存公開面のみ)。

## 4. 理由(Rationale)

- テナント分離(ADR-0010)の自動適用が最重要。Filter が効くエンティティ経由 JPQL は、native(選択肢 B)が抱える「手動 `tenant_id` 絞り込み忘れ」による漏洩リスクを構造的に排除する。設計規約 §6 / NIST AC-4 の要請に最も整合する。
- 設計規約 §3.3 のクエリ優先順位で JPQL は native より上位。native 許容(1) は「読み取りモデルを増やしたくない場合」の逃げ道であり、Filter 保護を犠牲にしてまで選ぶ理由がない。
- モジュール境界を Java 型レベルで侵さず(選択肢 A の `task` 公開面拡大を回避)、集計境界を `dashboard.usecase` に置けて #749 の設計意図に沿う。
- visibility 述語の二重化は、設計規約 §6 が必須化する PRIVATE 漏洩検証 IT(`DashboardIT`)で担保する。

## 5. 影響(Consequences)

### 良い影響(Positive)

- `dashboard` が自己完結し、`task` feature を変更せずに済む。
- テナント分離が Filter により自動保証され、レビュー負荷と漏洩リスクが下がる。
- 集計が DB 側 JPQL で完結し、N+1 を出さない(#749 受け入れ条件)。

### 悪い影響・制約(Negative)

- `tasks` / `task_stakeholders` を読み取り専用エンティティで二重マッピングする。書き込みは `task` feature の正本エンティティに限定し、`dashboard` 側は `@Immutable` で読み取りに固定する。
- visibility 3 役割評価のクエリ表現が `task`(Criteria)と `dashboard`(JPQL)に分散する。ADR-0005 を変更する際は両方の更新が必要。`DashboardIT` の漏洩検証で退行を検知する。

### 既存ドキュメント・規約への波及

- 設計規約 §6 を ADR-0005 整合に改訂(Tenant Admin 特別加算の撤廃を明記、個人視点に `GET /api/dashboard/tasks` を追記)。
- 設計規約 §3.3(1) の native 許容カテゴリは引き続き有効。本 ADR はその代替として「Filter 保護つきリードモデル」を第一選択として位置づける補足。

## 6. 実装メモ(Implementation Notes)

- `DashboardTaskView`(`tasks`)/ `DashboardStakeholderView`(`task_stakeholders`)はいずれも `@Immutable` + `TenantFilteredEntity` 継承。`status` / `priority` / `visibility` は ENUM 列を文字列として保持し、`task.domain` の enum 型へ依存しない(JSON 表現は enum と同一)。
- summary は認可フィルタ通過タスク集合を 1 クエリ(軽量射影 `DashboardSummaryRow`)で取得し、件数系・ブレークダウンを adapter 側で算出する(認可述語を 1 箇所に集約、over-fetch 回避)。
- 4 セクションは抽出条件が排他で、いずれも所有者または担当者(owner OR assignee)に限定。priority 降順は JPQL の CASE 式で HIGH>MEDIUM>LOW を表現する。

## 7. 参考リンク(References)

- Issue #749(S-03 個人視点バックエンド)/ #355(FM-G4、4 セクション契約 案 B)
- ADR-0005(タスク認可 3 役割)/ ADR-0010(Hibernate Filter テナント分離)
- 設計規約 §3.3(クエリ実装優先順位)/ §6(ダッシュボード集計の認可スコープ)
- 基本設計書 §6.2.2.1(個人視点ダッシュボード)
- OpenAPI v1.7.1 `DashboardTaskSections` / `DashboardSummary`
