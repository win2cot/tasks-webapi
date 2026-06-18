package xyz.dgz48.tasks.webapi.tenant.usecase;

import java.util.List;
import java.util.Optional;
import xyz.dgz48.tasks.webapi.tenant.domain.TenantMembership;
import xyz.dgz48.tasks.webapi.tenant.domain.TenantRole;
import xyz.dgz48.tasks.webapi.tenant.domain.TenantSummaryInfo;

/** テナントメンバーシップ検証ポート。 */
public interface TenantMembershipPort {

  /** 指定ユーザーが指定テナントの ACTIVE メンバーである場合そのロールを返す。非メンバーは空。 */
  Optional<TenantRole> findActiveRole(Long userId, Long tenantId);

  /** 指定ユーザーの全 ACTIVE メンバーシップを joined_at ASC 順で返す。 */
  List<TenantMembership> findActiveMemberships(Long userId);

  /** 指定ユーザーの全 ACTIVE メンバーシップをテナント詳細(code/name)付きで joined_at ASC 順で返す。 */
  List<TenantSummaryInfo> findActiveMembershipDetails(Long userId);
}
