# ADR-0006: Keycloak User Storage SPI を実装し、users テーブルを本システム単一 SoT として運用する(writable SPI)

- **Status**: Proposed
- **Date**: 2026-05-24
- **Deciders**: 開発チーム
- **Tags**: security, persistence, integration, keycloak

## 目次

- [1. コンテキスト](#1-コンテキスト)
- [2. 検討した選択肢](#2-検討した選択肢)
  - [選択肢 A: Read-only User Storage SPI(本システム users が SoT)](#選択肢-a-read-only-user-storage-spi本システム-users-が-sot)
  - [選択肢 B': 双方向同期(Keycloak DB と users 両方に user 情報、event listener で同期)](#選択肢-b-双方向同期keycloak-db-と-users-両方に-user-情報event-listener-で同期)
  - [選択肢 D': Writable User Storage SPI(本システム users が SoT、Keycloak は SPI 経由で read/write)](#選択肢-d-writable-user-storage-spi本システム-users-が-sotkeycloak-は-spi-経由で-readwrite)
  - [選択肢 E: Keycloak が user 情報 SoT、本システム users は業務代理キー table のみ + cache](#選択肢-e-keycloak-が-user-情報-sot本システム-users-は業務代理キー-table-のみ--cache)
- [3. 決定](#3-決定)
  - [3.1 SPI スコープと write 範囲](#31-spi-スコープと-write-範囲)
  - [3.2 突合キーと users テーブル schema 変更](#32-突合キーと-users-テーブル-schema-変更)
  - [3.3 Credentials の持ち場](#33-credentials-の持ち場)
  - [3.4 排他制御と削除方針(論理削除 + 個人情報匿名化)](#34-排他制御と削除方針論理削除--個人情報匿名化)
  - [3.5 Cache 戦略(NO_CACHE)](#35-cache-戦略no_cache)
  - [3.6 Keycloak バージョン(26.x)](#36-keycloak-バージョン26x)
  - [3.7 ロールの持ち場(既存方針維持)](#37-ロールの持ち場既存方針維持)
- [4. 理由](#4-理由)
- [5. 影響](#5-影響)
  - [良い影響(Positive)](#良い影響positive)
  - [悪い影響・制約(Negative)](#悪い影響制約negative)
  - [既存ドキュメント・規約・実装への波及](#既存ドキュメント規約実装への波及)
- [6. 実装メモ](#6-実装メモ)
- [7. 参考リンク](#7-参考リンク)

## 1. コンテキスト

infrastructure-plan v5 §5.3 / §3.6 で、本システム(tasks-webapi)は **Keycloak で OIDC 認証** + **本システムが users テーブルを保持** する構成を採用した。Keycloak と本システムの users をどのように連携させるか — 具体的には **どちらを user 情報の Source of Truth (SoT) とするか**、**両者の同期方針** をどうするかが本 ADR の論点である。

現行 scaffold(`webapi/src/main/resources/db/migration/V1.0.0_01__create_tables.sql`)では users テーブルが既に存在し、以下の列を持つ:

- `id BIGINT AUTO_INCREMENT PRIMARY KEY`
- `oidc_sub VARCHAR(255) NOT NULL UNIQUE`(コメント: 「Keycloak Subject」)
- `email VARCHAR(255) NOT NULL`
- `full_name VARCHAR(255) NOT NULL`
- `full_name_kana VARCHAR(255) NOT NULL`(日本語固有 field)
- `department_name VARCHAR(255) NULL`

また `tasks.owner_id` / `tasks.assignee_id` / `task_stakeholders.user_id` 等が `users.id` への `FOREIGN KEY ... ON DELETE RESTRICT` で参照している。

論点は以下の事情で複雑化している:

1. **業務代理キーの維持要件**: 将来 batch 処理・マーケティング機能等で users を含む業務系テーブルを参照する想定があり、業務 entity には **`users.id` という代理キー(BIGINT)** を持たせたい。OIDC sub claim を業務領域(`tasks.owner_*` 等)に直接持ち込むのは避ける(sub は認証認可レイヤーの識別子という整理)。
2. **将来 user データ移行が必要になる可能性**: 関連する別システムからの user データ移行が **将来必要になる可能性が認識されている**(現時点で確定した計画ではない)。本システムの users テーブルは移行のしやすさのため、形を soft に維持できると望ましい(具体的 schema 制約は本 ADR ではなく Flyway migration / #84 / #90 で扱う)。
3. **基本設計書・要件定義書の「既存users」表記の整理**: 基本設計書 §4.2.1 / 要件定義書 §5.3 の「既存DB別所」表記は外部システムを連想させるが、実際は本システム内で先に設計された users を指している。本 ADR で経緯を整理し、Sprint 0 App の #90 で表記補正に繋げる。
4. **Keycloak 構成の参照価値**: 本案件は「Keycloak を使った設計の参照実装」としての価値も認識されている背景があり、Keycloak の機能(Account Console、Admin Console、MFA、Identity Federation、Authorization Services、Event Listener)をできる限り活用する構成を選びたい。

本 ADR は、これら 4 つの制約のもとで Keycloak ↔ users の関係を確定する。

## 2. 検討した選択肢

### 選択肢 A: Read-only User Storage SPI(本システム users が SoT)

- **概要**: 本システム users が user 情報の SoT。Keycloak Custom User Storage SPI が JDBC で users を read のみ。user の作成・編集は本システム API 経由でのみ実施。
- **利点**:
  - SPI 実装が最小(`UserLookupProvider` のみ)
  - SoT 単一、整合性問題が発生しない
  - データ移行は users への INSERT のみで完結
- **欠点**:
  - **Keycloak の機能制限が大きい**: Account Console から profile 編集不可、Admin Console から user CRUD 不可、password reset は本システム実装
  - Identity Federation や MFA は限定的にしか使えない(user の代理表現が read-only だと、Keycloak が自分のものとして扱えない場面が出る)

### 選択肢 B': 双方向同期(Keycloak DB と users 両方に user 情報、event listener で同期)

- **概要**: Keycloak が自身の DB に user 情報を持ち、本システムも users を保持。Keycloak Event Listener と本システム API の両方で双方向同期する。
- **利点**:
  - Keycloak 機能フル活用可能
  - 本システム既存設計を維持
- **欠点**:
  - **典型的な分散システム anti-pattern**: 2 つの writer + 同期 = 競合解決ロジックを永続的に保守
  - イベント順序保証・ロスト event のリカバリ・初期同期 / 全件再同期の運用設計が必要
  - 実装複雑性が最も高い

### 選択肢 D': Writable User Storage SPI(本システム users が SoT、Keycloak は SPI 経由で read/write)

- **概要**: 本システム users が SoT。Keycloak Custom User Storage SPI を **writable** で実装し、Keycloak は SPI 経由で users を直接 read/write する。Account Console / Admin Console からの編集も SPI 経由で users に着地する。
- **利点**:
  - **単一 SoT**(users テーブルのみ)— 双方向同期不要
  - **Keycloak 機能ほぼフル活用**(Account Console、Admin Console、Federation、MFA、Authorization Services)
  - データ移行は users への INSERT で完結
  - 業務 entity は `users.id` 代理キーを保持できる(既存設計温存)
- **欠点**:
  - SPI 実装量が read-only の 2-3 倍(`UserLookupProvider` + `UserRegistrationProvider` + `UserQueryProvider` + `UserAdapter` の writable 実装)
  - 本システム API と Keycloak Console の両方が同じ users 行を更新し得るため、楽観排他の設計が必要
  - SPI 実装に Keycloak の Provider Plugin としての packaging / deploy が必要(Sprint 1 Infra)
  - Keycloak Console での user 作成時、本システム固有 field(`full_name_kana` 等)の入力経路が標準 UI に無い

### 選択肢 E: Keycloak が user 情報 SoT、本システム users は業務代理キー table のみ + cache

- **概要**: user の name / email / credential 等は Keycloak が SoT として保持。本システム users は業務代理キー(`users.id`)+ 表示用 cache(name 等)のみを持ち、Keycloak Event Listener で Keycloak → users へ片方向同期。
- **利点**:
  - Keycloak らしさが最大、典型的なベストプラクティス構成
  - 業務 entity は `users.id` 代理キーを保持できる
- **欠点**:
  - 本システム users は cache 化し、認証情報の責務を持たない(Keycloak の射影として存在)
  - データ移行は Keycloak Admin API 経由で user を inject する必要(JSON 一括 import / PartialImport endpoint)、users への INSERT だけでは完結しない
  - Keycloak 障害時に本システムの user 情報が古くなる(cache 整合の課題)
  - Sprint 1 Infra で Event Listener 実装が必要

## 3. 決定

**採用**: 選択肢 D'(Writable User Storage SPI、本システム users が SoT)

### 3.1 SPI スコープと write 範囲

Keycloak Custom User Storage SPI を Java で実装し、以下のインターフェイスを提供する:

| インターフェイス | 役割 | 実装 |
|---|---|---|
| `UserStorageProvider` | SPI のライフサイクル | 必須(close 等) |
| `UserLookupProvider` | user の lookup(id / username / email) | 必須 |
| `UserQueryProvider` | user の検索・一覧 | 必須(Admin Console の user list 用) |
| `UserRegistrationProvider` | user の作成・削除 | 実装(writable、削除は 3.4 の論理削除に変換) |
| `CredentialInputValidator` | credential 検証 | **実装しない**(Keycloak の標準 credential store に委譲、3.3 参照) |

write 範囲は **フル CRUD**:

| 操作 | SPI 経由 | 本システム API 経由 |
|---|---|---|
| user 作成 | △(Admin Console から、後述の制約あり) | ✓(招待 API、tenant 割当て等を含む業務ロジック) |
| user 属性更新(name、email、locale 等) | ✓(Account / Admin Console から) | ✓(profile 更新 API) |
| user 削除 | ✓(Admin Console から、3.4 の論理削除に変換) | ✓(解約・退会 API) |
| user 読み取り(認証時) | ✓(SPI が users を read) | — |

#### Console 経由 user 作成の方針

Keycloak Admin Console から user を作成する経路は、業務的なメタデータ(招待コード、tenant 割当て、`full_name_kana` 等の本システム固有 field)を伴わない。本 ADR では:

- **主経路は本システム招待 API**: 業務的な user 作成(招待コード送信、初回 tenant 割当て、`full_name_kana` 入力含む)は本システム API 経由で実施する
- **Console 経由は管理者運用 / 障害時リカバリ用**: SPI の `addUser` が呼ばれた場合、必須項目のうち Keycloak が標準で提供しないもの(`full_name_kana` 等)は **Keycloak User Attributes 機能経由で受け付ける**(realm 設定で Custom User Profile を有効化、Admin Console で必須 attribute として表示)。Custom User Profile は attribute の契約(name / validation)定義のみを担い、**保存先は SPI の `UserAdapter#setSingleAttribute` 等で `users.full_name_kana` 列に書き込む**(Keycloak のデフォルト `user_attribute` テーブルは使わない)
- **tenant 割当ては自動化しない**: Console 経由作成では `user_tenants` への INSERT は行わず、**user は tenant 未所属の状態で作成される**(business 側で別途 invite or assignment 操作が必要)。これは「Console 経由作成は例外運用」として受け入れる
- **詳細なバリデーション(`full_name_kana` の文字種制約等)**は Sprint 1 Infra の SPI 実装で対応、必要なら派生 ADR で確定

### 3.2 突合キーと users テーブル schema 変更

#### 既存 oidc_sub 列の用途確定

現行 scaffold の `users.oidc_sub VARCHAR(255) NOT NULL UNIQUE` を **本 ADR で「Keycloak の `sub` claim を保存する突合キー」と正式に確定**する(これまでコメントレベルで「Keycloak Subject」とのみ記載されていた)。

- VARCHAR(255) の長さは OIDC Core 1.0 §2 で `sub` の制約「255 ASCII characters 以内」に準拠し、現状維持
- Keycloak 内部発行 sub は UUID 形式(36 文字)、外部 IdP brokering 時は任意長(255 文字以内)を受け入れる
- 既存 unique index(`uq_users_oidc_sub`)を Keycloak SPI の lookup index として活用

#### 追加が必要な列(Sprint 0 App #84 / #90 のスコープで実施)

| 列名 | 型 | 制約 | 用途 |
|---|---|---|---|
| `version` | BIGINT(Java `Long`) | NOT NULL DEFAULT 0 | JPA `@Version`、楽観排他用(3.4 参照) |
| `deleted_at` | DATETIME | NULL | 論理削除日時。Keycloak Console からの delete を物理削除でなく論理削除に変換するため(3.4 参照) |

これらは ALTER TABLE で追加する。既存 FK 制約(`tasks.owner_id` 等の `ON DELETE RESTRICT`)および既存 UNIQUE 制約(`uq_users_oidc_sub`、`email` 等)はそのまま維持する(削除時の匿名化は placeholder 方式で UNIQUE 整合を保つ、3.4 参照)。

#### Keycloak ↔ users.id 解決フロー

1. Keycloak が OIDC 認証成功 → JWT に `sub` claim 発行
2. 本システム API が JWT を受信 → JwtAuthenticationConverter で `sub` を抽出
3. `users.oidc_sub = :sub AND users.deleted_at IS NULL` で lookup → `users.id` を解決
4. 業務 entity は `users.id` で操作

データ移行で旧システムから移行した user は旧 sub を持たないため、**初回ログイン時に Keycloak が新 sub を発行 → SPI 経由で users.oidc_sub に保存** する運用にする(あるいは移行スクリプトで Keycloak Admin API 経由に sub を割り当てる方針も可、運用設計時に詳細決定)。

### 3.3 Credentials の持ち場

- **Credentials(password、MFA factor、WebAuthn 等)は Keycloak が SoT として保持**
- SPI は `CredentialInputValidator` を実装しない
- これにより:
  - MFA / WebAuthn / Federation 等の Keycloak credential 機能をフル活用できる
  - 本システム users テーブルに password_hash 列を持たない(セキュリティ攻撃面の縮小)
  - データ移行で旧システムの password を引き継ぐことは不可 → **初回ログイン時にパスワードリセット**運用にする

パスワードリセットの reset link TTL / リマインダ送信回数 / リセット未完了 user の扱い等の運用詳細は、本 ADR のスコープ外として **データ移行運用設計の別 Issue**(Phase 2 移行準備時に起票)で扱う。

### 3.4 排他制御と削除方針(論理削除 + 個人情報匿名化)

#### 楽観排他(@Version)

users への書き込みは本システム API と Keycloak Console(SPI 経由)の 2 経路があるため、楽観排他で競合を検知する:

- 3.2 で追加する `users.version BIGINT NOT NULL DEFAULT 0` 列を使用
- JPA エンティティに `@Version` 注釈付き `Long version` field を追加
- 競合時は HTTP 409 Conflict / OptimisticLockException を返却(本システム API)/ SPI 経由の write はエラー応答(Keycloak Console 上で再操作を促す)
- 競合確率は低い前提で MVP は楽観排他のみ、頻発する場合は将来悲観排他や application-level の coordination を再検討

#### 削除方針(論理削除 + 個人情報匿名化)

`tasks.owner_id` / `tasks.assignee_id` / `task_stakeholders.user_id` 等が `users.id` への `FOREIGN KEY ... ON DELETE RESTRICT` で参照しているため、user の物理削除は FK 違反でエラーとなる(既存制約)。一方で、論理削除のみでは個人特定情報(email、full_name 等)が永続的に残るため、GDPR / 日本個人情報保護法の削除要件に対応できない。

本 ADR では **論理削除 + 個人情報匿名化** を採用する:

- **物理削除は MVP で提供しない**(ADR-0005 で物理削除を Phase 2 #167 テナント解約に統合した方針と整合、FK 整合の業務的価値を優先)
- Keycloak Console / SPI の `removeUser` 呼び出し、および本システム API 経由の解約・退会は **下記 anonymize 処理を 1 transaction で実行**:
  1. `users.deleted_at = NOW()`
  2. `users.email = '__deleted__' || users.id || '@deleted.invalid'`(placeholder で UNIQUE 制約維持)
  3. `users.oidc_sub = '__deleted__' || users.id`(placeholder で UNIQUE 制約維持)
  4. `users.full_name = '__deleted__'`
  5. `users.full_name_kana = '__deleted__'`
  6. `users.department_name = NULL`
  7. `users.version` を `@Version` により自動 increment
  8. `audit_logs` に `action = 'ANONYMIZE'`、`entity_type = 'users'`、`entity_id = users.id` を記録(個人情報削除の証跡として)
- SPI の `UserLookupProvider` は `deleted_at IS NOT NULL` の user を返さない(認証拒否につながる、再ログイン不可)
- Keycloak 側の credential / session は Keycloak 標準の `removeUser` 後処理で消える(SPI の責務外、SPI は `removeUser` で true を返して Keycloak の標準 cleanup に委ねる)
- `users.id` は維持されるため、業務 entity(`tasks.owner_id` 等)の参照整合は保たれる。UI では `deleted_at IS NOT NULL` の場合「削除済みユーザー」と表示する

#### 再登録ポリシー(削除済 user の OIDC 再ログイン時)

匿名化済 user の同一人物が OIDC 再ログイン(別 sub での federation、または新規 email での再登録)を試みた場合、本 ADR では:

- **新規 user として users 行を作成**(過去の業務履歴は別 `users.id` として保持、業務的に「別人」扱い)
- 過去 row(`deleted_at IS NOT NULL`、placeholder 化済)とは link しない
- 「退会したけど戻ってきた user の履歴を引き継ぎたい」要件は本 ADR では未対応(必要時は Phase 2 で再検討)

これにより:
- GDPR 観点で「削除」が機能している証跡が明確(過去 row は技術的にも復元不可)
- 実装が単純(再登録は完全な新規 user 作成パス)
- 業務的な「別人扱い」のトレードオフは UX で吸収(画面の用語選定等)

#### placeholder 方式を採用する理由

`email` / `oidc_sub` は UNIQUE 制約を持つため、単純 NULL 化すると複数 user の同時削除で NULL 重複は許容されるが運用上の混乱が起きる(複数の `deleted_at IS NOT NULL` レコードを email = NULL で検索するなど)。`users.id` を埋め込んだ placeholder 形式にすることで:

- UNIQUE 制約を変更せず維持できる(schema 変更最小)
- 個別の削除済 user を内部的に識別可能(追跡用途、外部からは見えない)
- 「削除済 user の身元特定」には `users.id` から business データ(tasks.owner_id 経由)で追える危険性が残るが、本 ADR では「`users.id` は技術的識別子であり外部公開しない」運用前提で許容(完全な GDPR 対応は Phase 2 #167 で物理削除も含めて再検討)

#### 検討した却下案(FK 制約見直しによる物理削除)

| 案 | 概要 | 却下理由 |
|---|---|---|
| α: ON DELETE CASCADE | user 削除 → 関連 tasks / stakeholders 全削除 | 業務データ消失甚大、退職者の owner task が突然消える |
| β: ON DELETE SET NULL | user 削除 → owner_id / assignee_id を NULL | `tasks.owner_id` NOT NULL なので不可、`task_stakeholders.user_id` は PK のため不可 |
| γ: SET DEFAULT(`__deleted_user__` 予約 user 集約) | 関連列を予約 user に付け替え後 DELETE | 個別 user の業務履歴追跡不可、本案 η と本質的に同じだが UX 違和感大 |
| δ: オーナー譲渡前提の物理削除 | 退会前に owner 引き継ぎフロー必須 → 物理削除 | UX 負担、突然退会・放置 user に対応不可、引き継ぎロジック設計コスト |

### 3.5 Cache 戦略(NO_CACHE)

Keycloak には複数の cache レイヤーが存在する。本 ADR では:

- **Storage Provider Cache Policy = `NO_CACHE`**(本 ADR 決定範囲)
  - Custom User Storage Provider が users を lookup する都度 DB hit
  - cache 整合性の責務を持たない(他 writer の変更を見逃さない)
  - users は PK / unique key lookup でアクセスされ、コストは数 ms 程度
  - 「Cache が見える」ことによる運用上の違和感を排除
- **User Cache(Infinispan)/ Realm Cache は Keycloak デフォルト挙動を維持**(本 ADR 決定範囲外)
  - これらは Keycloak の login / token 発行性能に関わる別レイヤーで、SPI レベルの NO_CACHE 設定とは独立

性能影響が観測された場合は Storage Provider Cache Policy を `MAX_LIFESPAN`(秒単位 TTL)へ切り替えを検討する。Sprint 2 Infra の監視設計(infrastructure-plan v5 §7)で以下のメトリクスを観測対象に含める:

- SPI lookup count(`UserLookupProvider#getUserByUsername` 等の呼び出し回数 / 秒)
- SPI lookup latency p50 / p95 / p99
- Keycloak login flow E2E latency(authentication endpoint の総応答時間)

### 3.6 Keycloak バージョン(26.x)

- 本 ADR 受理時点で **Keycloak 26.x 系の最新安定版**を採用
- 現状 Setup 1 #214 で構築した docker-compose.local.yml は `quay.io/keycloak/keycloak:24.0` を指定しているため、本 ADR 実装(Sprint 1 Infra)で **26.x への upgrade** を実施
- realm JSON export(`keycloak/realm-export/tasks-realm.json`)は 24.x → 26.x で多くが互換だが、import 時のフォーマット差異(`accessCodeLifespan` 等の field 追加)があれば import 検証で吸収する
- Sprint 1 Infra の Keycloak Custom Image 実装(infrastructure-plan v5 §5.3)で 26.x を base image とする

### 3.7 ロールの持ち場(既存方針維持)

#### Role の格納場所

- **SaaS Admin**: Keycloak realm role `APP_ADMIN` で管理(realm-level、user 単位、Keycloak が SoT)
- **Tenant Admin / Member**: 本システム `user_tenants.role` で管理(tenant-user 関係単位、users とは独立)

これは ADR-0005(タスク認可は 3 役割のみで評価)および ADR-0004(tenants 監査列例外)と整合する。

#### SPI の Role 返却方針

SPI の `UserAdapter#getRoleMappingsStream()` / `getRealmRoleMappingsStream()` は **空 stream を返す**:

- `APP_ADMIN` の付与は Keycloak realm role mapping(`keycloak_role_mapping` テーブル等、Keycloak が自身の DB で管理)で完結する
- Tenant Admin / Member は本システム `user_tenants` 経由でアクセス時の認可で評価され、Keycloak の role mapping には載らない
- SPI が role を返さないことで、Keycloak は user の role 評価を Keycloak 自身の標準 role mapping のみで完結できる(`APP_ADMIN` の有無)

これにより `user_tenants` の role を SPI から扱う必要がなく、SPI の責務が users 属性のみに絞られる。

#### Alternatives(本案件では却下、Phase 2 検討候補)

Keycloak の能力を最大活用する観点での別案として以下があるが、本案件の規模(tenant 数 100〜1000 想定)と運用負担を考慮して却下する:

- **Keycloak Authorization Services での tenant role 管理**: 各 tenant を Keycloak の Resource として宣言し、`TENANT_ADMIN` / `MEMBER` を Permission / Policy で表現。利点は認可情報の Keycloak 一元管理、欠点は tenant 数増で realm が肥大化
- **Keycloak Groups での tenant 表現**: 各 tenant を Keycloak Group(例: `tenant-{tenantId}`)、user の所属を group membership で管理。Console から所属管理可能だが本システム `user_tenants` テーブルとの二重 SoT が発生
- どちらも本案件では却下するが、tenant 数が小〜中(数十程度)の案件では有力候補となるため記録する

## 4. 理由

- **単一 SoT**: 双方向同期(選択肢 B')の競合解決を回避し、users テーブルを唯一の真実として保つ
- **既存業務代理キー設計と Keycloak 機能活用の両立**: 業務 entity が `users.id` を保持する設計を温存しつつ、Keycloak の Account/Admin Console や MFA / Federation を活用できる
- **データ移行のしやすさ**: users への INSERT だけで完結(Keycloak Admin API 経由の inject が不要)
- **MVP の実装スコープ妥当性**: 実装複雑性は read-only の 2-3 倍だが、機械的な実装で済み(JPA + Keycloak Provider Plugin)、Sprint 1 Infra のスコープに収まる

## 5. 影響

### 良い影響(Positive)

- users テーブルが唯一の SoT になり、データ整合性の責任が明確
- Keycloak の機能(Account / Admin Console、MFA、Identity Federation、Authorization Services、Event Listener)が活用可能
- 認証 credential を本システム DB に持たない(攻撃面縮小)
- 業務代理キー設計が温存され、batch / マーケティング機能等の将来要件と整合
- **削除時の個人情報匿名化(3.4)により GDPR / 日本個人情報保護法の削除要件に対応可**(MVP から)
- FK 整合維持(`ON DELETE RESTRICT` のまま)により業務データの履歴・監査追跡が壊れない
- 本案件の SPI 実装ノウハウは「既存業務 DB + Keycloak 機能」を両立する数少ない実例として、将来の他案件の参考にもなり得る

### 悪い影響・制約(Negative)

- SPI 実装が必要(read-only の 2-3 倍のコード量、Sprint 1 Infra でカスタム Java Provider Plugin として packaging / deploy)
- Keycloak Console と本システム API の両方が users を書ける構造のため、楽観排他制御の実装が必要(3.4 参照)
- Keycloak Console 経由の user 作成は tenant 未所属で着地する(本システム業務的には例外運用、Console は管理者運用 / 障害時リカバリ用と位置付け)
- 旧システムから移行した user の password は引き継げず、**初回ログイン時にリセット**運用が必須
- Keycloak Custom Image のビルド・配信パイプライン整備が Sprint 1 / Sprint 2 Infra のスコープに追加される
- Keycloak バージョン upgrade(本 ADR では 24 → 26、将来も)時に SPI が API 互換性を維持する責任が発生(major version 跨ぎ時に SPI 再ビルド・再検証)
- 削除時の匿名化処理(email / oidc_sub / full_name 等の placeholder 化)実装が必要、SPI `removeUser` と本システム API の両方で同一ロジック適用(共通サービス層に切り出し)
- 業務画面で `deleted_at IS NOT NULL` の user が「削除済みユーザー」として表示される(tasks 履歴等で散見)、UI 側で適切な表示処理が必要
- **削除済 user の再登録時は新規 user として作成され、過去の業務履歴は引き継がれない**(3.4 再登録ポリシー参照)— 「退会後復帰したい」業務要件があれば Phase 2 で別案検討
- 完全な物理削除(GDPR 完全対応)は MVP で提供しない、Phase 2 #167 テナント解約で再評価

### 既存ドキュメント・規約・実装への波及

- `docs/architecture/infrastructure-plan.md` v5 §5.3(Keycloak User Storage SPI)/ §3.6(Keycloak 構成)/ §3.4(Native Image との関係、Keycloak は Native 対象外)に本 ADR への参照を追加
- 基本設計書 §4.2.1 / 要件定義書 §5.3 の「既存usersテーブル / 既存DB別所」表記を、本 ADR の整理(本システム内の新規 users であり、外部「既存 DB」を指すものではない)に基づいて補正 — **Sprint 0 App #90** のスコープで実施
- 設計規約 / コーディング規約: 本 ADR は新たな実装制約(JPA `@Version` 列の追加、`deleted_at` 列の追加、論理削除運用)を導入するが、規約レベルの記述は不要(Flyway migration と JPA エンティティで表現)
- `docker-compose.local.yml`: Keycloak image を `quay.io/keycloak/keycloak:24.0` → `26.x` の最新安定版 tag に変更(Sprint 1 Infra 実装時)
- `keycloak/realm-export/tasks-realm.json`: 26.x で再 export または互換性検証して必要箇所だけ調整。Custom User Profile(`full_name_kana` 等の必須 attribute)設定を追加
- Flyway migration: 既存 `V1.0.0_01__create_tables.sql` の users テーブルに対し、**新規 migration ファイル**(`V1.x.x_NN__alter_users_add_version_deleted_at.sql` 等)で以下を追加 — **Sprint 0 App #90 のスコープを ADR-0006 派生作業として拡張し、docs 補正 + code (migration / JPA エンティティ) 両方を扱う**:
  - `ALTER TABLE users ADD COLUMN version BIGINT NOT NULL DEFAULT 0`
  - `ALTER TABLE users ADD COLUMN deleted_at DATETIME NULL`
- JPA エンティティ `User.java`: `@Version` 注釈付き `version` field と `deletedAt` field を追加。匿名化処理は domain service として実装(SPI と本システム API の両方から呼び出し)
- 既存 unique index `uq_users_oidc_sub` および `email` の UNIQUE 制約は維持(匿名化時 placeholder 形式で uniqueness 衝突回避)
- 業務画面側(Sprint 1 App 以降): `users.deleted_at IS NOT NULL` の参照を「削除済みユーザー」として表示する UI 処理を追加(Frontend / OpenAPI レスポンス DTO 側で対応)

## 6. 実装メモ

### 実装順序

1. **本 ADR の Accepted 化**(本 PR で実施)
2. **#90 のスコープ拡張で 3 点を同 Issue 内で実施**(Sprint 0 App、ADR-0006 派生作業として):
   - 基本設計書 / 要件定義書の「既存usersテーブル」表記補正(本 ADR で整理した内容に基づく)
   - Flyway migration で `users.version` / `users.deleted_at` 列を追加
   - JPA エンティティ `User.java` に `@Version Long version` field と `LocalDateTime deletedAt` field を追加
3. **匿名化 domain service の実装**(本 ADR 受理後、#90 完了に続けて Sprint 0 App か Sprint 1 Infra のいずれかで):
   - SPI と本システム API の両方から呼び出せる domain service として実装(命名は実装時、例: `UserAnonymizationDomainService`)
   - 3.4 の 8 ステップ匿名化処理を 1 transaction で実行
4. **Sprint 1 Infra で Keycloak Custom Image + Writable User Storage SPI 実装**(infrastructure-plan v5 §5.3 配下の Issue として起票):
   - Keycloak Provider Plugin としての Maven/Gradle ビルド構成
   - `UserStorageProvider` / `UserLookupProvider` / `UserQueryProvider` / `UserRegistrationProvider` 実装
   - `UserAdapter` の transaction 境界と JPA セッションのライフサイクル管理
   - SPI 経由の create 時の挙動(Console から作成された user は tenant 未所属で着地、`full_name_kana` 等は Custom User Profile attribute 経由で受け付け、保存は `UserAdapter` 経由で `users` 列へ)
   - SPI 経由の delete 時の挙動(上記 3 の匿名化 domain service を呼び出し)
   - keycloak/realm-export/tasks-realm.json を 26.x 対応に更新(**Custom User Profile 設定で `full_name_kana` を必須 attribute として定義する** 設定含む)
   - docker-compose.local.yml の Keycloak image tag を 26.x に変更
   - E2E ログイン確認: 本システム API 経由で user 作成 → Keycloak ログイン → SPI 経由で user lookup → JWT 発行 → 本システム API で `users.id` 解決
5. **Sprint 1 App で OIDC 認証統合**(#87 / #88 等で対応)

### 検証方法

- **SPI 単体テスト**: Testcontainers で Keycloak + MySQL を起動し、Keycloak Admin REST API 経由で SPI が users テーブルを正しく read/write することを検証
- **E2E**:
  - Account Console から profile 編集 → users テーブルに反映を確認
  - Admin Console から user 作成 → users テーブルに行追加を確認(tenant 未所属を確認)
  - Admin Console から user 削除 → `users.deleted_at` 更新と次回認証時の拒否を確認
  - 削除時の匿名化検証: 削除後の users 行で `email` / `oidc_sub` / `full_name` 等が placeholder / `__deleted__` に置換されていることを確認、UNIQUE 制約違反が起きないことを確認(同一 transaction 内で複数 user を削除する E2E テスト)
  - 匿名化後の Keycloak session terminate: 削除直前まで active だった user の既存 access token / refresh token が、削除直後の token validation / refresh で拒否されることを確認(Keycloak 側の session 失効が SPI removeUser の返り値 true により正しくトリガされる挙動の検証)
  - audit_logs に `ANONYMIZE` action が記録されていることを確認
  - 削除後の業務 entity 表示: 削除済 user が owner だった task を別 user が閲覧 → 「削除済みユーザー」と表示されることを確認(Sprint 1 App 以降)
- **楽観排他**: 本システム API と Keycloak Console から同 user を同時編集 → 一方が 409 / OptimisticLockException で失敗することを確認
- **Performance**: SPI 経由の login flow で users lookup レイテンシを計測(3.5 のメトリクスを Sprint 2 Infra 監視設計で実装し、NO_CACHE 採用の妥当性を検証)

### Phase 2 検討項目(本 ADR 派生)

- パスワードリセット運用詳細(reset link TTL、リマインダ、未完了 user の扱い)→ データ移行運用設計の別 Issue で
- 完全な物理削除(GDPR Right to Erasure 完全対応)→ Phase 2 #167 テナント解約と統合。MVP の論理削除 + 匿名化(3.4)で個人特定情報削除要件はカバー、physical row 削除が真に必要なケース(EU 法対応の本格化、外部監査要件等)が発生時に検討
- 削除済 user の `users.id` 経由追跡が business データから可能な点(3.4 placeholder 方式の許容トレードオフ)を Phase 2 で再評価
- 削除済 user の再登録時の **過去履歴引き継ぎ機能**(3.4 再登録ポリシー X 案の補完)→ 業務要件が顕在化した時に検討、本人申告 + 管理者承認のワークフローで `users.id` を意図的に link する別 table 追加等の設計案
- 3.7 Alternatives 記載の Keycloak Authorization Services / Groups 採用は、tenant 規模が小〜中の別案件で再検討

## 7. 参考リンク

### 本リポジトリ内

- `docs/architecture/infrastructure-plan.md` v5 §3.6(Keycloak 構成)/ §5.3(User Storage SPI)
- `docs/specs/基本設計書.md` §4.2.1(既存usersテーブルの表記、本 ADR で整理 → #90 で補正)
- `docs/specs/要件定義書.md` §5.3(既存DB別所、本 ADR で整理)
- `webapi/src/main/resources/db/migration/V1.0.0_01__create_tables.sql`(現行 users / 関連 FK 定義)
- `webapi/src/main/java/xyz/dgz48/tasks/webapi/user/User.java`(現行 JPA エンティティ)
- ADR-0001(意思決定の記録方法)
- ADR-0003(shared パッケージを OPEN モジュールとする)
- ADR-0004(tenants テーブルを監査列例外とする)
- ADR-0005(タスク認可は 3 役割のみ)
- 隣接 ADR(本 ADR と並行起票):
  - ADR-0007(#221、RDS MySQL vs Aurora 選定 — users テーブルの永続化基盤)
  - ADR-0008(#223、GraalVM Native Image 採用 — Keycloak は Native 対象外の整理に関連)
- 関連 Issue:
  - #220(本 ADR の起票元)
  - #214(Setup 1 Keycloak Realm 設計、本 ADR の Sprint 1 Infra で 26.x へ更新)
  - #90(基本設計書 既存usersテーブル記述補正、本 ADR 受理後にスコープ拡張で `users.version` / `deleted_at` 列追加 + JPA エンティティ更新も含める)
  - #87 / #88(Sprint 0 App で TenantContext / JwtAuthenticationConverter 実装)
  - #167(Phase 2 テナント解約、本 ADR の物理削除完全対応はここに統合して再検討)

### 外部リファレンス

- [Keycloak Server Developer Guide — User Storage SPI](https://www.keycloak.org/docs/latest/server_development/index.html#_user-storage-spi)
- [Keycloak Server Administration Guide — User Federation](https://www.keycloak.org/docs/latest/server_admin/index.html#_user-storage-federation)
- [Keycloak 26 Release Notes](https://www.keycloak.org/2024/10/keycloak-2600-released)(最新安定版)
- [JPA `@Version` Optimistic Locking](https://jakarta.ee/specifications/persistence/3.2/jakarta-persistence-spec-3.2#a14945)(Jakarta Persistence)
- [OpenID Connect Core 1.0 §2 — sub claim length constraint](https://openid.net/specs/openid-connect-core-1_0.html#IDToken)
