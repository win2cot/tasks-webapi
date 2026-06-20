package xyz.dgz48.tasks.webapi.security.adapter.web.dto;

import java.util.List;

public record MeResponse(UserProfileDto user, List<TenantSummaryDto> tenants) {}
