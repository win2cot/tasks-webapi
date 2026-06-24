package xyz.dgz48.tasks.webapi.dashboard.usecase;

import io.micrometer.observation.annotation.Observed;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.dgz48.tasks.webapi.dashboard.domain.DashboardTaskSections;

/**
 * S-03 ダッシュボードの 4 セクション取得ユースケース(operationId: getDashboardTasks)。
 *
 * <p>「今日」をサーバ側システム日付(JST、ADR-0009)で確定し、認可済みタスク集合の 4 セクション分割を {@link DashboardQueryPort} に委譲する。
 */
@Service
@RequiredArgsConstructor
public class GetDashboardTasksUseCase {

  private final DashboardQueryPort dashboardQueryPort;
  private final Clock clock;

  @Observed(name = "dashboard.tasks")
  @Transactional(readOnly = true)
  public DashboardTaskSections execute(Long userId, int dueWithinDays) {
    LocalDate today = LocalDate.now(clock);
    return dashboardQueryPort.findTaskSections(userId, today, dueWithinDays);
  }
}
