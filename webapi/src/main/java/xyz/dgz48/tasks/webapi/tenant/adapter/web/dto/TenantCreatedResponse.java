package xyz.dgz48.tasks.webapi.tenant.adapter.web.dto;

/**
 * テナント新規作成レスポンス(OpenAPI TenantCreatedResponse)。作成テナントと、初代 Tenant Admin として登録された {@code
 * user_tenants} 概要を含む。
 */
public record TenantCreatedResponse(TenantResponse tenant, TenantUserResponse initialAdmin) {}
