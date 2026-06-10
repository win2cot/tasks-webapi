package xyz.dgz48.tasks.webapi.tenant.domain;

/** プラットフォーム全体メトリクス(A-27 / S-12 監視ダッシュボード)。 */
public record PlatformMetrics(
    long totalTenants,
    long activeTenants,
    long suspendedTenants,
    long totalUsers,
    long totalTasks,
    long newTenantsLast24h) {}
