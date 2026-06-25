package xyz.dgz48.tasks.webapi.notification.infra;

import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import xyz.dgz48.tasks.webapi.notification.usecase.SendDueTodayNotificationsUseCase;

/**
 * B-01 期限当日通知メールのスケジューラ(基本設計書 §8.1、毎日 09:00 JST)。
 *
 * <p>{@link SchedulerLock} により複数ノードでも単一ノードのみが実行する(設計規約 §7)。実行時刻・タイムゾーン・有効化は {@code
 * notification.batch.*} で設定可能(既定: 09:00 / Asia/Tokyo / 有効)。MDC に {@code batchId} を設定しログ追跡可能にする。
 */
@Component
@ConditionalOnProperty(
    name = "notification.batch.enabled",
    havingValue = "true",
    matchIfMissing = true)
@RequiredArgsConstructor
public class NotificationBatchScheduler {

  private static final String BATCH_ID = "B-01";

  private final SendDueTodayNotificationsUseCase sendDueTodayNotificationsUseCase;

  @Scheduled(
      cron = "${notification.batch.cron:0 0 9 * * *}",
      zone = "${notification.batch.zone:Asia/Tokyo}")
  @SchedulerLock(
      name = "dueTodayNotificationBatch",
      lockAtLeastFor = "PT1M",
      lockAtMostFor = "PT10M")
  public void runDueTodayNotification() {
    MDC.put("batchId", BATCH_ID);
    try {
      sendDueTodayNotificationsUseCase.execute();
    } finally {
      MDC.remove("batchId");
    }
  }
}
