package xyz.dgz48.tasks.webapi.audit.usecase;

import java.util.List;
import xyz.dgz48.tasks.webapi.audit.domain.AuditLogEntry;

/**
 * 監査ログ参照(A-22)の 1 ページ分の結果。
 *
 * @param content created_at 降順のレコード列
 * @param totalElements 絞り込み条件に一致する総件数
 */
public record AuditLogPage(List<AuditLogEntry> content, long totalElements) {}
