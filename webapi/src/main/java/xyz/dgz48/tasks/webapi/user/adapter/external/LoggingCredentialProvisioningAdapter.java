package xyz.dgz48.tasks.webapi.user.adapter.external;

import lombok.extern.slf4j.Slf4j;
import xyz.dgz48.tasks.webapi.user.usecase.CredentialProvisioningPort;

/**
 * {@link CredentialProvisioningPort} の dev/test 用フォールバック実装。
 *
 * <p>Keycloak を呼ばず、credential プロビジョニングをスキップした旨をログに残すだけ(パスワードはログに出さない)。本番では Keycloak Admin REST API
 * 実装が {@code keycloak.admin.enabled=true} で優先され、こちらは {@code @ConditionalOnMissingBean}
 * で抑止される(ADR-0040 §3.1、SES の log フォールバックと同方式)。
 */
@Slf4j
public class LoggingCredentialProvisioningAdapter implements CredentialProvisioningPort {

  @Override
  public void provisionCredential(String email, String displayName, String rawPassword) {
    log.info("[dev] credential provisioning skipped (logging adapter): email={}", email);
  }
}
