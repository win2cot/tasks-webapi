package xyz.dgz48.tasks.webapi.tenant.usecase;

import java.util.List;
import xyz.dgz48.tasks.webapi.tenant.domain.TenantUserInfo;

/** テナント内ユーザー一覧取得ポート。 */
public interface ListTenantUsersPort {

  /** 指定テナントの全メンバーを joined_at ASC 順で返す。 */
  List<TenantUserInfo> findTenantUsers(Long tenantId);
}
