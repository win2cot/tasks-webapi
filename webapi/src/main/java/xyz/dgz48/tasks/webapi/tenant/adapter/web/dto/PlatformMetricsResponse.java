package xyz.dgz48.tasks.webapi.tenant.adapter.web.dto;

import xyz.dgz48.tasks.webapi.tenant.domain.PlatformMetrics;

/** A-27 プラットフォームメトリクスレスポンス(OpenAPI PlatformMetrics スキーマ)。 */
public record PlatformMetricsResponse(
    long totalTenants,
    long activeTenants,
    long suspendedTenants,
    long totalUsers,
    long totalTasks,
    long newTenantsLast24h) {

  public static PlatformMetricsResponse from(PlatformMetrics m) {
    return new PlatformMetricsResponse(
        m.totalTenants(),
        m.activeTenants(),
        m.suspendedTenants(),
        m.totalUsers(),
        m.totalTasks(),
        m.newTenantsLast24h());
  }
}
