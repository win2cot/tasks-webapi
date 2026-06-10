package xyz.dgz48.tasks.webapi.tenant.usecase;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.dgz48.tasks.webapi.audit.domain.AuditEventType;
import xyz.dgz48.tasks.webapi.audit.usecase.AuditLogPort;
import xyz.dgz48.tasks.webapi.shared.usecase.TenantFilterBypassService;
import xyz.dgz48.tasks.webapi.tenant.domain.Tenant;
import xyz.dgz48.tasks.webapi.tenant.domain.TenantStatus;

/**
 * A-04: テナント一覧取得ユースケース(SaaS Admin 専用)。
 *
 * <p>成功時に {@code TENANT_LIST_VIEWED} として {@code tenant_id=NULL} で監査ログに記録する(ADR-0020 §3.1)。
 */
@Service
@RequiredArgsConstructor
public class ListTenantsUseCase {

  private final AdminTenantRepository adminTenantRepository;
  private final TenantFilterBypassService tenantFilterBypassService;
  private final AuditLogPort auditLogPort;

  @Transactional
  public Page<Tenant> execute(
      Long userId, @Nullable TenantStatus status, @Nullable String keyword, Pageable pageable) {
    Page<Tenant> result =
        tenantFilterBypassService.runAsSaaSAdmin(
            () -> adminTenantRepository.findAll(status, keyword, pageable));

    String detail = buildListDetail(status, keyword, result.getTotalElements());
    auditLogPort.record(AuditEventType.TENANT_LIST_VIEWED, null, userId, detail);

    return result;
  }

  private static String buildListDetail(
      @Nullable TenantStatus status, @Nullable String keyword, long totalElements) {
    StringBuilder sb = new StringBuilder("{");
    sb.append("\"totalElements\":").append(totalElements);
    if (status != null) {
      sb.append(",\"statusFilter\":\"").append(status.name()).append("\"");
    }
    if (keyword != null && !keyword.isBlank()) {
      sb.append(",\"hasKeyword\":true");
    }
    sb.append("}");
    return sb.toString();
  }
}
