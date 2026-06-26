package xyz.dgz48.tasks.webapi.audit.adapter.web.dto;

import java.util.List;

/**
 * OpenAPI {@code listAuditLogs} の 200 レスポンス({@code {content, totalElements}})。
 *
 * @param content created_at 降順の監査ログ列
 * @param totalElements 絞り込み条件に一致する総件数
 */
public record AuditLogPageResponse(List<AuditLogResponse> content, long totalElements) {}
