package xyz.dgz48.tasks.webapi.dashboard.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;
import xyz.dgz48.tasks.webapi.dashboard.domain.TenantDashboardSummary;

class GetTenantDashboardSummaryUseCaseTest {

  private static final ZoneId JST = ZoneId.of("Asia/Tokyo");

  private final Clock clock =
      Clock.fixed(ZonedDateTime.of(2026, 1, 15, 19, 0, 0, 0, JST).toInstant(), JST);

  @Test
  void execute_resolvesTodayInJstAndDelegatesToPortWithTenantId() {
    DashboardQueryPort port = mock(DashboardQueryPort.class);
    TenantDashboardSummary expected = new TenantDashboardSummary(0, 0, 0, 0, Map.of(), Map.of(), 0);
    when(port.aggregateTenantSummary(eq(LocalDate.of(2026, 1, 15)), eq(42L))).thenReturn(expected);

    GetTenantDashboardSummaryUseCase useCase = new GetTenantDashboardSummaryUseCase(port, clock);

    assertThat(useCase.execute(42L)).isSameAs(expected);
  }
}
