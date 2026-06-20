package xyz.dgz48.tasks.webapi.tenant.domain;

import java.time.LocalDateTime;
import org.jspecify.annotations.Nullable;

/** テナント内ユーザー情報(GET /api/tenant/users レスポンス用)。 */
public record TenantUserInfo(
    Long userId,
    String email,
    String fullName,
    @Nullable String departmentName,
    TenantRole role,
    UserTenantStatus status,
    LocalDateTime joinedAt) {}
