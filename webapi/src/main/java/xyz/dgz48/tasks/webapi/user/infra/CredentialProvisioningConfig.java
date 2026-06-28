package xyz.dgz48.tasks.webapi.user.infra;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.web.client.RestClient;
import xyz.dgz48.tasks.webapi.user.adapter.external.KeycloakAdminCredentialAdapter;
import xyz.dgz48.tasks.webapi.user.adapter.external.LoggingCredentialProvisioningAdapter;
import xyz.dgz48.tasks.webapi.user.usecase.CredentialProvisioningPort;

/**
 * credential プロビジョニング実装の選択(ADR-0040 §3.1)。
 *
 * <p>{@code keycloak.admin.enabled=true}(本番)では Keycloak Admin REST API 実装({@link
 * KeycloakAdminCredentialAdapter})を用い、未設定(dev/test)では {@code @ConditionalOnMissingBean} で {@link
 * LoggingCredentialProvisioningAdapter} にフォールバックする。SES の log フォールバックと同方式。
 */
@Configuration
@EnableConfigurationProperties(KeycloakAdminProperties.class)
@ImportRuntimeHints(KeycloakAdminCredentialAdapter.Hints.class)
public class CredentialProvisioningConfig {

  @Bean
  @ConditionalOnProperty(name = "keycloak.admin.enabled", havingValue = "true")
  public CredentialProvisioningPort keycloakAdminCredentialProvisioningAdapter(
      KeycloakAdminProperties props) {
    RestClient restClient = RestClient.builder().baseUrl(props.serverUrl()).build();
    return new KeycloakAdminCredentialAdapter(restClient, props);
  }

  @Bean
  @ConditionalOnMissingBean(CredentialProvisioningPort.class)
  public CredentialProvisioningPort loggingCredentialProvisioningAdapter() {
    return new LoggingCredentialProvisioningAdapter();
  }
}
