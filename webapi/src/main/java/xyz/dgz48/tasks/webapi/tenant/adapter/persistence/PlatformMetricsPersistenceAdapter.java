package xyz.dgz48.tasks.webapi.tenant.adapter.persistence;

import io.micrometer.observation.annotation.Observed;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import xyz.dgz48.tasks.webapi.shared.infra.AppZones;
import xyz.dgz48.tasks.webapi.tenant.domain.PlatformMetrics;
import xyz.dgz48.tasks.webapi.tenant.domain.TenantStatus;
import xyz.dgz48.tasks.webapi.tenant.usecase.PlatformMetricsPort;

/** {@link PlatformMetricsPort} の JPA 実装。 */
@Observed(name = "tenant.repository")
@Component
@RequiredArgsConstructor
class PlatformMetricsPersistenceAdapter implements PlatformMetricsPort {

  private final TenantJpaRepository tenantJpaRepository;
  private final Clock clock;

  @Override
  @Transactional(readOnly = true)
  public PlatformMetrics getMetrics() {
    long totalTenants = tenantJpaRepository.countByStatusNot(TenantStatus.DELETED);
    long activeTenants = tenantJpaRepository.countByStatus(TenantStatus.ACTIVE);
    long suspendedTenants = tenantJpaRepository.countByStatus(TenantStatus.SUSPENDED);
    long totalUsers = tenantJpaRepository.countTotalUsers();
    long totalTasks = tenantJpaRepository.countTotalTasks();
    LocalDateTime since = LocalDateTime.now(clock.withZone(AppZones.JST)).minusHours(24);
    long newTenantsLast24h =
        tenantJpaRepository.countByCreatedAtGreaterThanEqualAndStatusNot(
            since, TenantStatus.DELETED);
    return new PlatformMetrics(
        totalTenants, activeTenants, suspendedTenants, totalUsers, totalTasks, newTenantsLast24h);
  }
}
