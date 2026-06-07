package xyz.dgz48.tasks.webapi.audit.domain;

/** audit_logs.action に記録するイベント種別。 */
public enum AuditEventType {
  TASK_CREATED,
  CROSS_TENANT_VIOLATION_ATTEMPT,
  STAKEHOLDER_ADDED,
  STAKEHOLDER_REMOVED
}
