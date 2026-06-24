package xyz.dgz48.tasks.webapi.dashboard.adapter.web.dto;

import java.util.List;

/** OpenAPI {@code DashboardTaskSections} に対応する 4 セクション一括レスポンス(S-03)。 */
public record DashboardTaskSectionsResponse(
    List<DashboardTaskItemResponse> overdue,
    List<DashboardTaskItemResponse> today,
    List<DashboardTaskItemResponse> upcoming,
    List<DashboardTaskItemResponse> completedToday) {}
