package xyz.dgz48.tasks.webapi.dashboard.domain;

import java.util.List;

/**
 * S-03 個人視点ダッシュボードの「当日対応 + 振り返り」4 セクション(OpenAPI {@code DashboardTaskSections})。
 *
 * <p>各リストはサーバ側でセクション別にソート済み(screen-flow.md §5.1 / OpenAPI 契約)。抽出条件が排他のため 同一タスクが複数セクションに重複することはない。
 */
public record DashboardTaskSections(
    List<DashboardTask> overdue,
    List<DashboardTask> today,
    List<DashboardTask> upcoming,
    List<DashboardTask> completedToday) {}
