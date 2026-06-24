package xyz.dgz48.tasks.webapi.dashboard.adapter.persistence;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.jspecify.annotations.Nullable;

/**
 * summary 集計のための軽量射影 1 行。認可フィルタ通過タスク集合を 1 クエリで取得し、件数系・ブレークダウンを adapter 側で算出する (TEXT 列等の over-fetch
 * を避けつつ、認可述語を 1 箇所に集約する)。
 */
record DashboardSummaryRow(
    String status,
    String priority,
    LocalDate dueDate,
    @Nullable LocalDateTime completedAt,
    Long ownerId) {}
