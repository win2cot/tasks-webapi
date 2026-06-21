package xyz.dgz48.tasks.webapi.tenant.adapter.persistence;

import io.micrometer.observation.annotation.Observed;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import xyz.dgz48.tasks.webapi.tenant.domain.TenantMembership;
import xyz.dgz48.tasks.webapi.tenant.domain.TenantRole;
import xyz.dgz48.tasks.webapi.tenant.domain.TenantSummaryInfo;
import xyz.dgz48.tasks.webapi.tenant.domain.UserTenantStatus;
import xyz.dgz48.tasks.webapi.tenant.usecase.TenantMembershipPort;

@Observed(name = "tenant.repository")
@Component
@RequiredArgsConstructor
class UserTenantMembershipAdapter implements TenantMembershipPort {

  private final UserTenantJpaRepository repository;

  @Override
  @Transactional(readOnly = true)
  public Optional<TenantRole> findActiveRole(Long userId, Long tenantId) {
    return repository
        .findByIdUserIdAndIdTenantIdAndStatus(userId, tenantId, UserTenantStatus.ACTIVE)
        .map(UserTenantJpaEntity::getRole);
  }

  @Override
  @Transactional(readOnly = true)
  public List<TenantMembership> findActiveMemberships(Long userId) {
    return repository
        .findByIdUserIdAndStatusOrderByJoinedAtAsc(userId, UserTenantStatus.ACTIVE)
        .stream()
        .map(e -> new TenantMembership(e.getId().getTenantId(), e.getRole()))
        .toList();
  }

  @Override
  public List<TenantSummaryInfo> findActiveMembershipDetails(Long userId) {
    return repository.findActiveMembershipsWithTenantDetail(userId, UserTenantStatus.ACTIVE);
  }
}
