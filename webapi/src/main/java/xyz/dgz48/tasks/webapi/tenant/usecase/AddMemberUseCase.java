package xyz.dgz48.tasks.webapi.tenant.usecase;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.dgz48.tasks.webapi.tenant.domain.TenantCrossBoundaryException;
import xyz.dgz48.tasks.webapi.tenant.domain.TenantRole;
import xyz.dgz48.tasks.webapi.tenant.domain.UserTenantAlreadyExistsException;

/** テナントへのメンバー追加ユースケース。 */
@Service
@RequiredArgsConstructor
public class AddMemberUseCase {

  private final UserTenantManagementPort managementPort;

  @Transactional
  public void execute(
      @Nullable Long callerTenantId, Long tenantId, Long targetUserId, TenantRole role) {
    if (callerTenantId != null && !callerTenantId.equals(tenantId)) {
      throw new TenantCrossBoundaryException(tenantId);
    }
    if (managementPort.existsMember(targetUserId, tenantId)) {
      throw new UserTenantAlreadyExistsException(targetUserId, tenantId);
    }
    managementPort.addMember(targetUserId, tenantId, role);
  }
}
