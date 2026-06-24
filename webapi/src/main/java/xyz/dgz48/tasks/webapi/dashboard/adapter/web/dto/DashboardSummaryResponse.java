package xyz.dgz48.tasks.webapi.dashboard.adapter.web.dto;

import java.util.Map;
import xyz.dgz48.tasks.webapi.dashboard.domain.DashboardSummary;

/** OpenAPI {@code DashboardSummary} に対応する数値カード集計レスポンス(S-03)。 */
public record DashboardSummaryResponse(
    long todayDueCount,
    long overdueCount,
    long completedTodayCount,
    long myOpenCount,
    Map<String, Long> statusBreakdown,
    Map<String, Long> priorityBreakdown) {

  public static DashboardSummaryResponse from(DashboardSummary summary) {
    return new DashboardSummaryResponse(
        summary.todayDueCount(),
        summary.overdueCount(),
        summary.completedTodayCount(),
        summary.myOpenCount(),
        summary.statusBreakdown(),
        summary.priorityBreakdown());
  }
}
