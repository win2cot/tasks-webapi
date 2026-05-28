# Feature 追加テンプレート手順

タスク管理システム(tasks-webapi)

## 概要

設計規約 §1.1 のハイブリッド構成(Spring Modulith feature-by-package + 各 feature 内クリーンアーキ 4 層)に従って、新規 feature を追加する際の手順テンプレート。

参照:
- [設計規約 §1.1 採用するアーキテクチャスタイル](../specs/設計規約.md#11-採用するアーキテクチャスタイル)
- [ADR-0003: shared パッケージを Open module として宣言する](../adr/0003-shared-package-as-open-module.md)

## ディレクトリ構成

```
xyz.dgz48.tasks.webapi.<feature>/
  ├─ domain/               POJO、Spring 非依存、JPA アノテーション禁止
  ├─ usecase/              ユースケース・Port 定義・@Transactional 境界
  ├─ adapter/
  │   ├─ web/              REST Controller、DTO(record)
  │   ├─ persistence/      JPA Entity(*JpaEntity)・Repository
  │   └─ external/         外部クライアント(必要 feature のみ)
  └─ infra/                feature 固有 Spring 設定(必要 feature のみ)
```

## 手順

### 1. パッケージ作成 + package-info.java

以下のすべてのパッケージに `package-info.java` を配置し、`@NullMarked` を付与する。

```java
@NullMarked
package xyz.dgz48.tasks.webapi.<feature>.<layer>;

import org.jspecify.annotations.NullMarked;
```

feature ルートパッケージも同様(Spring Modulith のモジュール認識に必要)。

### 2. Domain POJO 作成

JPA アノテーション禁止。`final` フィールド + `@Getter` を推奨。

```java
// domain/SomeDomain.java
@Getter
public class SomeDomain {
  private final Long id;
  private final Long tenantId;
  // ...
  public SomeDomain(Long id, Long tenantId, ...) { ... }
}
```

### 3. JPA Entity 作成(`*JpaEntity` 命名必須)

```java
// adapter/persistence/SomeDomainJpaEntity.java
@Entity
@Table(name = "some_table")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SuppressWarnings("NullAway.Init") // JPA requires no-args constructor; fields initialized via JPA
public class SomeDomainJpaEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "tenant_id", nullable = false)
  private Long tenantId;

  // ...

  public SomeDomain toDomain() {
    return new SomeDomain(id, tenantId, ...);
  }
}
```

### 4. Repository 作成

```java
// adapter/persistence/SomeDomainJpaRepository.java
public interface SomeDomainJpaRepository extends JpaRepository<SomeDomainJpaEntity, Long> {
  Optional<SomeDomainJpaEntity> findByTenantIdAndId(Long tenantId, Long id);
}
```

### 5. UseCase 作成

`@Transactional` の `readOnly` は常に明示(コーディング規約 §11.1)。

```java
// usecase/GetSomeDomainUseCase.java
@Service
@RequiredArgsConstructor
public class GetSomeDomainUseCase {
  private final SomeDomainJpaRepository repository;

  @Transactional(readOnly = true)
  public SomeDomain get(Long tenantId, Long id) {
    return repository
        .findByTenantIdAndId(tenantId, id)
        .map(e -> e.toDomain())
        .orElseThrow(() -> new SomeDomainNotFoundException(id));
  }
}
```

### 6. Controller + DTO 作成

DTO は `record` で作成し、`adapter.web.dto` 配下に置く。

```java
// adapter/web/dto/SomeDomainResponse.java
public record SomeDomainResponse(Long id, ...) {
  public static SomeDomainResponse from(SomeDomain domain) { ... }
}

// adapter/web/SomeDomainController.java
@RestController
@RequestMapping("/api/some-domains")
@RequiredArgsConstructor
public class SomeDomainController {
  private final GetSomeDomainUseCase useCase;

  @GetMapping("/{id}")
  public ResponseEntity<SomeDomainResponse> get(
      @RequestHeader("X-Tenant-Id") Long tenantId, @PathVariable Long id) {
    return ResponseEntity.ok(SomeDomainResponse.from(useCase.get(tenantId, id)));
  }
}
```

### 7. ModularityTests 確認

feature 追加後は必ず `ApplicationModules.verify()` が通ることを確認する。

```bash
./gradlew :webapi:test --tests "xyz.dgz48.tasks.webapi.ModularityTests"
```

## チェックリスト

- [ ] feature ルートの `package-info.java` に `@NullMarked`
- [ ] 各 4 層パッケージの `package-info.java` に `@NullMarked`
- [ ] Domain POJO に JPA アノテーションがないこと
- [ ] JPA Entity の単純クラス名が `*JpaEntity` で終わること
- [ ] UseCase の `@Transactional` に `readOnly` が明示されていること
- [ ] `ApplicationModules.verify()` が CI で green
- [ ] ArchUnit 依存を `build.gradle` に追加していないこと(ADR-0002)
