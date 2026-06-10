package xyz.dgz48.tasks.webapi.tenant.domain;

/** A-26 テナント状態切替コマンド。 */
public record TenantStatusUpdateCommand(TenantStatus status) {}
