package xyz.dgz48.tasks.webapi.dashboard.usecase;

import io.micrometer.observation.annotation.Observed;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.dgz48.tasks.webapi.dashboard.domain.DashboardSummary;

/**
 * S-03 ダッシュボードの数値カード集計ユースケース(operationId: getDashboardSummary)。
 *
 * <p>「今日」をサーバ側システム日付(JST、ADR-0009)で確定し、認可済みタスク集合の件数集計を {@link DashboardQueryPort} に委譲する。
 */
@Service
@RequiredArgsConstructor
public class GetDashboardSummaryUseCase {

  private final DashboardQueryPort dashboardQueryPort;
  private final Clock clock;

  @Observed(name = "dashboard.summary")
  @Transactional(readOnly = true)
  public DashboardSummary execute(Long userId) {
    LocalDate today = LocalDate.now(clock);
    return dashboardQueryPort.aggregateSummary(userId, today);
  }
}
