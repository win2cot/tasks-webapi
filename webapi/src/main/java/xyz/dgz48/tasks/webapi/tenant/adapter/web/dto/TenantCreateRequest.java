package xyz.dgz48.tasks.webapi.tenant.adapter.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * テナント新規作成リクエスト(OpenAPI TenantCreateRequest)。{@code code} はサーバ側で {@code name} の slug
 * から自動生成するため受け取らない。
 */
public record TenantCreateRequest(@NotBlank @Size(min = 1, max = 255) String name) {}
