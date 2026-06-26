package xyz.dgz48.tasks.webapi.notification.adapter.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import org.jspecify.annotations.Nullable;
import xyz.dgz48.tasks.webapi.notification.domain.NotificationSettings;
import xyz.dgz48.tasks.webapi.shared.infra.AppZones;

/**
 * OpenAPI {@code NotificationSettings} に対応するレスポンス(S-10)。
 *
 * <p>{@code updatedAt} はレコード未存在(デフォルト)時 {@code null}。スキーマ上 nullable でないため、null のときはフィールド自体を
 * 省略する({@code JsonInclude.NON_NULL})。値ありのときは JST オフセット付き(ADR-0009)。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NotificationSettingsResponse(
    boolean emailDueToday,
    boolean emailOverdue,
    boolean emailStakeholder,
    @Nullable OffsetDateTime updatedAt) {

  public static NotificationSettingsResponse from(NotificationSettings settings) {
    OffsetDateTime updatedAt =
        settings.updatedAt() == null
            ? null
            : settings.updatedAt().atZone(AppZones.JST).toOffsetDateTime();
    return new NotificationSettingsResponse(
        settings.emailDueToday(), settings.emailOverdue(), settings.emailStakeholder(), updatedAt);
  }
}
