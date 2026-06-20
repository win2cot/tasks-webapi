package xyz.dgz48.tasks.webapi.tenant.adapter.web;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import xyz.dgz48.tasks.webapi.shared.domain.TenantContext;
import xyz.dgz48.tasks.webapi.tenant.adapter.web.dto.TenantUserResponse;
import xyz.dgz48.tasks.webapi.tenant.usecase.ListTenantUsersUseCase;

/** テナント内ユーザー参照 API。 */
@RestController
@RequiredArgsConstructor
public class TenantUserController {

  private final ListTenantUsersUseCase listTenantUsersUseCase;

  /** GET /api/tenant/users — テナント内ユーザー一覧。認可: Member 以上。 */
  @GetMapping("/api/tenant/users")
  @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'MEMBER')")
  public List<TenantUserResponse> listTenantUsers() {
    Long tenantId = TenantContext.get();
    if (tenantId == null) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "テナントが選択されていません");
    }
    return listTenantUsersUseCase.execute(tenantId).stream().map(TenantUserResponse::from).toList();
  }
}
