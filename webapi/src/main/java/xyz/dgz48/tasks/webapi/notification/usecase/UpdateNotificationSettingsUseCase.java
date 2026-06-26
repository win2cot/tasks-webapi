package xyz.dgz48.tasks.webapi.notification.usecase;

import io.micrometer.observation.annotation.Observed;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.dgz48.tasks.webapi.notification.domain.NotificationSettings;

/**
 * 通知設定更新(A-24、operationId: updateNotificationSettings)のユースケース。
 *
 * <p>レコード未存在時は upsert(新規作成)する。{@code updated_at} はサーバ側システム時刻(JST、ADR-0009)。
 */
@Service
@RequiredArgsConstructor
public class UpdateNotificationSettingsUseCase {

  private final NotificationSettingsPort notificationSettingsPort;
  private final Clock clock;

  @Observed(name = "notification.settings.update")
  @Transactional
  public NotificationSettings execute(UpdateNotificationSettingsCommand command) {
    return notificationSettingsPort.upsert(
        command.userId(),
        command.tenantId(),
        command.emailDueToday(),
        command.emailOverdue(),
        command.emailStakeholder(),
        LocalDateTime.now(clock));
  }
}
