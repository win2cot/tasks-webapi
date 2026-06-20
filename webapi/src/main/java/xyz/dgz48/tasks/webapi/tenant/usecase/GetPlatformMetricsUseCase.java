package xyz.dgz48.tasks.webapi.tenant.usecase;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.dgz48.tasks.webapi.audit.domain.AuditEventType;
import xyz.dgz48.tasks.webapi.audit.usecase.AuditLogPort;
import xyz.dgz48.tasks.webapi.shared.usecase.TenantFilterBypassService;
import xyz.dgz48.tasks.webapi.tenant.domain.PlatformMetrics;

/**
 * A-27: プラットフォームメトリクス取得ユースケース(SaaS Admin 専用)。
 *
 * <p>成功時に {@code PLATFORM_METRICS_VIEWED} として {@code tenant_id=NULL}(横断集計)で監査ログに記録する(ADR-0020
 * §3.1)。
 */
@Service
@RequiredArgsConstructor
public class GetPlatformMetricsUseCase {

  private final PlatformMetricsPort platformMetricsPort;
  private final TenantFilterBypassService tenantFilterBypassService;
  private final AuditLogPort auditLogPort;

  @Transactional
  public PlatformMetrics execute(Long userId) {
    PlatformMetrics metrics =
        tenantFilterBypassService.runAsSaaSAdmin(platformMetricsPort::getMetrics);

    auditLogPort.record(AuditEventType.PLATFORM_METRICS_VIEWED, null, userId, Map.of());

    return metrics;
  }
}
