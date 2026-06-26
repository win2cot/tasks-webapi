package xyz.dgz48.tasks.webapi.tenant.adapter.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import xyz.dgz48.tasks.webapi.tenant.adapter.web.dto.TenantCreateRequest;
import xyz.dgz48.tasks.webapi.tenant.adapter.web.dto.TenantCreatedResponse;
import xyz.dgz48.tasks.webapi.tenant.adapter.web.dto.TenantResponse;
import xyz.dgz48.tasks.webapi.tenant.adapter.web.dto.TenantUserResponse;
import xyz.dgz48.tasks.webapi.tenant.usecase.CreateTenantCommand;
import xyz.dgz48.tasks.webapi.tenant.usecase.CreateTenantResult;
import xyz.dgz48.tasks.webapi.tenant.usecase.CreateTenantUseCase;

/**
 * セルフサインアップ(A-05)用 Controller。{@code POST /api/tenants} は認証済みであれば(テナント未所属でも)実行でき、{@code
 * X-Tenant-Id} ヘッダは不要({@code TenantContextFilter} の免除パス)。SaaS Admin 専用の他テナント操作({@link
 * TenantAdminController})とは認可要件が異なるため別 Controller として分離する。
 */
@RestController
@RequiredArgsConstructor
public class TenantSignupController {

  private final CreateTenantUseCase createTenantUseCase;

  @PostMapping("/api/tenants")
  public ResponseEntity<TenantCreatedResponse> createTenant(
      @Valid @RequestBody TenantCreateRequest request,
      @AuthenticationPrincipal(expression = "id") Long callerId,
      @AuthenticationPrincipal(expression = "email") String callerEmail,
      @AuthenticationPrincipal(expression = "fullName") String callerFullName,
      @AuthenticationPrincipal(expression = "departmentName")
          @Nullable String callerDepartmentName) {
    CreateTenantResult result =
        createTenantUseCase.execute(
            new CreateTenantCommand(
                callerId, callerEmail, callerFullName, callerDepartmentName, request.name()));
    TenantCreatedResponse body =
        new TenantCreatedResponse(
            TenantResponse.from(result.tenant()), TenantUserResponse.from(result.initialAdmin()));
    return ResponseEntity.status(HttpStatus.CREATED).body(body);
  }
}
