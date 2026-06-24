package xyz.dgz48.tasks.webapi.dashboard.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import xyz.dgz48.tasks.webapi.dashboard.domain.DashboardSummary;

/** {@link DashboardQueryAdapter} の集計ロジック単体テスト(認可済みタスク集合の件数・ブレークダウン算出)。 */
class DashboardQueryAdapterTest {

  private static final Long USER_A = 1L;
  private static final Long USER_B = 2L;
  private static final LocalDate TODAY = LocalDate.of(2026, 1, 15);

  @Test
  void aggregateSummary_computesCountsAndBreakdowns() {
    DashboardTaskViewRepository repository = mock(DashboardTaskViewRepository.class);
    List<DashboardSummaryRow> rows =
        List.of(
            row("NOT_STARTED", "MEDIUM", TODAY, null, USER_A), // 当日期限 + 自分の未完了
            row("IN_PROGRESS", "MEDIUM", TODAY.minusDays(3), null, USER_A), // 期限超過 + 自分の未完了
            row("NOT_STARTED", "MEDIUM", TODAY, null, USER_B), // 当日期限(他人所有)
            row("NOT_STARTED", "HIGH", TODAY.plusDays(1), null, USER_B), // 先の予定
            row("DONE", "LOW", TODAY.minusDays(1), TODAY.atTime(10, 0), USER_A), // 本日完了
            row(
                "DONE",
                "LOW",
                TODAY.minusDays(2),
                TODAY.minusDays(1).atTime(10, 0),
                USER_B), // 昨日完了
            row("NOT_STARTED", "MEDIUM", TODAY.plusDays(2), null, USER_B), // 担当の予定
            row("NOT_STARTED", "MEDIUM", TODAY.plusDays(10), null, USER_A)); // 自分の未完了(窓外)
    when(repository.findVisibleSummaryRows(USER_A)).thenReturn(rows);

    DashboardQueryAdapter adapter = new DashboardQueryAdapter(repository);
    DashboardSummary summary = adapter.aggregateSummary(USER_A, TODAY);

    assertThat(summary.todayDueCount()).isEqualTo(2);
    assertThat(summary.overdueCount()).isEqualTo(1);
    assertThat(summary.completedTodayCount()).isEqualTo(1);
    assertThat(summary.myOpenCount()).isEqualTo(3);
    assertThat(summary.statusBreakdown())
        .containsEntry("NOT_STARTED", 5L)
        .containsEntry("IN_PROGRESS", 1L)
        .containsEntry("DONE", 2L)
        .containsEntry("ON_HOLD", 0L);
    assertThat(summary.priorityBreakdown())
        .containsEntry("HIGH", 1L)
        .containsEntry("MEDIUM", 5L)
        .containsEntry("LOW", 2L);
  }

  @Test
  void aggregateSummary_emptySet_returnsZeroedBreakdowns() {
    DashboardTaskViewRepository repository = mock(DashboardTaskViewRepository.class);
    when(repository.findVisibleSummaryRows(USER_A)).thenReturn(List.of());

    DashboardSummary summary =
        new DashboardQueryAdapter(repository).aggregateSummary(USER_A, TODAY);

    assertThat(summary.todayDueCount()).isZero();
    assertThat(summary.overdueCount()).isZero();
    assertThat(summary.completedTodayCount()).isZero();
    assertThat(summary.myOpenCount()).isZero();
    assertThat(summary.statusBreakdown())
        .containsOnlyKeys("NOT_STARTED", "IN_PROGRESS", "DONE", "ON_HOLD");
    assertThat(summary.statusBreakdown().values()).allMatch(v -> v == 0L);
    assertThat(summary.priorityBreakdown()).containsOnlyKeys("HIGH", "MEDIUM", "LOW");
  }

  private static DashboardSummaryRow row(
      String status, String priority, LocalDate dueDate, LocalDateTime completedAt, Long ownerId) {
    return new DashboardSummaryRow(status, priority, dueDate, completedAt, ownerId);
  }
}
