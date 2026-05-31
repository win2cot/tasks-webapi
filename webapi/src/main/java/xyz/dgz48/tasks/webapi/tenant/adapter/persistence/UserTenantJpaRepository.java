package xyz.dgz48.tasks.webapi.tenant.adapter.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import xyz.dgz48.tasks.webapi.tenant.domain.UserTenantStatus;

interface UserTenantJpaRepository extends JpaRepository<UserTenantJpaEntity, UserTenantId> {

  boolean existsByIdUserIdAndIdTenantIdAndStatus(
      Long userId, Long tenantId, UserTenantStatus status);
}
