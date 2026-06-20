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

/**
 * A-25: テナント単体取得ユースケース。
 *
 * <p>SaaS Admin の場合は任意のテナントを取得し {@code TENANT_VIEWED}(tenant_id=対象テナント)で監査記録する。
 * 一般ユーザーはメンバーシップを確認し、所属していないテナントは {@link TenantNotFoundException} を返す(NIST AC-4)。
 * 一般ユーザーのアクセスは監査記録しない(ADR-0020 §3.2)。
 */
@Service
@RequiredArgsConstructor
public class GetTenantUseCase {

  private final AdminTenantRepository adminTenantRepository;
  private final TenantMembershipPort membershipPort;
  private final TenantFilterBypassService tenantFilterBypassService;
  private final AuditLogPort auditLogPort;

  @Transactional
  public Tenant executeAsSaasAdmin(Long tenantId, Long userId) {
    Tenant tenant =
        tenantFilterBypassService
            .runAsSaaSAdmin(() -> adminTenantRepository.findById(tenantId))
            .orElseThrow(() -> new TenantNotFoundException(tenantId));

    auditLogPort.record(
        AuditEventType.TENANT_VIEWED, tenantId, userId, Map.of("tenantId", tenantId));

    return tenant;
  }

  /**
   * 一般ユーザー向け: メンバーシップ確認 → テナント取得(監査なし)。
   *
   * <p>{@code tenants} テーブルは Hibernate Filter 対象外のため、テナント ID に対して直接クエリを行う。 メンバーシップチェックがアクセス制御の SSOT
   * となる(ADR-0010 §6.1)。
   */
  @Transactional(readOnly = true)
  public Tenant executeAsUser(Long tenantId, Long userId) {
    if (membershipPort.findActiveRole(userId, tenantId).isEmpty()) {
      throw new TenantNotFoundException(tenantId);
    }
    return adminTenantRepository
        .findById(tenantId)
        .orElseThrow(() -> new TenantNotFoundException(tenantId));
  }
}
