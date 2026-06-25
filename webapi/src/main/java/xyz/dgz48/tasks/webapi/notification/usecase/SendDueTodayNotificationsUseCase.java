package xyz.dgz48.tasks.webapi.notification.usecase;

import io.micrometer.observation.annotation.Observed;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import xyz.dgz48.tasks.webapi.notification.domain.DueTodayNotification;

/**
 * B-01 期限当日通知メール(F-18)の送出ユースケース。
 *
 * <p>DB 読み取り(対象抽出)は {@link DueTodayNotificationQueryPort} 実装側でトランザクション境界を持ち、外部 I/O であるメール送出は
 * トランザクション外で受信者単位に行う(1 件の送出失敗が他受信者をブロックしないよう個別に捕捉)。ログには PII(メールアドレス・タスクタイトル)を出力しない。
 */
@Service
@RequiredArgsConstructor
public class SendDueTodayNotificationsUseCase {

  private static final Logger log = LoggerFactory.getLogger(SendDueTodayNotificationsUseCase.class);

  private final DueTodayNotificationQueryPort queryPort;
  private final EmailSenderPort emailSender;
  private final Clock clock;

  @Observed(name = "notification.dueToday")
  public SendResult execute() {
    LocalDate today = LocalDate.now(clock);
    List<DueTodayNotification> notifications = queryPort.findDueTodayRecipients(today);

    int sent = 0;
    int failed = 0;
    for (DueTodayNotification n : notifications) {
      try {
        emailSender.send(n.email(), buildSubject(n), buildBody(n));
        sent++;
      } catch (RuntimeException e) {
        failed++;
        // PII 非出力: 宛先・タイトルは記録せず、テナント/ユーザー ID と件数のみ。
        log.warn(
            "期限当日通知の送出に失敗 tenantId={} userId={} taskCount={}: {}",
            n.tenantId(),
            n.userId(),
            n.tasks().size(),
            e.getMessage());
      }
    }
    SendResult result = new SendResult(notifications.size(), sent, failed);
    log.info(
        "期限当日通知バッチ完了 date={} recipients={} sent={} failed={}",
        today,
        result.recipientCount(),
        result.sentCount(),
        result.failedCount());
    return result;
  }

  private static String buildSubject(DueTodayNotification n) {
    return "本日が期限のタスクが " + n.tasks().size() + " 件あります";
  }

  private static String buildBody(DueTodayNotification n) {
    StringBuilder sb = new StringBuilder();
    sb.append(n.fullName()).append(" 様\n\n");
    sb.append("本日が期限のタスクは以下のとおりです。\n\n");
    for (DueTodayNotification.DueTask task : n.tasks()) {
      sb.append("・").append(task.title()).append("\n");
    }
    sb.append("\nタスク管理システム\n");
    return sb.toString();
  }

  /** バッチ実行結果(ログ・監視用、PII を含まない)。 */
  public record SendResult(int recipientCount, int sentCount, int failedCount) {}
}
