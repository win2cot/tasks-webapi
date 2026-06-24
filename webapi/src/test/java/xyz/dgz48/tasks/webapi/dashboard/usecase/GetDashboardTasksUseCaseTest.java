package xyz.dgz48.tasks.webapi.dashboard.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import xyz.dgz48.tasks.webapi.dashboard.domain.DashboardTaskSections;

class GetDashboardTasksUseCaseTest {

  private static final ZoneId JST = ZoneId.of("Asia/Tokyo");

  /** JST の当日(2026-01-15)を Clock として固定する。 */
  private final Clock clock =
      Clock.fixed(ZonedDateTime.of(2026, 1, 15, 19, 0, 0, 0, JST).toInstant(), JST);

  @Test
  void execute_resolvesTodayInJstAndDelegatesToPort() {
    DashboardQueryPort port = mock(DashboardQueryPort.class);
    DashboardTaskSections expected =
        new DashboardTaskSections(List.of(), List.of(), List.of(), List.of());
    when(port.findTaskSections(eq(42L), eq(LocalDate.of(2026, 1, 15)), eq(5))).thenReturn(expected);

    GetDashboardTasksUseCase useCase = new GetDashboardTasksUseCase(port, clock);

    assertThat(useCase.execute(42L, 5)).isSameAs(expected);
  }
}
