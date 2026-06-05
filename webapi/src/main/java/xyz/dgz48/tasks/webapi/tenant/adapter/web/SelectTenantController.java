package xyz.dgz48.tasks.webapi.tenant.adapter.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import xyz.dgz48.tasks.webapi.security.adapter.web.TasksAuthenticationToken;
import xyz.dgz48.tasks.webapi.tenant.usecase.SwitchTenantUseCase;

/** テナント切替 API。X-Tenant-Id ヘッダ不要(OpenAPI: security bearerAuth のみ)。 */
@RestController
@RequiredArgsConstructor
public class SelectTenantController {

  private final SwitchTenantUseCase switchTenantUseCase;

  @PostMapping("/api/auth/tenants/{tenantId}/select")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void selectTenant(@PathVariable Long tenantId, TasksAuthenticationToken token) {
    switchTenantUseCase.execute(token.getPrincipal().getId(), tenantId);
  }
}
