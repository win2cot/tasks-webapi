# 新 feature 追加手順テンプレート

tasks-webapi

Version 1.0

2026-05-31

作成者: 開発チーム

## 改訂履歴

| 版数 | 改訂日 | 改訂内容 | 改訂者 |
|---|---|---|---|
| 1.0 | 2026-05-31 | 新規作成(Issue #274) | 開発チーム |

## 目次

- [1. 概要](#1-概要)
- [2. パッケージ構造](#2-パッケージ構造)
- [3. package-info.java テンプレート](#3-package-infojava-テンプレート)
- [4. 各層の責務境界](#4-各層の責務境界)
  - [4.1 domain 層](#41-domain-層)
  - [4.2 usecase 層](#42-usecase-層)
  - [4.3 adapter.web 層](#43-adapterweb-層)
  - [4.4 adapter.persistence 層](#44-adapterpersistence-層)
  - [4.5 adapter.external 層](#45-adapterexternal-層)
  - [4.6 infra 層](#46-infra-層)
- [5. shared 参照ルール](#5-shared-参照ルール)
- [6. @Transactional 境界](#6-transactional-境界)
- [7. ApplicationModules.verify() 対応](#7-applicationmodulesverify-対応)
- [8. 参照実装: GET /api/tasks/{id}](#8-参照実装-get-apitasksid)
- [9. 追加チェックリスト](#9-追加チェックリスト)
- [10. 関連ドキュメント](#10-関連ドキュメント)

---

## 1. 概要

本書は **tasks-webapi に新しい feature を追加するときの標準手順** をまとめたテンプレートである。

アーキテクチャは「Spring Modulith による feature-by-package + 各 feature 内部でクリーンアーキテクチャ4層」を採用している(詳細: [設計規約.md §1.1](../specs/設計規約.md#11-採用するアーキテクチャスタイル))。本書はその規約を実装作業に落とし込むための手順書であり、[GET /api/tasks/{id} の実装(Issue #273)](#8-参照実装-get-apitasksid) を参照実装として参照する。

---

## 2. パッケージ構造

新 feature を `<feature>` とするとき、以下のサブパッケージを切る。

```text
xyz.dgz48.tasks.webapi.<feature>
├── package-info.java              ← @NullMarked(必須)
├── domain
│   └── package-info.java
├── usecase
│   └── package-info.java
├── adapter
│   ├── package-info.java
│   ├── web
│   │   ├── package-info.java
│   │   └── dto
│   │       └── package-info.java
│   ├── persistence
│   │   └── package-info.java
│   └── external                  ← 外部 API 連携がある場合のみ
│       └── package-info.java
└── infra                         ← feature 固有の Spring 設定が必要な場合のみ
    └── package-info.java
```

**依存方向**: `infra` → `adapter.*` → `usecase` → `domain`。逆方向は禁止。

---

## 3. package-info.java テンプレート

すべてのパッケージに `package-info.java` を置き、`@NullMarked` を付与する([コーディング規約.md §2.1](../specs/コーディング規約.md))。

**feature トップレベル**(`xyz.dgz48.tasks.webapi.<feature>`)

```java
@NullMarked
package xyz.dgz48.tasks.webapi.<feature>;

import org.jspecify.annotations.NullMarked;
```

**サブパッケージ**(domain / usecase / adapter / adapter.web / adapter.web.dto / adapter.persistence / adapter.external / infra)

```java
@NullMarked
package xyz.dgz48.tasks.webapi.<feature>.<subpackage>;

import org.jspecify.annotations.NullMarked;
```

> `shared` パッケージのみ `@ApplicationModule(type = Type.OPEN)` を追加する(下記 §5)。通常 feature では不要。

---

## 4. 各層の責務境界

### 4.1 domain 層

- POJO のみ。Spring アノテーション・JPA アノテーション禁止。
- 配置するもの: エンティティ(record / class)、値オブジェクト、ドメインサービス、リポジトリインターフェース(Port の定義)、業務例外。
- JPA アノテーションはここに書かない。`adapter.persistence` の `~JpaEntity` に書く。

```java
// domain/MyDomainEntity.java — POJO
public record MyDomainEntity(Long id, Long tenantId, String name) {}

// domain/MyDomainException.java — 業務例外
public class MyDomainException extends DomainException {
  public MyDomainException(Long id) {
    super("my-entity not found: " + id);
  }
}
```

### 4.2 usecase 層

- ユースケース実装クラス(`@Service`)と Port インターフェース(リポジトリ I/F など)を置く。
- **`@Transactional` はこの層のメソッドにのみ付与**(`readOnly` 常に明示 — §6)。
- Port インターフェースは `usecase` パッケージに置き、実装は `adapter.persistence` に置く。

```java
// usecase/MyRepository.java — Port
public interface MyRepository {
  Optional<MyDomainEntity> findById(Long id);
}

// usecase/GetMyEntityUseCase.java
@Service
@RequiredArgsConstructor
public class GetMyEntityUseCase {

  private final MyRepository myRepository;

  @Transactional(readOnly = true)
  public MyDomainEntity execute(Long id) {
    return myRepository.findById(id).orElseThrow(() -> new MyDomainException(id));
  }
}
```

### 4.3 adapter.web 層

- REST Controller と DTO を置く。DTO は `adapter.web.dto` サブパッケージに置く。
- Controller はユースケースを呼び出し、DTO への変換のみ行う。業務ロジックを書かない。
- DTO は `record` を使用し、`static MyDto from(DomainEntity entity)` ファクトリメソッドで変換する。

```java
// adapter/web/MyController.java
@RestController
@RequestMapping("/api/my-resources")
@RequiredArgsConstructor
public class MyController {

  private final GetMyEntityUseCase getMyEntityUseCase;

  @GetMapping("/{id}")
  public MyResponse get(@PathVariable Long id, TasksAuthenticationToken token) {
    MyDomainEntity entity = getMyEntityUseCase.execute(id);
    return MyResponse.from(entity);
  }
}

// adapter/web/dto/MyResponse.java
public record MyResponse(Long id, Long tenantId, String name) {

  public static MyResponse from(MyDomainEntity entity) {
    return new MyResponse(entity.id(), entity.tenantId(), entity.name());
  }
}
```

- 認可違反・リソース不在の例外を HTTP ステータスにマッピングする `@RestControllerAdvice` (または `@ExceptionHandler`) を `adapter.web` に置く(設計規約 §2.3: 参照系 404 / 更新系 403)。

### 4.4 adapter.persistence 層

- JPA Entity(`~JpaEntity`) と Spring Data JPA Repository、Port 実装(`~JpaRepositoryAdapter`)を置く。
- JPA Entity のクラス名は `~JpaEntity` で終える(domain の POJO と区別するため)。
- `~JpaRepositoryAdapter` は package-private クラスとし、Port インターフェース経由でのみ参照させる。
- マルチテナント対応: 業務テーブルの JPA Entity には `@Filter` で Hibernate Filter を有効化する([ADR-0010](../adr/0010-tenant-isolation-hibernate-filter.md))。

```java
// adapter/persistence/MyJpaEntity.java
@Entity
@Table(name = "my_table")
@Filter(name = TenantFilteredEntity.TENANT_FILTER_NAME,
        condition = "tenant_id = :tenantId")
public class MyJpaEntity extends TenantFilteredEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private Long tenantId;

  @Column(nullable = false)
  private String name;
  // ...
}

// adapter/persistence/MyJpaRepositoryAdapter.java — package-private
@Component
@RequiredArgsConstructor
class MyJpaRepositoryAdapter implements MyRepository {

  private final MyJpaRepository jpaRepository;

  @Override
  public Optional<MyDomainEntity> findById(Long id) {
    return jpaRepository.findById(id).map(this::toDomain);
  }

  private MyDomainEntity toDomain(MyJpaEntity entity) {
    return new MyDomainEntity(entity.getId(), entity.getTenantId(), entity.getName());
  }
}
```

### 4.5 adapter.external 層

- 外部サービス(SES、Keycloak Admin API 等)のクライアントを置く。
- 外部 API 連携がない feature では作成不要。

### 4.6 infra 層

- feature 固有の Spring 設定クラスを置く。Hibernate Filter 定義など。
- feature に固有でない設定は `shared.infra` に置く。
- feature 固有の設定が不要な場合は作成しない。

---

## 5. shared 参照ルール

`shared` は Open module として宣言されており、全 feature から自由に参照できる([ADR-0003](../adr/0003-shared-package-as-open-module.md)、[設計規約.md §1.3.1](../specs/設計規約.md#131-shared-パッケージの扱いopen-module))。

```java
// shared/package-info.java — 参照のみ、変更しない
@org.springframework.modulith.ApplicationModule(type = org.springframework.modulith.ApplicationModule.Type.OPEN)
@org.jspecify.annotations.NullMarked
package xyz.dgz48.tasks.webapi.shared;
```

**参照してよいもの**:

- `shared.exception.DomainException` — 業務例外の基底クラス
- `shared.web.ErrorResponse` / `shared.web.ErrorCode` — エラーレスポンス型
- `shared.domain.TenantContext` — 現在テナント ID の取得
- `shared.adapter.persistence.TenantFilteredEntity` — Hibernate Filter 基底クラス

**やってはいけないこと**:

- 業務ロジックを `shared` に逃がすこと
- Spring Bean / Service / Repository / JPA Entity を `shared` に追加すること
- `shared` の内部型を使って feature 間依存を迂回すること

新しい共通型が必要なときは、新規 feature の起票を検討し、規約改定 ADR を起票する。

---

## 6. @Transactional 境界

[コーディング規約.md §11](../specs/コーディング規約.md#11-トランザクション) の要点をまとめる。

| ルール | 内容 |
|---|---|
| 付与場所 | `usecase` 層のメソッドのみ。Controller / Repository / Domain には付けない |
| readOnly 常に明示 | `@Transactional(readOnly = true)` / `@Transactional(readOnly = false)` — 暗黙の Spring デフォルト依存禁止 |
| クラスレベル禁止 | `@Transactional` をクラスレベルに付与しない。必ずメソッドレベルで明示 |
| read-then-write | 単一 usecase 内で完結。別 `@Transactional` の service/usecase を入れ子で呼ばない |

```java
// 正しい例
@Transactional(readOnly = true)
public MyDomainEntity find(Long id) { ... }

@Transactional(readOnly = false)
public void update(Long id, String name) { ... }

// 禁止例
@Transactional          // クラスレベル禁止
public class MyUseCase {
  public void find() {} // readOnly 未明示 — 禁止
}
```

---

## 7. ApplicationModules.verify() 対応

- 既存の `ModularityTests.verifyModularity()` が CI で実行されており、feature 間の不正参照を検知する。
- 新 feature を追加した際は **`ModularityTests` は変更不要**。`verify()` が自動的に新 feature を検出する。
- 他 feature への依存が必要な場合(イベント連携以外)、`@ApplicationModule(allowedDependencies = ...)` を `package-info.java` に明示する。
- feature 間連携は Spring Modulith の `@ApplicationModuleListener`(イベント)または `@NamedInterface` 経由のみ許可。他 feature の `internal` 配下への直接参照は禁止。

```java
// 他 feature への依存を許可する例(通常は不要)
@ApplicationModule(allowedDependencies = {"shared", "tenant"})
@NullMarked
package xyz.dgz48.tasks.webapi.myfeature;
```

---

## 8. 参照実装: GET /api/tasks/{id}

Issue #273 で実装した `GET /api/tasks/{id}` が、4層構造の実証実装である。新 feature 追加時にパターンを確認すること。

| 役割 | クラス / ファイル |
|---|---|
| Domain エンティティ | [`task/domain/Task.java`](../../webapi/src/main/java/xyz/dgz48/tasks/webapi/task/domain/Task.java) |
| ドメインサービス | [`task/domain/TaskAuthorizationDomainService.java`](../../webapi/src/main/java/xyz/dgz48/tasks/webapi/task/domain/TaskAuthorizationDomainService.java) |
| 業務例外 | [`task/domain/TaskNotFoundException.java`](../../webapi/src/main/java/xyz/dgz48/tasks/webapi/task/domain/TaskNotFoundException.java) / [`TaskNotViewableException.java`](../../webapi/src/main/java/xyz/dgz48/tasks/webapi/task/domain/TaskNotViewableException.java) |
| Port(リポジトリ I/F) | [`task/usecase/TaskRepository.java`](../../webapi/src/main/java/xyz/dgz48/tasks/webapi/task/usecase/TaskRepository.java) |
| UseCase | [`task/usecase/GetTaskUseCase.java`](../../webapi/src/main/java/xyz/dgz48/tasks/webapi/task/usecase/GetTaskUseCase.java) |
| Controller | [`task/adapter/web/TaskController.java`](../../webapi/src/main/java/xyz/dgz48/tasks/webapi/task/adapter/web/TaskController.java) |
| Response DTO | [`task/adapter/web/dto/TaskResponse.java`](../../webapi/src/main/java/xyz/dgz48/tasks/webapi/task/adapter/web/dto/TaskResponse.java) |
| 例外ハンドラ | [`task/adapter/web/TaskExceptionHandler.java`](../../webapi/src/main/java/xyz/dgz48/tasks/webapi/task/adapter/web/TaskExceptionHandler.java) |
| JPA Entity | [`task/adapter/persistence/TaskJpaEntity.java`](../../webapi/src/main/java/xyz/dgz48/tasks/webapi/task/adapter/persistence/TaskJpaEntity.java) |
| Port 実装 | [`task/adapter/persistence/TaskJpaRepositoryAdapter.java`](../../webapi/src/main/java/xyz/dgz48/tasks/webapi/task/adapter/persistence/TaskJpaRepositoryAdapter.java) |
| Infra 設定 | [`task/infra/TaskInfraConfig.java`](../../webapi/src/main/java/xyz/dgz48/tasks/webapi/task/infra/TaskInfraConfig.java) |

**データフロー**:

```text
HTTP GET /api/tasks/{id}
  → TaskController.get()
    → GetTaskUseCase.execute()        ← @Transactional(readOnly = true)
      → TaskRepository.findById()     ← Port(interface in usecase)
        → TaskJpaRepositoryAdapter    ← impl in adapter.persistence
      → TaskAuthorizationDomainService.canBeViewedBy()
    → TaskResponse.from(task)         ← DTO 変換 (adapter.web)
  → 200 OK / 404 Not Found
```

---

## 9. 追加チェックリスト

新 feature を追加したら以下を確認する。

- [ ] feature トップと全サブパッケージに `package-info.java`(`@NullMarked`)を配置
- [ ] JPA アノテーションは `adapter.persistence` の `~JpaEntity` のみに記述
- [ ] `@Transactional` は `usecase` 層のメソッドのみ、`readOnly` 常に明示
- [ ] Port インターフェースは `usecase` パッケージ、実装は `adapter.persistence`
- [ ] `~JpaRepositoryAdapter` は package-private
- [ ] 業務テーブルの JPA Entity に Hibernate Filter を適用(`TenantFilteredEntity` 継承)
- [ ] `./gradlew :webapi:check` が通ること(Modularity テスト含む)
- [ ] 新 feature を表す Flyway マイグレーションスクリプトを追加([設計規約.md §3.1](../specs/設計規約.md#31-flyway-マイグレーション))
- [ ] OpenAPI(`api/openapi.yaml`)を先に修正してから実装([設計規約.md §2.1](../specs/設計規約.md#21-openapi-ファースト))
- [ ] 新クラスごとに対応するテストクラスを同一 PR に含める

---

## 10. 関連ドキュメント

- [設計規約.md §1.1](../specs/設計規約.md#11-採用するアーキテクチャスタイル) — アーキテクチャスタイルの定義
- [設計規約.md §1.3.1](../specs/設計規約.md#131-shared-パッケージの扱いopen-module) — shared Open module 規約
- [コーディング規約.md §11](../specs/コーディング規約.md#11-トランザクション) — @Transactional 規約
- [ADR-0002](../adr/0002-defer-archunit-adoption.md) — ArchUnit 導入保留(ModularityTests に集約)
- [ADR-0003](../adr/0003-shared-package-as-open-module.md) — shared Open module 決定根拠
- [ADR-0010](../adr/0010-tenant-isolation-hibernate-filter.md) — Hibernate Filter によるテナント分離
