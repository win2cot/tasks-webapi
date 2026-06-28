package xyz.dgz48.tasks.webapi.user.infra;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import xyz.dgz48.tasks.webapi.user.adapter.external.LoggingCredentialProvisioningAdapter;
import xyz.dgz48.tasks.webapi.user.usecase.CredentialProvisioningPort;

/**
 * credential プロビジョニング実装の選択(ADR-0040 §3.1)。
 *
 * <p>本番の Keycloak Admin REST API 実装({@code keycloak.admin.enabled=true} で有効、後続
 * PR)が存在しない場合は、dev/test 用の {@link LoggingCredentialProvisioningAdapter}
 * にフォールバックする({@code @ConditionalOnMissingBean})。SES の log フォールバックと同方式。
 */
@Configuration
public class CredentialProvisioningConfig {

  @Bean
  @ConditionalOnMissingBean(CredentialProvisioningPort.class)
  public CredentialProvisioningPort loggingCredentialProvisioningAdapter() {
    return new LoggingCredentialProvisioningAdapter();
  }
}
