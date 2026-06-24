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
import xyz.dgz48.tasks.webapi.dashboard.domain.DashboardSummary;

class GetDashboardSummaryUseCaseTest {

  private static final ZoneId JST = ZoneId.of("Asia/Tokyo");

  private final Clock clock =
      Clock.fixed(ZonedDateTime.of(2026, 1, 15, 19, 0, 0, 0, JST).toInstant(), JST);

  @Test
  void execute_resolvesTodayInJstAndDelegatesToPort() {
    DashboardQueryPort port = mock(DashboardQueryPort.class);
    DashboardSummary expected = new DashboardSummary(0, 0, 0, 0, Map.of(), Map.of());
    when(port.aggregateSummary(eq(7L), eq(LocalDate.of(2026, 1, 15)))).thenReturn(expected);

    GetDashboardSummaryUseCase useCase = new GetDashboardSummaryUseCase(port, clock);

    assertThat(useCase.execute(7L)).isSameAs(expected);
  }
}
