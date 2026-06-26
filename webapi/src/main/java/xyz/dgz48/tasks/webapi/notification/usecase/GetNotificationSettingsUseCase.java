package xyz.dgz48.tasks.webapi.notification.usecase;

import io.micrometer.observation.annotation.Observed;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.dgz48.tasks.webapi.notification.domain.NotificationSettings;

/**
 * 通知設定取得(A-23、operationId: getNotificationSettings)のユースケース。
 *
 * <p>レコードが無い場合は {@link NotificationSettings#defaults()}(全 true)を返す。
 */
@Service
@RequiredArgsConstructor
public class GetNotificationSettingsUseCase {

  private final NotificationSettingsPort notificationSettingsPort;

  @Observed(name = "notification.settings.get")
  @Transactional(readOnly = true)
  public NotificationSettings execute(Long userId) {
    return notificationSettingsPort.find(userId).orElseGet(NotificationSettings::defaults);
  }
}
