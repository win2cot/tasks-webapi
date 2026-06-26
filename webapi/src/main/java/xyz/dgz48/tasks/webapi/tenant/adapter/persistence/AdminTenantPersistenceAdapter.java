package xyz.dgz48.tasks.webapi.tenant.adapter.persistence;

import io.micrometer.observation.annotation.Observed;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import xyz.dgz48.tasks.webapi.tenant.domain.Tenant;
import xyz.dgz48.tasks.webapi.tenant.domain.TenantPlan;
import xyz.dgz48.tasks.webapi.tenant.domain.TenantStatus;
import xyz.dgz48.tasks.webapi.tenant.usecase.AdminTenantRepository;

/** {@link AdminTenantRepository} の JPA 実装。Hibernate Filter 非適用(SaaS Admin 専用経路)。 */
@Observed(name = "tenant.repository")
@Component
@RequiredArgsConstructor
class AdminTenantPersistenceAdapter implements AdminTenantRepository {

  private final TenantJpaRepository tenantJpaRepository;

  @Override
  @Transactional
  public Tenant createTenant(String code, String name) {
    TenantJpaEntity saved =
        tenantJpaRepository.save(new TenantJpaEntity(code, name, TenantPlan.FREE));
    // 新規作成直後はメンバー未登録 → COUNT クエリ(特に tasks)を発行しない。
    // userCount は呼び出し側 UseCase が初代 admin 登録後に 1 へ補正する。
    return new Tenant(
        saved.getId(),
        saved.getCode(),
        saved.getName(),
        saved.getPlan(),
        saved.getStatus(),
        saved.getCreatedAt(),
        saved.getUpdatedAt(),
        0L,
        0L);
  }

  @Override
  @Transactional(readOnly = true)
  public boolean existsByCode(String code) {
    return tenantJpaRepository.existsByCode(code);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<Tenant> findById(Long id) {
    return tenantJpaRepository.findById(id).map(e -> toTenant(e));
  }

  @Override
  @Transactional(readOnly = true)
  public Page<Tenant> findAll(
      @Nullable TenantStatus status, @Nullable String keyword, Pageable pageable) {
    Page<TenantJpaEntity> entityPage =
        tenantJpaRepository.findAllFiltered(status, keyword, pageable);
    if (entityPage.isEmpty()) {
      return entityPage.map(e -> toTenant(e));
    }
    List<Long> ids = entityPage.getContent().stream().map(TenantJpaEntity::getId).toList();
    Map<Long, Long> userCounts =
        tenantJpaRepository.countUsersByTenantIds(ids).stream()
            .collect(
                Collectors.toMap(
                    r -> ((Number) r[0]).longValue(), r -> ((Number) r[1]).longValue()));
    Map<Long, Long> taskCounts =
        tenantJpaRepository.countTasksByTenantIds(ids).stream()
            .collect(
                Collectors.toMap(
                    r -> ((Number) r[0]).longValue(), r -> ((Number) r[1]).longValue()));
    return entityPage.map(
        e ->
            new Tenant(
                e.getId(),
                e.getCode(),
                e.getName(),
                e.getPlan(),
                e.getStatus(),
                e.getCreatedAt(),
                e.getUpdatedAt(),
                userCounts.getOrDefault(e.getId(), 0L),
                taskCounts.getOrDefault(e.getId(), 0L)));
  }

  @Override
  @Transactional
  public Tenant updateName(Long id, String name) {
    TenantJpaEntity entity = tenantJpaRepository.findById(id).orElseThrow();
    entity.updateName(name);
    return toTenant(tenantJpaRepository.save(entity));
  }

  @Override
  @Transactional
  public Tenant updateStatus(Long id, TenantStatus status) {
    TenantJpaEntity entity = tenantJpaRepository.findById(id).orElseThrow();
    entity.updateStatus(status);
    return toTenant(tenantJpaRepository.save(entity));
  }

  private Tenant toTenant(TenantJpaEntity e) {
    long userCount = tenantJpaRepository.countUsersByTenantId(e.getId());
    long taskCount = tenantJpaRepository.countTasksByTenantId(e.getId());
    return new Tenant(
        e.getId(),
        e.getCode(),
        e.getName(),
        e.getPlan(),
        e.getStatus(),
        e.getCreatedAt(),
        e.getUpdatedAt(),
        userCount,
        taskCount);
  }
}
