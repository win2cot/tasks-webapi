package xyz.dgz48.tasks.webapi.tenant.adapter.web.dto;

import java.time.LocalDateTime;
import org.jspecify.annotations.Nullable;
import xyz.dgz48.tasks.webapi.tenant.domain.TenantRole;
import xyz.dgz48.tasks.webapi.tenant.domain.TenantUserInfo;
import xyz.dgz48.tasks.webapi.tenant.domain.UserTenantStatus;

/** GET /api/tenant/users レスポンス DTO(OpenAPI TenantUser スキーマ)。 */
public record TenantUserResponse(
    Long userId,
    String email,
    String fullName,
    @Nullable String departmentName,
    TenantRole role,
    UserTenantStatus status,
    LocalDateTime joinedAt) {

  public static TenantUserResponse from(TenantUserInfo info) {
    return new TenantUserResponse(
        info.userId(),
        info.email(),
        info.fullName(),
        info.departmentName(),
        info.role(),
        info.status(),
        info.joinedAt());
  }
}
