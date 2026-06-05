package xyz.dgz48.tasks.webapi.tenant.domain;

/** テナントメンバーシップのドメイン値オブジェクト。 */
public record UserTenant(Long userId, Long tenantId, TenantRole role) {}
