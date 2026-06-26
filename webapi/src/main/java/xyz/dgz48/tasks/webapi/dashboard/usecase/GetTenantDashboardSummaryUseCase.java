package xyz.dgz48.tasks.webapi.dashboard.usecase;

import io.micrometer.observation.annotation.Observed;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.dgz48.tasks.webapi.dashboard.domain.TenantDashboardSummary;

/**
 * S-15 テナント運営者向けダッシュボードの数値カード集計ユースケース(operationId: getTenantDashboardSummary)。
 *
 * <p>「今日」をサーバ側システム日付(JST、ADR-0009)で確定し、{@code visibility ∈ {TENANT, STAKEHOLDERS}} のタスク集合の集計を
 * {@link DashboardQueryPort} に委譲する({@code PRIVATE} は除外、ADR-0005 §3.5 / NIST AC-4)。
 */
@Service
@RequiredArgsConstructor
public class GetTenantDashboardSummaryUseCase {

  private final DashboardQueryPort dashboardQueryPort;
  private final Clock clock;

  @Observed(name = "dashboard.tenant.summary")
  @Transactional(readOnly = true)
  public TenantDashboardSummary execute(Long tenantId) {
    LocalDate today = LocalDate.now(clock);
    return dashboardQueryPort.aggregateTenantSummary(today, tenantId);
  }
}
