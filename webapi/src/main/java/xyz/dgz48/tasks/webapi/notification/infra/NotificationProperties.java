package xyz.dgz48.tasks.webapi.notification.infra;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 通知機能の設定(application.yml の {@code notification.*})。
 *
 * <p>{@code email.provider} が {@code ses} のときのみ SES クライアントを生成し、それ以外(既定 {@code log})はログ出力フォールバックを使う。
 */
@ConfigurationProperties("notification")
public record NotificationProperties(@DefaultValue Email email) {

  public record Email(
      @DefaultValue("log") String provider,
      @DefaultValue("no-reply@example.invalid") String from) {}
}
