package xyz.dgz48.tasks.webapi.tenant.adapter.persistence;

import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import xyz.dgz48.tasks.webapi.shared.infra.AppZones;
import xyz.dgz48.tasks.webapi.tenant.domain.PlatformMetrics;
import xyz.dgz48.tasks.webapi.tenant.usecase.PlatformMetricsPort;

/** {@link PlatformMetricsPort} の JPA 実装。 */
@Component
@RequiredArgsConstructor
class PlatformMetricsPersistenceAdapter implements PlatformMetricsPort {

  private final TenantJpaRepository tenantJpaRepository;
  private final Clock clock;

  @Override
  @Transactional(readOnly = true)
  public PlatformMetrics getMetrics() {
    long totalTenants = tenantJpaRepository.countNonDeletedTenants();
    long activeTenants = tenantJpaRepository.countActiveTenants();
    long suspendedTenants = tenantJpaRepository.countSuspendedTenants();
    long totalUsers = tenantJpaRepository.countTotalUsers();
    long totalTasks = tenantJpaRepository.countTotalTasks();
    LocalDateTime since = LocalDateTime.now(clock.withZone(AppZones.JST)).minusHours(24);
    long newTenantsLast24h = tenantJpaRepository.countTenantsCreatedSince(since);
    return new PlatformMetrics(
        totalTenants, activeTenants, suspendedTenants, totalUsers, totalTasks, newTenantsLast24h);
  }
}
