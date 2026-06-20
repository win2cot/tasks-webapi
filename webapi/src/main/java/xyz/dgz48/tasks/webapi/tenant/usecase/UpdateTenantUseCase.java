package xyz.dgz48.tasks.webapi.tenant.usecase;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.dgz48.tasks.webapi.audit.domain.AuditEventType;
import xyz.dgz48.tasks.webapi.audit.usecase.AuditFieldChange;
import xyz.dgz48.tasks.webapi.audit.usecase.AuditLogPort;
import xyz.dgz48.tasks.webapi.shared.usecase.TenantFilterBypassService;
import xyz.dgz48.tasks.webapi.tenant.domain.FieldChange;
import xyz.dgz48.tasks.webapi.tenant.domain.Tenant;
import xyz.dgz48.tasks.webapi.tenant.domain.TenantAuditDiffDomainService;
import xyz.dgz48.tasks.webapi.tenant.domain.TenantNotFoundException;
import xyz.dgz48.tasks.webapi.tenant.domain.TenantUpdateCommand;

/**
 * A-06: テナント名更新ユースケース(SaaS Admin 専用)。
 *
 * <p>成功時に {@code TENANT_UPDATED} として {@code tenant_id=対象テナント id} で監査ログに記録する(ADR-0020 §3.1)。 差分は
 * ADR-0013 の field-by-field diff 形式で {@code detail} に記録する。
 */
@Service
@RequiredArgsConstructor
public class UpdateTenantUseCase {

  private final AdminTenantRepository adminTenantRepository;
  private final TenantFilterBypassService tenantFilterBypassService;
  private final TenantAuditDiffDomainService tenantAuditDiffDomainService;
  private final AuditLogPort auditLogPort;

  @Transactional
  public Tenant execute(Long tenantId, Long userId, TenantUpdateCommand cmd) {
    Tenant previous =
        tenantFilterBypassService
            .runAsSaaSAdmin(() -> adminTenantRepository.findById(tenantId))
            .orElseThrow(() -> new TenantNotFoundException(tenantId));

    List<FieldChange> changes = tenantAuditDiffDomainService.diff(previous, cmd);
    if (changes.isEmpty()) {
      return previous;
    }

    Tenant updated =
        tenantFilterBypassService.runAsSaaSAdmin(
            () -> adminTenantRepository.updateName(tenantId, cmd.name()));

    List<AuditFieldChange> auditChanges =
        changes.stream()
            .map(c -> new AuditFieldChange(c.field(), c.oldValue(), c.newValue()))
            .toList();
    auditLogPort.record(AuditEventType.TENANT_UPDATED, tenantId, userId, auditChanges);

    return updated;
  }
}
