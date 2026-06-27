package xyz.dgz48.tasks.webapi.tenant.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.dgz48.tasks.webapi.tenant.domain.TenantRole;
import xyz.dgz48.tasks.webapi.tenant.domain.TenantUserInfo;
import xyz.dgz48.tasks.webapi.tenant.domain.UserTenantNotFoundException;
import xyz.dgz48.tasks.webapi.tenant.domain.UserTenantSelfOperationException;

/**
 * 現在のテナント(X-Tenant-Id 駆動)の ACTIVE メンバーのロールを変更するユースケース(A-10・Tenant Admin 専用)。
 *
 * <p>テナントは呼び出し側の {@code TenantContext} で暗黙に決まるため、越境チェックは不要(設計規約 X-Tenant-Id 不変則)。 変更後の {@link
 * TenantUserInfo} を返す(OpenAPI {@code updateRole} は 200 + TenantUser)。
 */
@Service
@RequiredArgsConstructor
public class ChangeMemberRoleUseCase {

  private final UserTenantManagementPort managementPort;
  private final ListTenantUsersPort listTenantUsersPort;

  @Transactional
  public TenantUserInfo execute(
      Long callerId, Long tenantId, Long targetUserId, TenantRole newRole) {
    if (callerId.equals(targetUserId)) {
      throw new UserTenantSelfOperationException();
    }
    boolean changed = managementPort.changeActiveMemberRole(targetUserId, tenantId, newRole);
    if (!changed) {
      throw new UserTenantNotFoundException(targetUserId, tenantId);
    }
    return listTenantUsersPort
        .findActiveTenantUser(targetUserId, tenantId)
        .orElseThrow(() -> new UserTenantNotFoundException(targetUserId, tenantId));
  }
}
