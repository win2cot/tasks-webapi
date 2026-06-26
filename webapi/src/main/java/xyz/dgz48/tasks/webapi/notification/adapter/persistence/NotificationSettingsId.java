package xyz.dgz48.tasks.webapi.notification.adapter.persistence;

import java.io.Serializable;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

/** {@link NotificationSettingsJpaEntity} の複合主キークラス(user_id, tenant_id)。 */
@NoArgsConstructor
@EqualsAndHashCode
@SuppressWarnings("NullAway.Init") // JPA composite ID class requires no-args constructor
class NotificationSettingsId implements Serializable {
  @Nullable private Long userId;
  @Nullable private Long tenantId;

  NotificationSettingsId(Long userId, Long tenantId) {
    this.userId = userId;
    this.tenantId = tenantId;
  }
}
