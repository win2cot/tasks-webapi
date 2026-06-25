package xyz.dgz48.tasks.webapi.notification.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import xyz.dgz48.tasks.webapi.notification.usecase.SendDueTodayNotificationsUseCase;
import xyz.dgz48.tasks.webapi.notification.usecase.SendDueTodayNotificationsUseCase.SendResult;

class NotificationBatchSchedulerTest {

  @Test
  void runDueTodayNotification_delegatesToUseCase() {
    SendDueTodayNotificationsUseCase useCase = mock(SendDueTodayNotificationsUseCase.class);
    when(useCase.execute()).thenReturn(new SendResult(0, 0, 0));
    NotificationBatchScheduler scheduler = new NotificationBatchScheduler(useCase);

    scheduler.runDueTodayNotification();

    verify(useCase).execute();
  }

  @Test
  void runDueTodayNotification_clearsBatchIdFromMdcAfterRun() {
    SendDueTodayNotificationsUseCase useCase = mock(SendDueTodayNotificationsUseCase.class);
    when(useCase.execute()).thenReturn(new SendResult(0, 0, 0));
    NotificationBatchScheduler scheduler = new NotificationBatchScheduler(useCase);

    scheduler.runDueTodayNotification();

    assertThat(MDC.get("batchId")).isNull();
  }
}
