package xyz.dgz48.tasks.webapi.tenant.usecase;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.dgz48.tasks.webapi.tenant.domain.TenantCrossBoundaryException;
import xyz.dgz48.tasks.webapi.tenant.domain.TenantRole;
import xyz.dgz48.tasks.webapi.tenant.domain.UserTenantNotFoundException;
import xyz.dgz48.tasks.webapi.tenant.domain.UserTenantSelfOperationException;

/** テナントメンバーのロール変更ユースケース。 */
@Service
@RequiredArgsConstructor
public class ChangeMemberRoleUseCase {

  private final UserTenantManagementPort managementPort;

  @Transactional
  public void execute(
      Long callerId,
      @Nullable Long callerTenantId,
      Long tenantId,
      Long targetUserId,
      TenantRole newRole) {
    if (callerTenantId != null && !callerTenantId.equals(tenantId)) {
      throw new TenantCrossBoundaryException(tenantId);
    }
    if (callerId.equals(targetUserId)) {
      throw new UserTenantSelfOperationException();
    }
    boolean changed = managementPort.changeActiveMemberRole(targetUserId, tenantId, newRole);
    if (!changed) {
      throw new UserTenantNotFoundException(targetUserId, tenantId);
    }
  }
}
