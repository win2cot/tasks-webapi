package xyz.dgz48.tasks.webapi.tenant.usecase;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.dgz48.tasks.webapi.audit.domain.AuditEventType;
import xyz.dgz48.tasks.webapi.audit.usecase.AuditLogPort;
import xyz.dgz48.tasks.webapi.shared.usecase.TenantFilterBypassService;
import xyz.dgz48.tasks.webapi.tenant.domain.Tenant;
import xyz.dgz48.tasks.webapi.tenant.domain.TenantNotFoundException;
import xyz.dgz48.tasks.webapi.tenant.domain.TenantStatus;
import xyz.dgz48.tasks.webapi.tenant.domain.TenantStatusNotAllowedException;
import xyz.dgz48.tasks.webapi.tenant.domain.TenantStatusUpdateCommand;

/**
 * A-26: テナント状態切替ユースケース(SaaS Admin 専用)。
 *
 * <p>状態が実際に変更された場合のみ {@code TENANT_SUSPENDED} または {@code TENANT_REACTIVATED} として {@code
 * tenant_id=対象テナント id} で監査ログに記録する。同一状態への変更要求は no-op で監査記録しない(ADR-0020 §3.1)。
 */
@Service
@RequiredArgsConstructor
public class UpdateTenantStatusUseCase {

  private final AdminTenantRepository adminTenantRepository;
  private final TenantFilterBypassService tenantFilterBypassService;
  private final AuditLogPort auditLogPort;

  @Transactional
  public Tenant execute(Long tenantId, Long userId, TenantStatusUpdateCommand cmd) {
    if (cmd.status() == TenantStatus.DELETED) {
      throw new TenantStatusNotAllowedException(cmd.status());
    }

    Tenant previous =
        tenantFilterBypassService
            .runAsSaaSAdmin(() -> adminTenantRepository.findById(tenantId))
            .orElseThrow(() -> new TenantNotFoundException(tenantId));

    if (previous.getStatus() == cmd.status()) {
      return previous;
    }

    Tenant updated =
        tenantFilterBypassService.runAsSaaSAdmin(
            () -> adminTenantRepository.updateStatus(tenantId, cmd.status()));

    AuditEventType eventType =
        cmd.status() == TenantStatus.SUSPENDED
            ? AuditEventType.TENANT_SUSPENDED
            : AuditEventType.TENANT_REACTIVATED;

    auditLogPort.record(
        eventType,
        tenantId,
        userId,
        Map.of(
            "tenantId", tenantId,
            "newStatus", cmd.status().name(),
            "previousStatus", previous.getStatus().name()));

    return updated;
  }
}
