package xyz.dgz48.tasks.webapi.tenant.usecase;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.dgz48.tasks.webapi.tenant.domain.TenantUserInfo;

/** テナント内ユーザー一覧取得ユースケース。 */
@Service
@RequiredArgsConstructor
public class ListTenantUsersUseCase {

  private final ListTenantUsersPort port;

  @Transactional(readOnly = true)
  public List<TenantUserInfo> execute(Long tenantId) {
    return port.findTenantUsers(tenantId);
  }
}
