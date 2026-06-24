package xyz.dgz48.tasks.webapi.dashboard.domain;

import java.util.Map;

/**
 * S-03 個人視点ダッシュボードの数値カード集計(OpenAPI {@code DashboardSummary})。
 *
 * <p>集計対象は §6.2.1 のタスク参照認可フィルタ(3 役割評価、ADR-0005)通過後のタスク集合。Tenant Admin であっても特別権限はなく、
 * 通常の参照認可フィルタが適用される(基本設計書 §6.2.2.1)。
 *
 * <ul>
 *   <li>{@code myOpenCount} は「自分が <b>所有</b> する未完了件数」(所有者視点指標)。担当・関係者として関与するタスクは含まない。
 *   <li>{@code statusBreakdown} / {@code priorityBreakdown} は認可フィルタ通過タスク全体が対象(myOpenCount
 *       とはスコープが異なる)。
 * </ul>
 */
public record DashboardSummary(
    long todayDueCount,
    long overdueCount,
    long completedTodayCount,
    long myOpenCount,
    Map<String, Long> statusBreakdown,
    Map<String, Long> priorityBreakdown) {}
