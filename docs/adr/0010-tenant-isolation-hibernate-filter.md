# ADR-0010: マルチテナント絞り込みの自動付与は Hibernate Filter で行う

- **Status**: Accepted
- **Date**: 2026-05-30
- **Deciders**: 開発チーム
- **Tags**: persistence, multi-tenancy, security

## 目次

- [1. コンテキスト](#1-コンテキスト)
- [2. 検討した選択肢](#2-検討した選択肢)
  - [選択肢 A: Hibernate Filter(`@FilterDef` + `@Filter`)で全 SELECT に自動付与](#選択肢-a-hibernate-filterfilterdef--filterで全-select-に自動付与)
  - [選択肢 B: Spring AOP(`@Around`)で Repository メソッドを intercept](#選択肢-b-spring-aoparoundで-repository-メソッドを-intercept)
  - [選択肢 C: 両者の併用(SELECT は Filter、UPDATE/DELETE/native は AOP)](#選択肢-c-両者の併用select-は-filterupdatedeletenative-は-aop)
- [3. 決定](#3-決定)
- [4. 理由](#4-理由)
- [5. 影響](#5-影響)
  - [良い影響](#良い影響)
  - [悪い影響・制約](#悪い影響制約)
  - [既存ドキュメント・規約への波及](#既存ドキュメント規約への波及)
- [6. 実装メモ](#6-実装メモ)
- [7. 参考リンク](#7-参考リンク)

## 1. コンテキスト

tasks-webapi はマルチテナント SaaS であり、全業務テーブルが `tenant_id BIGINT NOT NULL` を持ち、全 SQL に `WHERE tenant_id = :ctx` を自動付与する不変ルール(基本設計書 §6.3 / 設計規約 §3.3)を採る。越境アクセスは参照系 404 / 更新系 403 で拒否する(NIST AC-4)。

設計規約 v1.3 §3.3 は事実上「Hibernate Filter で全 SELECT に自動付与する」前提でドラフトされているが、コーディング規約 v1.3 §8.1 と合わせて「Filter vs Spring AOP の選択は今後の ADR で決定予定 — 想定 ADR-0004」と保留してきた。一方、ADR-0004 番号は別件(`tenants-as-audit-column-exception`、2026-05-16 Accepted)に消費されたため、本決定は **ADR-0010** として起票する。

Sprint 0 着手(2026-07-14 予定)を前に方式を確定し、§3.3 / §8.1 の保留節を解消する必要がある。直近で Accepted となった ADR-0008(GraalVM Native Image 採用)と ADR-0009(JST 全層統一)も本決定の制約条件となる。

なお、現行 scaffold には既に `TaskJpaEntity` / `TenantJpaEntity` / `UserJpaEntity` の 3 JPA entity と `findByIdAndTenantId` 形式の明示絞り込み Repository が配置されているが、Hibernate Filter(`@FilterDef` / `@Filter`)および `TenantContext` 注入経路は**未配線**である。本 ADR は方式の確定のみを行い、実コード配線(`@FilterDef` 配置 + リクエストスコープでの enable + Repository 命名緩和)は本 PR とは別の Sprint 0 着手タスクで起票・実施する(規約決定 ADR は実装を別 PR に分離する運用、ADR-0004 と同形)。

関連 Issue: #145(本 ADR 起票 Issue) / #121(Sprint 0 readiness 親 Issue) / ADR-0001 §6 候補リスト。

## 2. 検討した選択肢

### 選択肢 A: Hibernate Filter(`@FilterDef` + `@Filter`)で全 SELECT に自動付与

- 概要: 共通の mapped superclass(または各エンティティ)に `@FilterDef(name="tenantFilter", parameters=@ParamDef(name="tenantId", type=Long.class))` と `@Filter(name="tenantFilter", condition="tenant_id = :tenantId")` を宣言する。`TenantContext` から現在の `tenant_id` を取得し、リクエストスコープの interceptor(または `@EntityManagerFactoryBean` カスタマイズ)で session ごとに `enableFilter("tenantFilter").setParameter(...)` する。
- 利点:
  - Hibernate ネイティブ機能で**追加依存ゼロ**。`@Filter` API は Hibernate 6/7 で安定。
  - **エンティティ取得と関連ナビゲーション(`task.subTasks` 等)の双方に効く**。lazy loading で発行される SQL にも Filter が乗る。
  - GraalVM Native Image(ADR-0008)と相性が良い(Spring proxy 不要、reflection hints は Hibernate 側で整備済)。
  - SaaS Admin の `tenants` テーブルアクセス(ADR-0004 で確定の例外)は Filter 未設定で自然に bypass される。
  - バッチや管理用途で一時的に bypass する場合も `session.disableFilter("tenantFilter")` を専用ヘルパー経由で明示でき、レビュー対象として可視化しやすい。
- 欠点:
  - **`@Modifying` JPQL の UPDATE/DELETE および native query には適用されない**ため、書き込み系は別途明示絞り込みが必要(現行 §3.3 で既にルール化済)。
  - session で `enableFilter` し忘れると全件漏洩が起きるため、interceptor の設定漏れが致命的(検知は §9.4 クロステナント漏洩テストで担保)。
  - boolean 型パラメータが `INTEGER` バインドになる等のバージョン差異があり、MySQL 8.4 + Hibernate 7 での挙動確認が必要。

### 選択肢 B: Spring AOP(`@Around`)で Repository メソッドを intercept

- 概要: Repository メソッドに対する `@Around` Aspect を定義し、`TenantContext` から取得した `tenant_id` を Specification / JPQL / native SQL に挿入する。
- 利点:
  - UPDATE / DELETE / native query を含め、Repository メソッドを経由する全アクセスを一律 intercept できる(設計上)。
  - 既存の Spring AOP(`spring-boot-starter-aop` 同梱)で実装可能。
- 欠点:
  - **エンティティ関連ナビゲーションには効かない**(Hibernate が SQL を直接発行するため AOP は触れない)。`task.subTasks` 等の lazy ナビゲーションでテナント漏洩が起きる経路をふさげない。
  - **GraalVM Native Image(ADR-0008)との相性が悪い**。Spring AOP は CGLIB / JDK proxy 依存で、`reflect-config.json` / `proxy-config.json` の追加運用と debug 困難さが Sprint 0 早期に必ず障害となる。
  - `@Modifying` JPQL の本文を AOP が書き換えるのは現実的でなく、結局は Specification や `EntityManager#createQuery` 経由の書き直しが必要となり、§3.3 の明示絞り込みルールを温存する形に落ち着く。
  - pointcut 漏れ(新規 Repository / メソッドシグネチャ追加時の追従漏れ)が一部経路だけの漏洩を生むため、検知が困難。
  - `@SpringBootTest` 必須化でテスト境界が広がり、Slice テスト(`@DataJpaTest`)で Aspect が外れる/付くの差異が混乱を生む。

### 選択肢 C: 両者の併用(SELECT は Filter、UPDATE/DELETE/native は AOP)

- 概要: 選択肢 A の Filter で SELECT を担保しつつ、UPDATE / DELETE / native query は選択肢 B の AOP で intercept する。
- 利点: 各機構の長所を取り、SELECT は Filter、書き込みは AOP で多層防御できる(設計上)。
- 欠点:
  - 二機構の同時運用で**設定漏れ箇所が単独案より増える**。
  - **重複絞り込み**(Filter と AOP が同じ SELECT に対し `WHERE tenant_id=? AND tenant_id=?` を生む)を回避するロジックが必要で、Filter enable 状態と AOP の判定を相互に参照する必要が出る。
  - B の GraalVM Native Image 不適合と関連ナビゲーション不対応の欠点を併用案でも引き継ぐ。
  - デバッグ時、絞り込みが Filter / AOP / 派生クエリの 3 経路のどこで効いたかを追跡するコストが上がる。

## 3. 決定

**採用**: 選択肢 A(Hibernate Filter で全 SELECT に自動付与)

具体的には:

- 設計規約 §3.3 を以下のとおり確定する(本 ADR と同 PR で改訂):
  - 「Filter vs Spring AOP の選択は今後の ADR で決定予定 — 想定 ADR-0004」の保留節を削除し、「`@FilterDef` + `@Filter` を採用する」旨と本 ADR への参照を明記する。
  - 既存の「`@Modifying` JPQL / native query は明示的に `tenant_id` を絞り込む」「全件検索を書かない」「一時 disable は専用ヘルパー経由 + レビュー必須」のルールは維持する。
- コーディング規約 §8.1 を以下のとおり緩和する(本 ADR と同 PR で改訂):
  - **緩和**: 業務テーブル参照の SELECT 系派生クエリは `findById(Long id)` / `findByStatus(...)` 等の**単純命名で構わない**(`tenant_id` は Filter が自動付与)。
  - **維持**: `@Modifying` UPDATE / DELETE および native query / `@Query(nativeQuery=true)` は引き続き `findByTenantIdAndId(...)` / `deleteByTenantIdAndId(...)` / 明示 JPQL の `WHERE tenant_id = :tenantId AND ...` を必須とする(設計規約 §3.3)。
- 実装上の固定点:
  - `@FilterDef` は共通 mapped superclass(または `package-info.java`)に集約する。
  - `TenantContext` の値を session に注入する経路は、リクエストスコープの `OncePerRequestFilter` または Hibernate `SessionEventListener` で集約し、忘却を構造的に防止する。具体的な経路は Sprint 0 着手時に PoC で確定する(本 ADR は方式の確定のみを行う)。

## 4. 理由

- **GraalVM Native Image(ADR-0008)との相性が決定打**。Spring AOP は CGLIB / JDK proxy 依存で Native Image での運用コスト(reflect-config / proxy-config の追加運用、debug 困難)が大きく、Sprint 0 早期に必ず障害となる。Hibernate Filter は Hibernate ネイティブ機能で proxy 不要、ADR-0008 と整合的に運用できる。
- **エンティティ関連ナビゲーション(`task.subTasks` 等)に効くのは Hibernate Filter だけ**。AOP は Hibernate が直接発行する lazy loading SQL を touch できない。テナント漏洩経路の最大候補をふさげるのは A のみ。
- **設計規約 §3.3 が既に A 案ドラフトの形をしており**、コーディング規約 §8.1 で `findByTenantId` 起点を強制している。本 ADR は「§3.3 を正式化し §8.1 を緩和する」という Issue #145 のスコープと整合する。
- B / C の最大の利点(UPDATE / DELETE の自動絞り)は AOP の本質的能力ではなく **SQL 書換に依存**しており、AOP では現実的に難しい。書き換え戦略を真剣に採るなら Hibernate `StatementInspector` を別 ADR で評価する方が筋が良く、本 ADR の選択肢には含めない。
- UPDATE / DELETE / native query の絞り漏れは、§3.3 の明示絞り込みルール + §9.4 クロステナント漏洩 IT(参照系 404 / 更新系 403)+ §3.5 監査ログで多層防御済。AOP を足しても防御層が「proxy 越し」になるだけで根本対処にはならない。
- 形骸化リスク(Filter session enable 忘れ)は **§9.4 IT が即座に fail する**ため、検知容易な失敗モードに収束する。AOP の pointcut 漏れ(一部経路だけ漏洩)は検知が困難な失敗モードで、運用上の不安が大きい。

## 5. 影響

### 良い影響

- 設計規約 §3.3 / コーディング規約 §8.1 の保留節がクローズし、Sprint 0 着手前の規約整合チェックリストから 2 項目が消える。
- SELECT 系 Repository メソッドが `findById(Long id)` 等のシンプル命名になり、コードの可読性とテストの記述量が下がる。
- ADR-0008(GraalVM Native Image)と整合する持続的な方式が確定し、Sprint 0 以降の AOT コンパイル運用で予期せぬ proxy 起因の障害が発生する確率が下がる。
- `tenants` テーブルの SaaS Admin 操作(ADR-0004)は Filter 未設定で自然に bypass され、追加の bypass 機構を設計する必要がない。

### 悪い影響・制約

- `@Modifying` JPQL / native query では引き続き明示的な `tenant_id` 絞り込みが必要で、レビュー観点として残る。
- Filter session enable を忘れた場合の影響は「全件漏洩」と大きいため、interceptor 設置箇所の構造的担保(`OncePerRequestFilter` で集約)と §9.4 クロステナント漏洩 IT を**必ず実装**する必要がある。
- 将来 Hibernate 6 → 7 → 8 のメジャーバージョンアップで `@Filter` API に変更が入った場合の追従工数が残る。

### 既存ドキュメント・規約への波及

- 設計規約 v1.4 §3.3 を改訂(保留節削除 + 本 ADR リンク追加)。本 ADR と同 PR で対応(Issue #145)。
- コーディング規約 v1.4 §8.1 を緩和(SELECT は単純命名 OK / `@Modifying`・native は明示絞り維持)。本 ADR と同 PR で対応(Issue #145)。
- ADR-0001 §6 候補リストの「テナント分離実装 — 想定 ADR-0004」は履歴情報として現状維持(候補番号はあくまで起票時の見込みである旨を ADR-0001 §6 が明記済)。

## 6. 実装メモ

### §6.1 Filter 除外テーブル一覧(Issue #315 で確定)

以下のテーブルに対応する JPA エンティティは `TenantFilteredEntity` を継承せず、Hibernate Filter を適用しない。設計規約 §3.3.1 も参照のこと。

| テーブル | 対応 JPA エンティティ | 除外理由 |
|---|---|---|
| `tenants` | `TenantJpaEntity` | マスタテーブル。`tenant_id` 列なし。このテーブル自体がテナント境界 |
| `users` | `UserJpaEntity` | プラットフォーム横断ユーザー。`tenant_id` 列なし |
| `user_tenants` | `UserTenantJpaEntity` | `TenantContext` 確立前(`TenantContextFilter` 内)にクロステナント参照が必要。Filter を適用するとデッドロック的矛盾が生じる |
| `audit_logs` | (Sprint 1 以降実装) | `tenant_id` が nullable(システム横断イベントは `NULL`)。テナント範囲を超えた参照が必要 |
| `shedlock` | (JPA エンティティなし) | `tenant_id` 列なし。ShedLock フレームワーク管理テーブル |
| `app_admin_users` | `AppAdminUserJpaEntity` | SaaS Admin ユーザー管理。Keycloak が保持し、テナント境界の外側 |

**付与基準**: `tenant_id BIGINT NOT NULL` 列を持ち、テナント境界で分離すべき業務エンティティのみが `TenantFilteredEntity` を継承する。将来実装される `task_stakeholders` / `user_notification_settings` 等の業務テーブルは対応 JPA エンティティ作成時に `TenantFilteredEntity` を継承すること。

静的検証: `HibernateFilterEntityAuditTest` が `EntityManagerFactory` のメタモデルを走査し、全 JPA エンティティが上記基準を満たすことを CI で確認する。

- 本 ADR は方式の確定のみを行う。実コードでの配線は **Issue #87(N5: TenantContext + TenantContextFilter + Hibernate Filter 実装)** で行い、以下を実施する:
  - 既存 `TaskJpaEntity` / `TenantJpaEntity` / `UserJpaEntity`(および後続 entity)への `@Filter` / `@FilterDef` 適用。`@FilterDef` を共通 mapped superclass に置く案と `package-info.java` に置く案の選定(Modulith 内境界への影響を含めて Sprint 0 で確定)。
  - `TenantContext` の値を session に注入する経路の構築。`OncePerRequestFilter` 案と Hibernate `SessionEventListener` 案を比較選定し、忘却を構造的に防止する。
  - 既存 `TaskJpaRepository#findByIdAndTenantId` 等の明示絞り込み命名を、コーディング規約 §8.1 v1.4 の緩和に沿って `findById` 等の単純命名に書き換え(SELECT 系のみ。`@Modifying` UPDATE/DELETE と native は明示絞り維持)。
  - MapStruct(想定 ADR 未決)で DTO 投影する際に Filter が効くことの確認。
  - boolean 型パラメータの MySQL 8.4 + Hibernate 7 でのバインド挙動の確認。
  - Spring Modulith `@ApplicationModuleTest` 単位での Filter session 状態の test isolation 確認。
- 上記配線・検証で重大な不整合が見つかった場合は、本 ADR を Supersede する別 ADR(`StatementInspector` への切替、または併用案再評価)を起こす。

## 7. 参考リンク

- Issue #145(本 ADR 起票 Issue)
- Issue #315(D-2: Filter 除外テーブル運用整理 — §6.1 除外一覧の確定)
- Issue #121(Sprint 0 readiness 親 Issue)
- Issue #87(N5: TenantContext + TenantContextFilter + Hibernate Filter 実装 — 本 ADR の実コード配線担当)
- 設計規約 §3.3 / §3.5(本 ADR と同 PR で改訂)
- コーディング規約 §8.1 / §9.4(本 ADR と同 PR で改訂)
- 基本設計書 §6.3(マルチテナント設計)
- ADR-0001(ADR 制度導入)
- ADR-0004(SaaS Admin スコープ整理 / `tenants` 監査列例外)
- ADR-0008(GraalVM Native Image 採用)
- ADR-0009(JST 全層統一)
- Hibernate ORM User Guide: Filtering data
