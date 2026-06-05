package xyz.dgz48.tasks.webapi.tenant.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.dgz48.tasks.webapi.tenant.domain.TenantNotMemberException;

/** テナント切替のユースケース。対象テナントが呼び出しユーザーの所属テナントであるか検証する。 */
@Service
@RequiredArgsConstructor
public class SwitchTenantUseCase {

  private final TenantMembershipPort tenantMembershipPort;

  @Transactional(readOnly = true)
  public void execute(Long userId, Long tenantId) {
    tenantMembershipPort
        .findActiveRole(userId, tenantId)
        .orElseThrow(() -> new TenantNotMemberException(tenantId));
  }
}
