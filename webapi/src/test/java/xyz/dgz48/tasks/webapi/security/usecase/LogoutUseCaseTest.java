package xyz.dgz48.tasks.webapi.security.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LogoutUseCaseTest {

  private static final String ISSUER_URI = "http://keycloak:8080/realms/tasks";

  private final LogoutUseCase useCase = new LogoutUseCase(ISSUER_URI);

  @Test
  void buildEndSessionUrl_containsIssuerAndLogoutPath() {
    String url = useCase.buildEndSessionUrl("dummy-token", "https://app.example.com/");
    assertThat(url).startsWith(ISSUER_URI + "/protocol/openid-connect/logout");
  }

  @Test
  void buildEndSessionUrl_containsIdTokenHint() {
    String idToken = "eyJhbGciOiJSUzI1NiJ9.test";
    String url = useCase.buildEndSessionUrl(idToken, "https://app.example.com/");
    assertThat(url).contains("id_token_hint=" + idToken);
  }

  @Test
  void buildEndSessionUrl_containsPostLogoutRedirectUri() {
    String postLogoutRedirectUri = "https://app.example.com/login";
    String url = useCase.buildEndSessionUrl("dummy-token", postLogoutRedirectUri);
    assertThat(url).contains("post_logout_redirect_uri=");
  }

  @Test
  void buildEndSessionUrl_matchesExpectedFormat() {
    String url = useCase.buildEndSessionUrl("token-hint", "https://app.example.com/");
    assertThat(url)
        .matches(
            "http://keycloak:8080/realms/tasks/protocol/openid-connect/logout"
                + "\\?id_token_hint=.+&post_logout_redirect_uri=.+");
  }
}
