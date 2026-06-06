package xyz.dgz48.tasks.webapi.tenant.usecase;

import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.dgz48.tasks.webapi.tenant.domain.TenantMembership;

/**
 * ログイン時の初期テナント解決サービス。
 *
 * <p>X-Tenant-Id ヘッダ未指定時に呼び出され、joined_at ASC で最初の ACTIVE メンバーシップを返す。 0 件の場合は空を返す(呼び出し側が 403
 * を返す)。複数件の場合は最初に参加したテナントを選択する(ADR-0016)。
 */
@Service
@RequiredArgsConstructor
public class UserTenantsResolverService {

  private final TenantMembershipPort tenantMembershipPort;

  @Transactional(readOnly = true)
  public Optional<TenantMembership> resolveInitial(Long userId) {
    List<TenantMembership> memberships = tenantMembershipPort.findActiveMemberships(userId);
    if (memberships.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(memberships.get(0));
  }
}
