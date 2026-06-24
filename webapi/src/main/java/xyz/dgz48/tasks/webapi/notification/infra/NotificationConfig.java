package xyz.dgz48.tasks.webapi.notification.infra;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import xyz.dgz48.tasks.webapi.notification.adapter.external.LoggingEmailSender;
import xyz.dgz48.tasks.webapi.notification.adapter.external.SesEmailSender;
import xyz.dgz48.tasks.webapi.notification.usecase.EmailSenderPort;

/**
 * 通知機能のメール送出経路を構成する。
 *
 * <p>{@code notification.email.provider=ses} のときのみ SES クライアントと {@link SesEmailSender} を生成する。それ以外(既定
 * {@code log})は {@link LoggingEmailSender} をフォールバックとして使う。{@code @Bean} の宣言順により、ses 経路が無いときだけ
 * フォールバックが選択される。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(NotificationProperties.class)
class NotificationConfig {

  @Bean
  @ConditionalOnProperty(name = "notification.email.provider", havingValue = "ses")
  SesV2Client sesV2Client() {
    // リージョン・認証情報は AWS SDK の標準プロバイダチェーン(ECS Fargate タスクロール / 環境変数)から解決する。
    return SesV2Client.create();
  }

  @Bean
  @ConditionalOnProperty(name = "notification.email.provider", havingValue = "ses")
  EmailSenderPort sesEmailSender(SesV2Client client, NotificationProperties properties) {
    return new SesEmailSender(client, properties.email().from());
  }

  @Bean
  @ConditionalOnMissingBean(EmailSenderPort.class)
  EmailSenderPort loggingEmailSender() {
    return new LoggingEmailSender();
  }
}
