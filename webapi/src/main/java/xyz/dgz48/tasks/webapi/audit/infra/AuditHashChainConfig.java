package xyz.dgz48.tasks.webapi.audit.infra;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.ssm.SsmClient;
import xyz.dgz48.tasks.webapi.audit.adapter.external.PropertyHmacKeyProvider;
import xyz.dgz48.tasks.webapi.audit.adapter.external.SsmHmacKeyProvider;
import xyz.dgz48.tasks.webapi.audit.usecase.HmacKeyProvider;

/**
 * 監査ハッシュチェーンの HMAC 鍵プロバイダを構成する(ADR-0038 §3.3)。
 *
 * <p>{@code source=ssm}(本番)は Parameter Store から鍵をロードする {@link SsmHmacKeyProvider} を生成する。それ以外 (既定
 * {@code property}、ローカル / テスト)は設定値由来の {@link PropertyHmacKeyProvider} をフォールバックとして使う ({@link
 * ConditionalOnMissingBean} により ssm 経路が無いときだけ選択される)。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AuditHashChainProperties.class)
class AuditHashChainConfig {

  @Bean
  @ConditionalOnProperty(name = "audit.hash-chain.source", havingValue = "ssm")
  SsmClient auditSsmClient() {
    // リージョン・認証情報は AWS SDK の標準プロバイダチェーン(ECS タスクロール / 環境変数)から解決する。
    return SsmClient.create();
  }

  @Bean
  @ConditionalOnProperty(name = "audit.hash-chain.source", havingValue = "ssm")
  HmacKeyProvider ssmHmacKeyProvider(
      SsmClient auditSsmClient, AuditHashChainProperties properties) {
    return new SsmHmacKeyProvider(
        auditSsmClient, properties.keyId(), properties.ssmParameterPrefix());
  }

  @Bean
  @ConditionalOnMissingBean(HmacKeyProvider.class)
  HmacKeyProvider propertyHmacKeyProvider(AuditHashChainProperties properties) {
    return new PropertyHmacKeyProvider(properties.keyId(), properties.secret());
  }
}
