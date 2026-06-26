package xyz.dgz48.tasks.webapi.dashboard.adapter.web.dto;

import java.util.Map;
import xyz.dgz48.tasks.webapi.dashboard.domain.TenantDashboardSummary;

/** OpenAPI {@code TenantDashboardSummary} に対応する運営者向け数値カード集計レスポンス(S-15)。 */
public record TenantDashboardSummaryResponse(
    long totalTaskCount,
    long todayDueCount,
    long overdueCount,
    long completedTodayCount,
    Map<String, Long> statusBreakdown,
    Map<String, Long> priorityBreakdown,
    long memberCount) {

  public static TenantDashboardSummaryResponse from(TenantDashboardSummary summary) {
    return new TenantDashboardSummaryResponse(
        summary.totalTaskCount(),
        summary.todayDueCount(),
        summary.overdueCount(),
        summary.completedTodayCount(),
        summary.statusBreakdown(),
        summary.priorityBreakdown(),
        summary.memberCount());
  }
}
