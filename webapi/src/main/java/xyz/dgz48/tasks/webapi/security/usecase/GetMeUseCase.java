package xyz.dgz48.tasks.webapi.security.usecase;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.dgz48.tasks.webapi.tenant.domain.TenantSummaryInfo;
import xyz.dgz48.tasks.webapi.tenant.usecase.TenantMembershipPort;

/** ログインユーザーの所属テナント一覧を返すユースケース。 */
@Service
@RequiredArgsConstructor
public class GetMeUseCase {

  private final TenantMembershipPort tenantMembershipPort;

  @Transactional(readOnly = true)
  public List<TenantSummaryInfo> findTenants(Long userId) {
    return tenantMembershipPort.findActiveMembershipDetails(userId);
  }
}
