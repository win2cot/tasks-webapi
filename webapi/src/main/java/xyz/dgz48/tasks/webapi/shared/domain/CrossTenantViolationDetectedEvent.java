package xyz.dgz48.tasks.webapi.shared.domain;

import org.jspecify.annotations.Nullable;

/**
 * Hibernate Filter 未設定で tenant-filtered テーブルへの SQL が発行された際に {@code CrossTenantViolationInspector}
 * が発火するドメインイベント。
 *
 * <p>{@code audit} feature の {@code CrossTenantViolationAuditService} が {@code REQUIRES_NEW}
 * トランザクションで {@code audit_logs} に記録する。
 */
public record CrossTenantViolationDetectedEvent(
    @Nullable Long tenantId, @Nullable Long userId, String tableName, String sqlType) {}
