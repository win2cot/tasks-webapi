package xyz.dgz48.tasks.webapi.tenant.adapter.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A-06 テナント名更新リクエスト。 */
public record TenantUpdateRequest(@NotBlank @Size(min = 1, max = 255) String name) {}
