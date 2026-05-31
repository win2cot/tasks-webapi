package xyz.dgz48.tasks.webapi.tenant.adapter.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import xyz.dgz48.tasks.webapi.tenant.domain.UserTenantStatus;
import xyz.dgz48.tasks.webapi.tenant.usecase.TenantMembershipPort;

@Component
@RequiredArgsConstructor
class UserTenantMembershipAdapter implements TenantMembershipPort {

  private final UserTenantJpaRepository repository;

  @Override
  @Transactional(readOnly = true)
  public boolean isActiveMember(Long userId, Long tenantId) {
    return repository.existsByIdUserIdAndIdTenantIdAndStatus(
        userId, tenantId, UserTenantStatus.ACTIVE);
  }
}
