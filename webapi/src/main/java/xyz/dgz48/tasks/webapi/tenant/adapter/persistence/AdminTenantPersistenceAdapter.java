package xyz.dgz48.tasks.webapi.tenant.adapter.persistence;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import xyz.dgz48.tasks.webapi.tenant.domain.Tenant;
import xyz.dgz48.tasks.webapi.tenant.domain.TenantStatus;
import xyz.dgz48.tasks.webapi.tenant.usecase.AdminTenantRepository;

/** {@link AdminTenantRepository} の JPA 実装。Hibernate Filter 非適用(SaaS Admin 専用経路)。 */
@Component
@RequiredArgsConstructor
class AdminTenantPersistenceAdapter implements AdminTenantRepository {

  private final TenantJpaRepository tenantJpaRepository;

  @Override
  @Transactional(readOnly = true)
  public Optional<Tenant> findById(Long id) {
    return tenantJpaRepository.findById(id).map(e -> toTenant(e));
  }

  @Override
  @Transactional(readOnly = true)
  public Page<Tenant> findAll(
      @Nullable TenantStatus status, @Nullable String keyword, Pageable pageable) {
    return tenantJpaRepository.findAllFiltered(status, keyword, pageable).map(e -> toTenant(e));
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
