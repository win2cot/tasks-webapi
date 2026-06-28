package xyz.dgz48.tasks.webapi.user.adapter.external;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import xyz.dgz48.tasks.webapi.user.infra.KeycloakAdminProperties;
import xyz.dgz48.tasks.webapi.user.usecase.CredentialProvisioningException;
import xyz.dgz48.tasks.webapi.user.usecase.CredentialProvisioningPort;

/**
 * Keycloak Admin REST API による credential プロビジョニング実装(ADR-0040 §3.1)。{@code
 * keycloak.admin.enabled=true} で有効。
 *
 * <p>service-account クライアントの client_credentials grant で admin トークンを取得し、email でユーザーを引いて ①
 * パスワード設定(reset-password) → ② {@code emailVerified=true} を設定する(ADR-0040 §3.4)。credential 自体は
 * Keycloak が SoT(ADR-0006 §3.3)。Spring {@link RestClient} を用い、native image(ADR-0008)で重い
 * JAX-RS/RESTEasy 依存(keycloak-admin-client)を避ける。
 */
public class KeycloakAdminCredentialAdapter implements CredentialProvisioningPort {

  private final RestClient restClient;
  private final KeycloakAdminProperties props;

  public KeycloakAdminCredentialAdapter(RestClient restClient, KeycloakAdminProperties props) {
    this.restClient = restClient;
    this.props = props;
  }

  @Override
  public void provisionCredential(String email, String rawPassword) {
    try {
      String token = fetchAdminToken();
      String userId = findUserIdByEmail(email, token);
      resetPassword(userId, rawPassword, token);
      markEmailVerified(userId, token);
    } catch (RestClientException e) {
      // PII / パスワードは含めない(規約 §7)
      throw new CredentialProvisioningException("Keycloak Admin API 呼び出しに失敗しました", e);
    }
  }

  private String fetchAdminToken() {
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("grant_type", "client_credentials");
    form.add("client_id", props.clientId());
    form.add("client_secret", props.clientSecret());
    TokenResponse resp =
        restClient
            .post()
            .uri("/realms/{realm}/protocol/openid-connect/token", props.realm())
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(form)
            .retrieve()
            .body(TokenResponse.class);
    if (resp == null || resp.accessToken() == null || resp.accessToken().isBlank()) {
      throw new CredentialProvisioningException("Keycloak admin トークンの取得に失敗しました");
    }
    return resp.accessToken();
  }

  private String findUserIdByEmail(String email, String token) {
    KeycloakUserRef[] users =
        restClient
            .get()
            .uri(
                builder ->
                    builder
                        .path("/admin/realms/{realm}/users")
                        .queryParam("email", email)
                        .queryParam("exact", true)
                        .build(props.realm()))
            .header(HttpHeaders.AUTHORIZATION, bearer(token))
            .retrieve()
            .body(KeycloakUserRef[].class);
    if (users == null || users.length == 0 || users[0].id() == null) {
      throw new CredentialProvisioningException("Keycloak に対象ユーザーが見つかりません");
    }
    return users[0].id();
  }

  private void resetPassword(String userId, String rawPassword, String token) {
    restClient
        .put()
        .uri("/admin/realms/{realm}/users/{id}/reset-password", props.realm(), userId)
        .header(HttpHeaders.AUTHORIZATION, bearer(token))
        .contentType(MediaType.APPLICATION_JSON)
        .body(new PasswordCredential("password", rawPassword, false))
        .retrieve()
        .toBodilessEntity();
  }

  private void markEmailVerified(String userId, String token) {
    restClient
        .put()
        .uri("/admin/realms/{realm}/users/{id}", props.realm(), userId)
        .header(HttpHeaders.AUTHORIZATION, bearer(token))
        .contentType(MediaType.APPLICATION_JSON)
        .body(new EmailVerifiedUpdate(true))
        .retrieve()
        .toBodilessEntity();
  }

  private static String bearer(String token) {
    return "Bearer " + token;
  }

  /** client_credentials トークン応答(必要なフィールドのみ)。 */
  public record TokenResponse(@JsonProperty("access_token") @Nullable String accessToken) {}

  /** Admin API のユーザー検索結果(id のみ参照)。 */
  public record KeycloakUserRef(@Nullable String id) {}

  /** reset-password の credential 表現。 */
  public record PasswordCredential(String type, String value, boolean temporary) {}

  /** ユーザー更新(emailVerified のみ)。 */
  public record EmailVerifiedUpdate(boolean emailVerified) {}

  /**
   * Keycloak Admin API の JSON DTO を native image で reflection 可能にする hints(ADR-0008)。RestClient +
   * Jackson の record シリアライズ/デシリアライズは Spring Boot AOT に自動登録されないため明示する({@code @JsonProperty} の
   * access_token マッピングを含む)。JVM CI では非検出のため登録自体を {@code KeycloakAdminRuntimeHintsTest} で固定。
   */
  public static class Hints implements RuntimeHintsRegistrar {
    @Override
    public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
      for (Class<?> dto :
          new Class<?>[] {
            TokenResponse.class,
            KeycloakUserRef.class,
            PasswordCredential.class,
            EmailVerifiedUpdate.class
          }) {
        hints.reflection().registerType(dto, MemberCategory.values());
      }
    }
  }
}
