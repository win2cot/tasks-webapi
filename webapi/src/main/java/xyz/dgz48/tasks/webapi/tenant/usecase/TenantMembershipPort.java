package xyz.dgz48.tasks.webapi.tenant.usecase;

import java.util.Optional;
import xyz.dgz48.tasks.webapi.tenant.domain.TenantRole;

/** テナントメンバーシップ検証ポート。 */
public interface TenantMembershipPort {

  /** 指定ユーザーが指定テナントの ACTIVE メンバーである場合そのロールを返す。非メンバーは空。 */
  Optional<TenantRole> findActiveRole(Long userId, Long tenantId);
}
