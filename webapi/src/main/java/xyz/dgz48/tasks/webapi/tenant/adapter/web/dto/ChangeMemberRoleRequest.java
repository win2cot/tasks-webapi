package xyz.dgz48.tasks.webapi.tenant.adapter.web.dto;

import jakarta.validation.constraints.NotNull;
import xyz.dgz48.tasks.webapi.tenant.domain.TenantRole;

public record ChangeMemberRoleRequest(@NotNull TenantRole role) {}
