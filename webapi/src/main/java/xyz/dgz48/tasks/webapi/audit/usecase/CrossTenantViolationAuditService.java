package xyz.dgz48.tasks.webapi.audit.usecase;

import java.util.Map;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import xyz.dgz48.tasks.webapi.audit.domain.AuditEventType;
import xyz.dgz48.tasks.webapi.shared.domain.CrossTenantViolationDetectedEvent;

/**
 * {@link CrossTenantViolationDetectedEvent} を受信して {@code audit_logs} に記録するサービス。
 *
 * <p>{@code REQUIRES_NEW} トランザクションで独立コミットするため、違反検知元のトランザクションがロールバックされても記録は保持される。 {@code
 * CrossTenantViolationInspector} が {@code StatementInspector} 内から発火するイベントの再帰を防ぐため、 Inspector 側で
 * {@code IN_VIOLATION_HANDLING} フラグを立ててから {@code publishEvent} を呼び出す。
 */
@Service
class CrossTenantViolationAuditService {

  private final AuditLogPort auditLogPort;

  CrossTenantViolationAuditService(AuditLogPort auditLogPort) {
    this.auditLogPort = auditLogPort;
  }

  @EventListener
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void onViolationDetected(CrossTenantViolationDetectedEvent event) {
    var detail = Map.of("table", event.tableName(), "sqlType", event.sqlType());
    auditLogPort.record(
        AuditEventType.CROSS_TENANT_VIOLATION_ATTEMPT, event.tenantId(), event.userId(), detail);
  }
}
