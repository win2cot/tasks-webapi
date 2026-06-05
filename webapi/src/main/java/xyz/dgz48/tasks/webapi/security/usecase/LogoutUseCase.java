package xyz.dgz48.tasks.webapi.security.usecase;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

/** Keycloak の end_session_endpoint へのログアウト URL を構築するユースケース。 */
@Component
public class LogoutUseCase {

  private final String issuerUri;

  public LogoutUseCase(
      @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri) {
    this.issuerUri = issuerUri;
  }

  /**
   * Keycloak の end_session_endpoint URL を構築する。
   *
   * @param idTokenHint クライアントが保持する ID トークン(または access token)
   * @param postLogoutRedirectUri ログアウト完了後にリダイレクトする URI
   * @return {@code {issuerUri}/protocol/openid-connect/logout?id_token_hint=...&post_logout_redirect_uri=...}
   */
  public String buildEndSessionUrl(String idTokenHint, String postLogoutRedirectUri) {
    return UriComponentsBuilder.fromUriString(issuerUri)
        .path("/protocol/openid-connect/logout")
        .queryParam("id_token_hint", idTokenHint)
        .queryParam("post_logout_redirect_uri", postLogoutRedirectUri)
        .build()
        .toUriString();
  }
}
