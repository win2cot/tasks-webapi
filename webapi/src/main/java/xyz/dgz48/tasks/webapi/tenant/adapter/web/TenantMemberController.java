package xyz.dgz48.tasks.webapi.tenant.adapter.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import xyz.dgz48.tasks.webapi.shared.domain.TenantContext;
import xyz.dgz48.tasks.webapi.tenant.adapter.web.dto.AddMemberRequest;
import xyz.dgz48.tasks.webapi.tenant.adapter.web.dto.ChangeMemberRoleRequest;
import xyz.dgz48.tasks.webapi.tenant.usecase.AddMemberUseCase;
import xyz.dgz48.tasks.webapi.tenant.usecase.ChangeMemberRoleUseCase;
import xyz.dgz48.tasks.webapi.tenant.usecase.RemoveMemberUseCase;

/** テナントメンバー管理 API(追加 / 削除 / ロール変更)。Tenant Admin または SaaS Admin のみ操作可能。 */
@RestController
@RequestMapping("/api/tenants/{tenantId}/users")
@RequiredArgsConstructor
public class TenantMemberController {

  private final AddMemberUseCase addMemberUseCase;
  private final RemoveMemberUseCase removeMemberUseCase;
  private final ChangeMemberRoleUseCase changeMemberRoleUseCase;

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAnyRole('APP_ADMIN', 'TENANT_ADMIN')")
  public void addMember(@PathVariable Long tenantId, @RequestBody @Valid AddMemberRequest request) {
    addMemberUseCase.execute(callerTenantId(), tenantId, request.userId(), request.role());
  }

  @DeleteMapping("/{userId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAnyRole('APP_ADMIN', 'TENANT_ADMIN')")
  public void removeMember(
      @PathVariable Long tenantId,
      @PathVariable Long userId,
      @AuthenticationPrincipal(expression = "id") Long callerId) {
    removeMemberUseCase.execute(callerId, callerTenantId(), tenantId, userId);
  }

  @PatchMapping("/{userId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAnyRole('APP_ADMIN', 'TENANT_ADMIN')")
  public void changeMemberRole(
      @PathVariable Long tenantId,
      @PathVariable Long userId,
      @RequestBody @Valid ChangeMemberRoleRequest request,
      @AuthenticationPrincipal(expression = "id") Long callerId) {
    changeMemberRoleUseCase.execute(callerId, callerTenantId(), tenantId, userId, request.role());
  }

  /** SaaS Admin は X-Tenant-Id ヘッダ不要のため null。Tenant Admin は TenantContext に設定済みの値を使用。 */
  private static @Nullable Long callerTenantId() {
    return TenantContext.get();
  }
}
