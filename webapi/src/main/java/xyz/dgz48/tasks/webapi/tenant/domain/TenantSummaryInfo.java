package xyz.dgz48.tasks.webapi.tenant.domain;

/** ユーザーの所属テナント要約情報。/api/auth/me 等での表示用。 */
public record TenantSummaryInfo(Long id, String code, String name, TenantRole role) {}
