package xyz.dgz48.tasks.webapi.tenant.adapter.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import xyz.dgz48.tasks.webapi.tenant.domain.UserTenantStatus;

interface UserTenantJpaRepository extends JpaRepository<UserTenantJpaEntity, UserTenantId> {

  Optional<UserTenantJpaEntity> findByIdUserIdAndIdTenantIdAndStatus(
      Long userId, Long tenantId, UserTenantStatus status);
}
