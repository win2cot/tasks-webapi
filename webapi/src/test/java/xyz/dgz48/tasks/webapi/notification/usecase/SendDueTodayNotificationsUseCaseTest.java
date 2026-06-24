package xyz.dgz48.tasks.webapi.notification.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import xyz.dgz48.tasks.webapi.notification.domain.DueTodayNotification;
import xyz.dgz48.tasks.webapi.notification.domain.DueTodayNotification.DueTask;
import xyz.dgz48.tasks.webapi.notification.usecase.EmailSenderPort.EmailSendException;

class SendDueTodayNotificationsUseCaseTest {

  private static final ZoneId JST = ZoneId.of("Asia/Tokyo");
  private final Clock clock =
      Clock.fixed(ZonedDateTime.of(2026, 1, 15, 19, 0, 0, 0, JST).toInstant(), JST);

  private final DueTodayNotificationQueryPort queryPort = mock(DueTodayNotificationQueryPort.class);
  private final EmailSenderPort emailSender = mock(EmailSenderPort.class);
  private final SendDueTodayNotificationsUseCase useCase =
      new SendDueTodayNotificationsUseCase(queryPort, emailSender, clock);

  private static DueTodayNotification notification(long userId, String email, String... titles) {
    List<DueTask> tasks = new java.util.ArrayList<>();
    for (int i = 0; i < titles.length; i++) {
      tasks.add(new DueTask((long) (i + 1), titles[i]));
    }
    return new DueTodayNotification(100L, userId, email, "氏名" + userId, tasks);
  }

  @Test
  void execute_queriesWithJstToday_andSendsOnePerRecipient() {
    when(queryPort.findDueTodayRecipients(eq(LocalDate.of(2026, 1, 15))))
        .thenReturn(
            List.of(
                notification(1L, "a@example.com", "資料作成", "レビュー"),
                notification(2L, "b@example.com", "打合せ")));
    doNothing().when(emailSender).send(anyString(), anyString(), anyString());

    SendDueTodayNotificationsUseCase.SendResult result = useCase.execute();

    assertThat(result.recipientCount()).isEqualTo(2);
    assertThat(result.sentCount()).isEqualTo(2);
    assertThat(result.failedCount()).isZero();
    verify(emailSender, times(2)).send(anyString(), anyString(), anyString());
  }

  @Test
  void execute_buildsSubjectAndBodyFromTasks() {
    when(queryPort.findDueTodayRecipients(any()))
        .thenReturn(List.of(notification(1L, "a@example.com", "資料作成", "レビュー")));

    useCase.execute();

    ArgumentCaptor<String> to = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
    verify(emailSender).send(to.capture(), subject.capture(), body.capture());
    assertThat(to.getValue()).isEqualTo("a@example.com");
    assertThat(subject.getValue()).contains("2 件");
    assertThat(body.getValue()).contains("資料作成").contains("レビュー");
  }

  @Test
  void execute_continuesAfterPerRecipientFailure() {
    when(queryPort.findDueTodayRecipients(any()))
        .thenReturn(
            List.of(
                notification(1L, "fail@example.com", "失敗"),
                notification(2L, "ok@example.com", "成功")));
    doThrow(new EmailSendException("boom", new RuntimeException()))
        .when(emailSender)
        .send(eq("fail@example.com"), anyString(), anyString());

    SendDueTodayNotificationsUseCase.SendResult result = useCase.execute();

    assertThat(result.recipientCount()).isEqualTo(2);
    assertThat(result.sentCount()).isEqualTo(1);
    assertThat(result.failedCount()).isEqualTo(1);
    verify(emailSender).send(eq("ok@example.com"), anyString(), anyString());
  }

  @Test
  void execute_noRecipients_sendsNothing() {
    when(queryPort.findDueTodayRecipients(any())).thenReturn(List.of());

    SendDueTodayNotificationsUseCase.SendResult result = useCase.execute();

    assertThat(result.recipientCount()).isZero();
    verify(emailSender, never()).send(anyString(), anyString(), anyString());
  }
}
