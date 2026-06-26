package xyz.dgz48.tasks.webapi.audit.domain;

import java.time.LocalDateTime;
import org.jspecify.annotations.Nullable;

/**
 * 監査ログ参照(A-22、{@code GET /api/audit-logs})の 1 レコード。
 *
 * <p>{@code audit_logs} テーブルのうち API で公開する列のみを保持する({@code tenant_id} / {@code actor_sub} / {@code
 * hash_chain} 等の内部列は除外、OpenAPI {@code AuditLog} 参照)。{@code detailJson} は DB に保存された JSON
 * 文字列をそのまま保持し、object への変換は web 層で行う(domain は Jackson 非依存)。
 *
 * @param userId 操作ユーザー({@code users.id})。認証失敗 / システム起因イベントは {@code null}。
 * @param detailJson 監査詳細の JSON 文字列(空は {@code "{}"})。
 */
public record AuditLogEntry(
    Long id,
    @Nullable Long userId,
    String action,
    @Nullable String entityType,
    @Nullable Long entityId,
    String detailJson,
    @Nullable String ipAddress,
    LocalDateTime createdAt) {}
