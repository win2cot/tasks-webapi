package xyz.dgz48.tasks.webapi.dashboard.domain;

import java.util.Map;

/**
 * S-15 テナント運営者向けダッシュボードの数値カード集計(OpenAPI {@code TenantDashboardSummary})。
 *
 * <p>Tenant Admin がテナント運営視点で把握する集計(基本設計書 §3.3.4 / §6.2.2.2、ADR-0005 §3.5)。
 *
 * <ul>
 *   <li>集計対象は自テナント内の {@code visibility ∈ {TENANT, STAKEHOLDERS}} のタスクのみ。
 *   <li><b>{@code PRIVATE} タスクは件数も含めて集計対象外</b>(NIST AC-4 整合、Tenant Admin であっても存在を集計値経由で推定できない)。
 *   <li>{@code memberCount} はテナントの全 ACTIVE 所属ユーザー数(Tenant Admin を含む)。
 * </ul>
 */
public record TenantDashboardSummary(
    long totalTaskCount,
    long todayDueCount,
    long overdueCount,
    long completedTodayCount,
    Map<String, Long> statusBreakdown,
    Map<String, Long> priorityBreakdown,
    long memberCount) {}
