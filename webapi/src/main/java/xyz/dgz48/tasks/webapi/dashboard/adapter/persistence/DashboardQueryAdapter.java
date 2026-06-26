package xyz.dgz48.tasks.webapi.dashboard.adapter.persistence;

import io.micrometer.observation.annotation.Observed;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import xyz.dgz48.tasks.webapi.dashboard.domain.DashboardSummary;
import xyz.dgz48.tasks.webapi.dashboard.domain.DashboardTask;
import xyz.dgz48.tasks.webapi.dashboard.domain.DashboardTaskSections;
import xyz.dgz48.tasks.webapi.dashboard.domain.TenantDashboardSummary;
import xyz.dgz48.tasks.webapi.dashboard.usecase.DashboardQueryPort;

/**
 * {@link DashboardQueryPort} の JPA 実装。
 *
 * <p>4 セクションは DB 側でソート済みの結果をそのままマッピングする。summary は認可フィルタ通過タスク集合を 1 クエリで取得し、各指標を
 * メモリ上で算出する(件数系・ブレークダウンともに同一集合が対象のため、認可述語を 1 箇所に集約しつつ N+1 を避ける)。
 */
@Observed(name = "dashboard.repository")
@Component
@RequiredArgsConstructor
class DashboardQueryAdapter implements DashboardQueryPort {

  private static final String DONE = "DONE";

  /** statusBreakdown のキー(OpenAPI TaskStatus enum)。0 件のステータスも 0 で明示するため全キーを初期化する。 */
  private static final List<String> STATUS_KEYS =
      List.of("NOT_STARTED", "IN_PROGRESS", "DONE", "ON_HOLD");

  /** priorityBreakdown のキー(OpenAPI Priority enum)。 */
  private static final List<String> PRIORITY_KEYS = List.of("HIGH", "MEDIUM", "LOW");

  private final DashboardTaskViewRepository repository;

  @Override
  public DashboardTaskSections findTaskSections(Long userId, LocalDate today, int dueWithinDays) {
    LocalDate upperBound = today.plusDays(dueWithinDays);
    LocalDateTime startOfDay = today.atStartOfDay();
    LocalDateTime endOfDay = today.plusDays(1).atStartOfDay();

    return new DashboardTaskSections(
        toDomainList(repository.findOverdue(userId, today)),
        toDomainList(repository.findToday(userId, today)),
        toDomainList(repository.findUpcoming(userId, today, upperBound)),
        toDomainList(repository.findCompletedToday(userId, startOfDay, endOfDay)));
  }

  @Override
  public DashboardSummary aggregateSummary(Long userId, LocalDate today) {
    List<DashboardSummaryRow> rows = repository.findVisibleSummaryRows(userId);
    AggregatedMetrics metrics = aggregate(rows, today);

    // 自分が所有する未完了(所有者視点指標、担当・関係者は含まない)。S-03 個人集計固有の指標。
    long myOpenCount =
        rows.stream()
            .filter(row -> !DONE.equals(row.status()) && row.ownerId().equals(userId))
            .count();

    return new DashboardSummary(
        metrics.todayDueCount(),
        metrics.overdueCount(),
        metrics.completedTodayCount(),
        myOpenCount,
        metrics.statusBreakdown(),
        metrics.priorityBreakdown());
  }

  @Override
  public TenantDashboardSummary aggregateTenantSummary(LocalDate today, Long tenantId) {
    List<DashboardSummaryRow> rows = repository.findTenantVisibleSummaryRows();
    AggregatedMetrics metrics = aggregate(rows, today);
    long memberCount = repository.countActiveMembers(tenantId);

    return new TenantDashboardSummary(
        rows.size(),
        metrics.todayDueCount(),
        metrics.overdueCount(),
        metrics.completedTodayCount(),
        metrics.statusBreakdown(),
        metrics.priorityBreakdown(),
        memberCount);
  }

  /** S-03 個人集計と S-15 運営者集計で共通の件数系・ブレークダウン指標(myOpenCount は除く)。 */
  private record AggregatedMetrics(
      long todayDueCount,
      long overdueCount,
      long completedTodayCount,
      Map<String, Long> statusBreakdown,
      Map<String, Long> priorityBreakdown) {}

  /**
   * 軽量射影行集合から共通指標を 1 パスで算出する。対象タスク集合の絞り込み(個人=3 役割評価 / 運営者=visibility ∈ {TENANT,
   * STAKEHOLDERS})は呼び出し側のクエリが担い、本メソッドは集計のみを担う。
   */
  private AggregatedMetrics aggregate(List<DashboardSummaryRow> rows, LocalDate today) {
    long todayDueCount = 0;
    long overdueCount = 0;
    long completedTodayCount = 0;
    Map<String, Long> statusBreakdown = newBreakdown(STATUS_KEYS);
    Map<String, Long> priorityBreakdown = newBreakdown(PRIORITY_KEYS);

    for (DashboardSummaryRow row : rows) {
      boolean done = DONE.equals(row.status());

      // 当日期限(due_date = TODAY、ステータス不問)
      if (row.dueDate().isEqual(today)) {
        todayDueCount++;
      }
      // 期限切れ未完了(due_date < TODAY かつ未完了)
      if (!done && row.dueDate().isBefore(today)) {
        overdueCount++;
      }
      // 本日完了(status = DONE かつ completed_at の日付 = TODAY、JST)
      LocalDateTime completedAt = row.completedAt();
      if (done && completedAt != null && completedAt.toLocalDate().isEqual(today)) {
        completedTodayCount++;
      }
      // ステータス別・優先度別
      statusBreakdown.merge(row.status(), 1L, (a, b) -> a + b);
      priorityBreakdown.merge(row.priority(), 1L, (a, b) -> a + b);
    }

    return new AggregatedMetrics(
        todayDueCount, overdueCount, completedTodayCount, statusBreakdown, priorityBreakdown);
  }

  private static Map<String, Long> newBreakdown(List<String> keys) {
    Map<String, Long> map = new LinkedHashMap<>();
    for (String key : keys) {
      map.put(key, 0L);
    }
    return map;
  }

  private static List<DashboardTask> toDomainList(List<DashboardTaskView> views) {
    return views.stream().map(DashboardQueryAdapter::toDomain).toList();
  }

  private static DashboardTask toDomain(DashboardTaskView v) {
    return new DashboardTask(
        v.getId(),
        v.getVersion(),
        v.getTitle(),
        v.getDescription(),
        v.getStatus(),
        v.getPriority(),
        v.getVisibility(),
        v.getOwnerId(),
        v.getAssigneeId(),
        v.getDueDate(),
        v.getCompletedAt(),
        v.getCreatedAt(),
        v.getUpdatedAt());
  }
}
