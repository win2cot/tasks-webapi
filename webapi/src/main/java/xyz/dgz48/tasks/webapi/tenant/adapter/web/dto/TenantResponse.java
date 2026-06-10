package xyz.dgz48.tasks.webapi.tenant.adapter.web.dto;

import java.time.LocalDateTime;
import xyz.dgz48.tasks.webapi.tenant.domain.Tenant;
import xyz.dgz48.tasks.webapi.tenant.domain.TenantPlan;
import xyz.dgz48.tasks.webapi.tenant.domain.TenantStatus;

/** Tenant レスポンス DTO(OpenAPI Tenant スキーマ)。 */
public record TenantResponse(
    Long id,
    String code,
    String name,
    TenantPlan plan,
    TenantStatus status,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    long userCount,
    long taskCount) {

  public static TenantResponse from(Tenant t) {
    return new TenantResponse(
        t.getId(),
        t.getCode(),
        t.getName(),
        t.getPlan(),
        t.getStatus(),
        t.getCreatedAt(),
        t.getUpdatedAt(),
        t.getUserCount(),
        t.getTaskCount());
  }
}
