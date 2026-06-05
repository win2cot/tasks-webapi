package xyz.dgz48.tasks.webapi.tenant.usecase;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.dgz48.tasks.webapi.tenant.domain.TenantCrossBoundaryException;
import xyz.dgz48.tasks.webapi.tenant.domain.UserTenantNotFoundException;
import xyz.dgz48.tasks.webapi.tenant.domain.UserTenantSelfOperationException;

/** テナントからのメンバー削除ユースケース。 */
@Service
@RequiredArgsConstructor
public class RemoveMemberUseCase {

  private final UserTenantManagementPort managementPort;

  @Transactional
  public void execute(
      Long callerId, @Nullable Long callerTenantId, Long tenantId, Long targetUserId) {
    if (callerTenantId != null && !callerTenantId.equals(tenantId)) {
      throw new TenantCrossBoundaryException(tenantId);
    }
    if (callerId.equals(targetUserId)) {
      throw new UserTenantSelfOperationException();
    }
    boolean removed = managementPort.removeActiveMember(targetUserId, tenantId);
    if (!removed) {
      throw new UserTenantNotFoundException(targetUserId, tenantId);
    }
  }
}
