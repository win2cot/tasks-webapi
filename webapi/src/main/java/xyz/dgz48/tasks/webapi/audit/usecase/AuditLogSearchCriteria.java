package xyz.dgz48.tasks.webapi.audit.usecase;

import java.time.LocalDateTime;
import org.jspecify.annotations.Nullable;

/**
 * 監査ログ参照(A-22)の検索条件。
 *
 * <p>{@code tenantId} は現在のテナント({@code X-Tenant-Id})。参照スコープは {@code audit_logs.tenant_id = tenantId}
 * に一致するレコード(ADR-0020 §3.4)。{@code from} / {@code to} は {@code created_at} の半開区間 {@code [from,
 * to)}、{@code action} は完全一致。いずれも {@code null} なら絞り込みなし。
 *
 * @param from 開始日時(JST、{@code created_at >= from})
 * @param to 終了日時(JST、{@code created_at < to})
 */
public record AuditLogSearchCriteria(
    Long tenantId,
    @Nullable LocalDateTime from,
    @Nullable LocalDateTime to,
    @Nullable String action,
    int page,
    int size) {}
