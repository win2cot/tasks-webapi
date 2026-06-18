package xyz.dgz48.tasks.webapi.security.adapter.web.dto;

import java.util.List;
import org.jspecify.annotations.Nullable;

public record MeResponse(
    UserProfileDto user, List<TenantSummaryDto> tenants, @Nullable Long activeTenantId) {}
