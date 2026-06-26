package xyz.dgz48.tasks.webapi.audit.usecase;

/**
 * 監査ログ参照(A-22)の取得ポート(クリーンアーキの out port)。
 *
 * <p>実装は adapter.persistence。{@code audit_logs} は {@code TenantFilteredEntity} 非適用(tenant_id が
 * nullable、 ADR-0010 §6.1)のため、テナント分離は実装側で {@code tenant_id = :tenantId} を明示絞り込みする。
 */
public interface AuditLogQueryPort {

  /** 検索条件に一致する監査ログを created_at 降順・ページングで取得する。 */
  AuditLogPage search(AuditLogSearchCriteria criteria);
}
