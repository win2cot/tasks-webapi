package xyz.dgz48.tasks.webapi.audit.infra;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import xyz.dgz48.tasks.webapi.audit.adapter.external.PropertyHmacKeyProvider;
import xyz.dgz48.tasks.webapi.audit.usecase.HmacKeyProvider;

/**
 * 監査ハッシュチェーンの HMAC 鍵プロバイダを構成する(ADR-0038 §3.3)。
 *
 * <p>既定は設定値由来の {@link PropertyHmacKeyProvider}(ローカル / テスト)。{@code source=ssm} の本番経路は別 PR で
 * {@code @ConditionalOnProperty} の SSM 実装を追加し、本フォールバックは {@link ConditionalOnMissingBean} で退避する。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AuditHashChainProperties.class)
class AuditHashChainConfig {

  @Bean
  @ConditionalOnMissingBean(HmacKeyProvider.class)
  HmacKeyProvider propertyHmacKeyProvider(AuditHashChainProperties properties) {
    return new PropertyHmacKeyProvider(properties.keyId(), properties.secret());
  }
}
