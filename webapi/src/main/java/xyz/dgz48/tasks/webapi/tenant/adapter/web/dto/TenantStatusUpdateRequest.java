package xyz.dgz48.tasks.webapi.tenant.adapter.web.dto;

import jakarta.validation.constraints.NotNull;
import xyz.dgz48.tasks.webapi.tenant.domain.TenantStatus;

/** A-26 テナント状態切替リクエスト。 */
public record TenantStatusUpdateRequest(@NotNull TenantStatus status) {}
