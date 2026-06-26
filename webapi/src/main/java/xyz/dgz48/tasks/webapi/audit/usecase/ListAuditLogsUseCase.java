package xyz.dgz48.tasks.webapi.audit.usecase;

import io.micrometer.observation.annotation.Observed;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 監査ログ参照(A-22、operationId: listAuditLogs)のユースケース。
 *
 * <p>Tenant Admin が自テナント({@code X-Tenant-Id})の監査ログを参照する。参照スコープ・テナント分離は {@link AuditLogQueryPort}
 * 実装が担う(ADR-0020 §3.4)。
 */
@Service
@RequiredArgsConstructor
public class ListAuditLogsUseCase {

  private final AuditLogQueryPort auditLogQueryPort;

  @Observed(name = "audit.list")
  @Transactional(readOnly = true)
  public AuditLogPage execute(AuditLogSearchCriteria criteria) {
    return auditLogQueryPort.search(criteria);
  }
}
