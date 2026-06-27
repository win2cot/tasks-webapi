package xyz.dgz48.tasks.webapi.tenant.adapter.persistence;

import io.micrometer.observation.annotation.Observed;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import xyz.dgz48.tasks.webapi.tenant.domain.TenantRole;
import xyz.dgz48.tasks.webapi.tenant.domain.UserTenant;
import xyz.dgz48.tasks.webapi.tenant.domain.UserTenantStatus;
import xyz.dgz48.tasks.webapi.tenant.usecase.UserTenantManagementPort;

@Observed(name = "tenant.repository")
@Component
@RequiredArgsConstructor
class UserTenantManagementAdapter implements UserTenantManagementPort {

  private final UserTenantJpaRepository repository;
  private final Clock clock;

  @Override
  @Transactional
  public UserTenant addMember(Long userId, Long tenantId, TenantRole role) {
    var entity = new UserTenantJpaEntity(userId, tenantId, role, LocalDateTime.now(clock));
    repository.save(entity);
    return new UserTenant(userId, tenantId, role);
  }

  @Override
  @Transactional
  public boolean removeActiveMember(Long userId, Long tenantId) {
    return repository
        .findByIdUserIdAndIdTenantIdAndStatus(userId, tenantId, UserTenantStatus.ACTIVE)
        .map(
            entity -> {
              repository.delete(entity);
              return true;
            })
        .orElse(false);
  }

  @Override
  @Transactional
  public boolean changeActiveMemberRole(Long userId, Long tenantId, TenantRole newRole) {
    return repository
        .findByIdUserIdAndIdTenantIdAndStatus(userId, tenantId, UserTenantStatus.ACTIVE)
        .map(
            entity -> {
              entity.updateRole(newRole);
              return true;
            })
        .orElse(false);
  }
}
