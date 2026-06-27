package xyz.dgz48.tasks.webapi.tenant.adapter.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import xyz.dgz48.tasks.webapi.shared.domain.TenantContext;
import xyz.dgz48.tasks.webapi.tenant.adapter.web.dto.ChangeMemberRoleRequest;
import xyz.dgz48.tasks.webapi.tenant.adapter.web.dto.TenantUserResponse;
import xyz.dgz48.tasks.webapi.tenant.adapter.web.dto.UserInviteRequest;
import xyz.dgz48.tasks.webapi.tenant.usecase.ChangeMemberRoleUseCase;
import xyz.dgz48.tasks.webapi.tenant.usecase.InviteUserUseCase;
import xyz.dgz48.tasks.webapi.tenant.usecase.RemoveMemberUseCase;

/**
 * テナントメンバー管理 API(招待 / 削除 / ロール変更)。Tenant Admin 専用。
 *
 * <p>テナントは X-Tenant-Id ヘッダ駆動(テナント暗黙)で {@link TenantContext} から取得する(設計規約 X-Tenant-Id 不変則)。SaaS Admin
 * はメンバー管理権限を持たず、{@code TenantContextFilter} が当該パスでテナント解決に失敗するため 403 となる(基本設計書 §6.2.1)。直接追加(by
 * userId)は廃止し、招待フロー(A-09)に一本化している。
 */
@RestController
@RequestMapping("/api/tenant/users")
@RequiredArgsConstructor
public class TenantMemberController {

  private final InviteUserUseCase inviteUserUseCase;
  private final RemoveMemberUseCase removeMemberUseCase;
  private final ChangeMemberRoleUseCase changeMemberRoleUseCase;

  /** POST /api/tenant/users/invite — 現在テナントへ email を招待する(A-09 / ADR-0017)。 */
  @PostMapping("/invite")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasRole('TENANT_ADMIN')")
  public void inviteUser(
      @RequestBody @Valid UserInviteRequest request,
      @AuthenticationPrincipal(expression = "id") Long callerId) {
    inviteUserUseCase.execute(currentTenantId(), callerId, request.email(), request.role());
  }

  /** DELETE /api/tenant/users/{userId} — 現在テナントから ACTIVE メンバーを削除する。 */
  @DeleteMapping("/{userId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasRole('TENANT_ADMIN')")
  public void removeMember(
      @PathVariable Long userId, @AuthenticationPrincipal(expression = "id") Long callerId) {
    removeMemberUseCase.execute(callerId, currentTenantId(), userId);
  }

  /** PUT /api/tenant/users/{userId}/role — 現在テナントの ACTIVE メンバーのロールを変更する(A-10)。 */
  @PutMapping("/{userId}/role")
  @PreAuthorize("hasRole('TENANT_ADMIN')")
  public TenantUserResponse updateRole(
      @PathVariable Long userId,
      @RequestBody @Valid ChangeMemberRoleRequest request,
      @AuthenticationPrincipal(expression = "id") Long callerId) {
    return TenantUserResponse.from(
        changeMemberRoleUseCase.execute(callerId, currentTenantId(), userId, request.role()));
  }

  /** TenantContext に設定済みの現在テナント ID。未選択時は 403(通常は TenantContextFilter が設定済み)。 */
  private static Long currentTenantId() {
    Long tenantId = TenantContext.get();
    if (tenantId == null) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "テナントが選択されていません");
    }
    return tenantId;
  }
}
