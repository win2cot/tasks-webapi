package xyz.dgz48.tasks.webapi.tenant.domain;

/** ユーザーの有効テナントメンバーシップ。初期テナント解決等で使用する軽量ドメインレコード。 */
public record TenantMembership(Long tenantId, TenantRole role) {}
