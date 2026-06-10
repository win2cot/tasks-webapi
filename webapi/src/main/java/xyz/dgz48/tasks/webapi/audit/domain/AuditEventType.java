package xyz.dgz48.tasks.webapi.audit.domain;

/** audit_logs.action に記録するイベント種別。 */
public enum AuditEventType {
  TASK_CREATED,
  TASK_UPDATED,
  TASK_DELETED,
  VISIBILITY_CHANGED,
  STAKEHOLDER_PURGED,
  CROSS_TENANT_VIOLATION_ATTEMPT,
  STAKEHOLDER_ADDED,
  STAKEHOLDER_REMOVED
}
