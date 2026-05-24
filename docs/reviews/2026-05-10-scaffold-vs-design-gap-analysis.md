# Scaffold ↔ 設計書 v1.3 整合性ギャップ分析

**実施日**: 2026-05-10
**対象**: `main` ブランチの既存scaffold(`b537826` 時点)と詳細設計成果物(`feature/initial-design-docs`)
**目的**: Sprint 0 着手時に必要な追加実装・修正・設計補正項目の洗い出し

---

> **⚠ 規約成立後の方針更新について(2026-05-24 追記)**
>
> 本 doc は 2026-05-10 時点の整理。以降の規約成立(2026-05-14 PR #135 マージ、ADR-0002 / ADR-0006 / ADR-0008 等)により、以下の項目は方針が更新されている。最新は **設計規約 / コーディング規約 / 各 ADR を参照**:
>
> - § G-5「クリーンアーキ 4 層 + ArchUnit」 → **Spring Modulith による feature-by-package + 各 feature 内 4 層** のハイブリッド構成([設計規約 §1.1](../specs/設計規約.md#11-採用するアーキテクチャスタイル))。**ArchUnit 採用は当面保留**([ADR-0002](../adr/0002-defer-archunit-adoption.md))、依存方向検証は Spring Modulith `ApplicationModules.verify()` に集約。
> - § G-6「環境別 application-{local,staging,prod}.yml の分割」 → **採用しない**。[コーディング規約 §20.2](../specs/コーディング規約.md#202-spring-プロファイル不使用) で `@Profile` 全面禁止、[infrastructure-plan §3.4](../architecture/infrastructure-plan.md#34-環境変数による設定注入spring-プロファイル不使用) で単一 yml + 環境変数注入に統一。
> - § G-6「シークレットは AWS Secrets Manager 参照」 → tasks-webapi の RDS は IAM 認証で DB password 不使用([infrastructure-plan §3.5](../architecture/infrastructure-plan.md#35-rds-認証k-a-案mysql-84))。OIDC Issuer URI は機密ではなく環境変数で十分。
> - § 推奨アクション「各 Issue を 1 PR とし」 → schema 変更を伴う実装は **migration と JPA Entity を 1 PR で同時 commit**(N1 / N3 のような分割は禁止、別 Issue として起票も推奨しない)。
>
> 本 doc 自体は 2026-05-10 の整理として保持(履歴的価値)。実装着手時は上記更新方針を優先。

---

## サマリ

| 領域 | 整合状態 | 必要対応 |
|---|---|---|
| `users` テーブル / `User.java` | ✅ 完全一致(6カラム同一) | **設計書の補正のみ**(「既存DB別所」表記を訂正) |
| `tasks` テーブル / `Task.java` | 🟠 大きなギャップ | 拡張 migration + Entity拡張 |
| `SecurityConfig` | 🟡 ベース整合・細粒度認可未実装 | RBAC + テナント認可の追加 |
| `TasksJwtAuthenticationConverter` | ✅ 完全一致 | なし |
| `TasksPrincipal` | ✅ 完全一致 | なし |
| `application.yml` | 🟡 最小構成 | OAuth2 issuer / DataSource / Flyway 設定追加 |
| クリーンアーキテクチャ4層構造 | 🔴 未実装(scaffold は標準Spring構成) | Domain / UseCase / Adapter リファクタ |
| その他テーブル(tenants/user_tenants/task_stakeholders/audit_logs/user_notification_settings/shedlock) | 🔴 未実装 | 新規 migration + Entity |
| API実装(28 endpoints) | 🔴 未実装 | Controller + UseCase + Repository |

---

## 詳細

### G-1. ✅ `users` / `User.java` — 完全一致

scaffold:
```sql
CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    oidc_sub VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    full_name_kana VARCHAR(255) NOT NULL,
    department_name VARCHAR(255) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_users_oidc_sub (oidc_sub)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

`User.java` も全6カラムを Lombok Getter で公開、protected no-args constructor、JPA Entity として実装済。

**設計書側の補正が必要**:
- 基本設計書 §4.2.1 / 要件定義書 §5.3 に「既存DBにあり、本システムからはReadOnlyレプリカ経由で参照のみ」と書いたが、現実には **本リポジトリの V1.0.0_01__create_tables.sql で同一スキーマを作成している**。
- **訂正案**: 「同一スキーマで本システムDB内に保有(他システムからの移行を視野に同等の構造を保持)」または「将来既存DBへの統合を視野」と表現を改める。

---

### G-2. 🟠 `tasks` / `Task.java` — 大きなギャップ

scaffold:
```sql
CREATE TABLE tasks (
    id BIGINT NOT NULL AUTO_INCREMENT,
    status ENUM('INCOMPLETE','COMPLETE') NOT NULL DEFAULT 'INCOMPLETE',
    owner_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    body LONGTEXT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_tasks_owner FOREIGN KEY (owner_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

設計書 v1.3 §4.2.4 が要求する追加・変更:

| 項目 | 現状 | 設計 v1.3 | 差分 |
|---|---|---|---|
| `tenant_id` | なし | BIGINT NOT NULL + FK | **追加必須**(マルチテナント分離キー) |
| `status` enum | INCOMPLETE / COMPLETE(2) | NOT_STARTED / IN_PROGRESS / DONE / ON_HOLD(4) | **enum拡張**(データ移行考慮) |
| `priority` | なし | VARCHAR(10) HIGH/MEDIUM/LOW NOT NULL | **追加必須** |
| `visibility` | なし | VARCHAR(20) TENANT/STAKEHOLDERS/PRIVATE NOT NULL DEFAULT 'TENANT' | **追加必須**(可視性モデル) |
| `assignee_id` | なし | BIGINT NULL | **追加**(担当者) |
| `due_date` | なし | DATE NOT NULL | **追加必須**(F-08 期限必須化) |
| `completed_at` | なし | DATETIME NULL | **追加** |
| `deleted_at` | なし | DATETIME NULL | **追加**(論理削除) |
| `created_at` / `updated_at` | なし | DATETIME NOT NULL | **追加**(監査用) |
| `body` | LONGTEXT | `description` TEXT(2000) | **改名 + 型変更**(API側は description) |
| `title` | VARCHAR(255) | VARCHAR(100) | **長さ縮小**(設計合わせ) |

**マイグレーション案**: `V1.0.1__align_tasks_with_design_v1_3.sql`
```sql
-- 列名変更
ALTER TABLE tasks CHANGE COLUMN body description TEXT NULL;

-- 列追加
ALTER TABLE tasks
  ADD COLUMN tenant_id    BIGINT       NOT NULL  AFTER id,
  ADD COLUMN priority     VARCHAR(10)  NOT NULL DEFAULT 'MEDIUM',
  ADD COLUMN visibility   VARCHAR(20)  NOT NULL DEFAULT 'TENANT',
  ADD COLUMN assignee_id  BIGINT       NULL,
  ADD COLUMN due_date     DATE         NULL,        -- 一旦 NULL 許容で追加後、データ移行後 NOT NULL 化
  ADD COLUMN completed_at DATETIME     NULL,
  ADD COLUMN deleted_at   DATETIME     NULL,
  ADD COLUMN created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  ADD COLUMN updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;

-- status enum 拡張(MySQL ENUM の安全な拡張)
ALTER TABLE tasks
  MODIFY COLUMN status ENUM('NOT_STARTED','IN_PROGRESS','DONE','ON_HOLD','INCOMPLETE','COMPLETE')
    NOT NULL DEFAULT 'NOT_STARTED';
-- 既存値マッピング:
UPDATE tasks SET status = 'NOT_STARTED' WHERE status = 'INCOMPLETE';
UPDATE tasks SET status = 'DONE'        WHERE status = 'COMPLETE';
-- 旧値を削除
ALTER TABLE tasks
  MODIFY COLUMN status ENUM('NOT_STARTED','IN_PROGRESS','DONE','ON_HOLD')
    NOT NULL DEFAULT 'NOT_STARTED';

-- インデックス
CREATE INDEX idx_tasks_tenant_due         ON tasks (tenant_id, due_date);
CREATE INDEX idx_tasks_tenant_owner_due   ON tasks (tenant_id, owner_id, due_date);
CREATE INDEX idx_tasks_tenant_assignee_due ON tasks (tenant_id, assignee_id, due_date);
CREATE INDEX idx_tasks_tenant_status_due  ON tasks (tenant_id, status, due_date);
CREATE INDEX idx_tasks_tenant_visibility  ON tasks (tenant_id, visibility);

-- title 縮小は既存データに依存。100超えがある場合はバッチで前処理してから ALTER:
-- UPDATE tasks SET title = LEFT(title, 100) WHERE LENGTH(title) > 100;
ALTER TABLE tasks MODIFY COLUMN title VARCHAR(100) NOT NULL;
```

**Entity拡張**: `Task.java` に上記カラム + Lombok or canonical setters を追加。クリーンアーキ移行時はDomain層に Task 集約を作り、Entity は `adapter.persistence` に移動。

> **※ 2026-05-24 追記**: 本セクションの「`V1.0.1__align_tasks_with_design_v1_3.sql` 追加」案は、初期版直接追記の例外運用ルール([設計規約 §3.1](../specs/設計規約.md#31-flyway-マイグレーション)、dev 初回デプロイ前 = Sprint 1 完了 2026-07-11 まで)成立により **`V1.0.0_01__create_tables.sql` への直接追記** に方針転換した。実装は既に V1.0.0 へ統合済(2026-05-24 確認)。Entity 拡張も同 PR で同時 commit のルールに従う(同上 §3.1)。Issue #83 (N1) / #85 (N3) は完了確認後 close 予定。

---

### G-3. 🔴 未実装テーブル(全て新規 migration が必要)

| テーブル | 役割 | 設計書節 |
|---|---|---|
| `tenants` | テナント | §4.2.2 |
| `user_tenants` | ユーザー所属・ロール | §4.2.3 |
| `task_stakeholders` | タスク関係者 | §4.2.5 |
| `audit_logs` | 監査ログ(ハッシュチェーン) | §4.2.6 |
| `user_notification_settings` | 通知設定 | §4.2.7 |
| `shedlock` | バッチ排他制御 | §4.2.8 |

`V1.0.2__add_multitenancy_and_supporting_tables.sql` として一括追加を推奨。

> **※ 2026-05-24 追記**: 本セクションの「`V1.0.2__add_multitenancy_and_supporting_tables.sql` 追加」案も §G-2 と同様に、初期版直接追記の例外運用ルール([設計規約 §3.1](../specs/設計規約.md#31-flyway-マイグレーション)、dev 初回デプロイ前 = Sprint 1 完了 2026-07-11 まで)成立により **`V1.0.0_01__create_tables.sql` への直接追記** に方針転換した。実装は既に V1.0.0 へ 6 テーブル(`tenants` / `user_tenants` / `task_stakeholders` / `audit_logs` / `user_notification_settings` / `shedlock`)が統合済(2026-05-24 確認)。Issue #84 (N2) は完了確認後 close 予定。

---

### G-4. 🟡 `SecurityConfig` — ベース整合だが認可未実装

scaffold:
- ✅ ステートレスJWT認証
- ✅ OAuth2 Resource Server
- ✅ `/actuator/health` `/actuator/info` を permitAll
- ✅ その他全パスに認証要

設計書 §6.2 が要求する追加:
- **メソッドレベル認可**: `@EnableMethodSecurity` + `@PreAuthorize`
- **TenantContext**: ヘッダ `X-Tenant-Id` を `TenantContextHolder` に格納する Filter / Interceptor
- **ロール抽出**: `user_tenants(role)` から `TENANT_ADMIN` / `MEMBER` を取り出して `GrantedAuthority` 化
- **SaaS Admin 判定**: Keycloak realm role `APP_ADMIN`(JWT の `realm_access.roles`)を抽出
- **タスクごとの認可** (§6.2.1): `TaskAuthorizationDomainService`(参照可否・編集可否ロジック)
- **認可違反応答** (§6.2.3): GET=404 / 編集=403 のポリシーを `@RestControllerAdvice` で実装

`TasksJwtAuthenticationConverter` を拡張して、現状の `List.of()`(authorities空)を上記ロール抽出に置き換える。

---

### G-5. 🔴 クリーンアーキテクチャ4層構造の未実装

scaffold は **機能パッケージ構成**(`task/`, `user/`, `security/`)で、各パッケージ内に Entity / Repository / Controller を素直に並べる Spring 標準スタイル。

設計書 v1.3 §2.1.1 が要求する **クリーンアーキ4層**:

| 層 | 期待パッケージ | 現状 |
|---|---|---|
| Domain | `xyz.dgz48.tasks.webapi.domain.{task,tenant,user,stakeholder,audit}` | 不在 |
| UseCase (Application) | `xyz.dgz48.tasks.webapi.usecase.{task,tenant,stakeholder}` | 不在 |
| Adapter | `xyz.dgz48.tasks.webapi.adapter.{web,persistence,external}` | 部分的(現 `task/User.java` 等が persistence 相当) |
| Infrastructure | `xyz.dgz48.tasks.webapi.infra` | 不在(`TasksWebapiApplication` が直下) |

**選択肢**:
1. **設計書に合わせて scaffold をリファクタ**(クリーンアーキ4層に再配置)
2. **scaffold の機能パッケージ構成を維持し設計書を実装に合わせて補正**
3. **段階移行**: 既存 `task/` 等を維持しつつ、新規実装(stakeholder/tenant等)から4層構造を導入。後で旧コードを移行

3 を推奨。Sprint 0 で4層雛形 + ArchUnit ルールを整備し、新規実装を4層で書く。既存3エンティティは Sprint 2 等で段階リファクタ。

> ※本セクションの方針は 2026-05-14 以降に更新済、冒頭ディスクレーマ参照。

---

### G-6. 🟡 `application.yml` — 最小構成

現状: `spring.application.name`, `jpa.open-in-view: false`, `hibernate.ddl-auto: none`, `server.port: 8080`, actuator endpoints のみ。

設計書 v1.3 が前提とする追加設定:
- DataSource(MySQL接続、URL/credentials は Secrets Manager 経由)
- Flyway(`spring.flyway.enabled: true`, `locations: classpath:db/migration`)
- OAuth2 Resource Server(`spring.security.oauth2.resourceserver.jwt.issuer-uri: <Keycloak URL>`)
- ロギング(JSON出力 → CloudWatch Logs)
- Actuator(`/actuator/prometheus` メトリクス、ECS health check 用)

環境別 `application-{local,staging,prod}.yml` の分割も検討。

> ※本セクションの方針は 2026-05-14 以降に更新済、冒頭ディスクレーマ参照。

---

## 推奨アクション

### Sprint 0(2週間想定)で着手:

| Issue | 内容 | 優先度 |
|---|---|---|
| #N1 | `V1.0.1__align_tasks_with_design_v1_3.sql` 追加(tasks拡張) | P0 |
| #N2 | `V1.0.2__add_multitenancy_and_supporting_tables.sql` 追加(6テーブル) | P0 |
| #N3 | `Task.java` を設計に合わせて拡張 | P0 |
| #N4 | クリーンアーキ4層の雛形 + ArchUnit 依存方向ルール | P1 |
| #N5 | `TenantContext` / `TenantContextFilter` 実装 + Hibernate Filter | P0 |
| #N6 | `TasksJwtAuthenticationConverter` にロール抽出を追加 | P1 |
| #N7 | `application.yml` の DataSource / Flyway / OAuth2 issuer 設定 | P0 |
| #N8 | 設計書 §4.2.1 / §5.3 の「既存DB別所」表記を補正 | P2 |

### 別 PR 化を推奨:

各 Issue を 1 PR とし、`feature/N1-align-tasks-schema` 等のブランチで運用。

> ※本セクションの方針は 2026-05-14 以降に更新済、冒頭ディスクレーマ参照。

---

## 良かった点

- **既存scaffold は設計の方向性と概ね整合**(JWT認証、ステートレス、users スキーマ完全一致)
- 設計書 v1.3 の **TasksPrincipal は scaffold 実装と同一フィールド構成** で実装も完了済
- Lombok / NullAway / JSpecify / Spotless 等のツールチェーンが整備済(計画書 §5 と整合)
