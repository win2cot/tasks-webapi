package xyz.dgz48.tasks.webapi.tenant.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.dgz48.tasks.webapi.tenant.domain.UserTenantNotFoundException;
import xyz.dgz48.tasks.webapi.tenant.domain.UserTenantSelfOperationException;

/**
 * 現在のテナント(X-Tenant-Id 駆動)から ACTIVE メンバーを削除するユースケース(A-10 系・Tenant Admin 専用)。
 *
 * <p>テナントは呼び出し側の {@code TenantContext} で暗黙に決まるため、越境チェックは不要(設計規約 X-Tenant-Id 不変則)。
 */
@Service
@RequiredArgsConstructor
public class RemoveMemberUseCase {

  private final UserTenantManagementPort managementPort;

  @Transactional
  public void execute(Long callerId, Long tenantId, Long targetUserId) {
    if (callerId.equals(targetUserId)) {
      throw new UserTenantSelfOperationException();
    }
    boolean removed = managementPort.removeActiveMember(targetUserId, tenantId);
    if (!removed) {
      throw new UserTenantNotFoundException(targetUserId, tenantId);
    }
  }
}
