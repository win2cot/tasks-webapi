package xyz.dgz48.tasks.webapi.notification.domain;

import java.time.LocalDateTime;
import org.jspecify.annotations.Nullable;

/**
 * ユーザー通知設定(S-10、現在のテナントスコープ)。{@code user_notification_settings} に対応。
 *
 * <p>レコードが存在しない場合は {@link #defaults()}(全フラグ true、{@code updatedAt} は未設定)を用いる。
 *
 * @param updatedAt 最終更新日時。レコード未存在(デフォルト)時は {@code null}。
 */
public record NotificationSettings(
    boolean emailDueToday,
    boolean emailOverdue,
    boolean emailStakeholder,
    @Nullable LocalDateTime updatedAt) {

  /** レコード未存在時の既定値(全フラグ true)。 */
  public static NotificationSettings defaults() {
    return new NotificationSettings(true, true, true, null);
  }
}
