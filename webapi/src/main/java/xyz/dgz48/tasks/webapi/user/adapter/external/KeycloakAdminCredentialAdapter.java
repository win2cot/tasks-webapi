package xyz.dgz48.tasks.webapi.user.adapter.external;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.net.URI;
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
 *
 * <p>email 検索がヒットしない場合はローカル Keycloak ユーザーを新規作成する(ADR-0040 §3.1「必要なら addUser correlation」)。User
 * Storage SPI federation(ADR-0006)が有効な環境では検索が app {@code users} を federated user
 * として返すため作成は行われない。federation 未活性の環境では作成したローカルユーザーが、初回ログイン時に app {@code users} の {@code pending} 行と
 * correlation される(ADR-0040 §3.2 / {@code OidcSubCorrelationService})。いずれの構成でも provisioning が成立するよう
 * find-or-create とする。
 */
public class KeycloakAdminCredentialAdapter implements CredentialProvisioningPort {

  private final RestClient restClient;
  private final KeycloakAdminProperties props;

  public KeycloakAdminCredentialAdapter(RestClient restClient, KeycloakAdminProperties props) {
    this.restClient = restClient;
    this.props = props;
  }

  @Override
  public void provisionCredential(String email, String displayName, String rawPassword) {
    try {
      String token = fetchAdminToken();
      String userId = resolveOrCreateUserId(email, displayName, token);
      resetPassword(userId, rawPassword, token);
      markEmailVerified(userId, token);
    } catch (RestClientException e) {
      // PII / パスワードは含めない(規約 §7)
      throw new CredentialProvisioningException("Keycloak Admin API 呼び出しに失敗しました", e);
    }
  }

  /**
   * email でユーザーを解決する。既存(federated 含む)ならその id を返し、無ければローカルユーザーを新規作成して id を返す。SPI federation
   * 有効時は検索がヒットするため作成は行わない。
   */
  private String resolveOrCreateUserId(String email, String displayName, String token) {
    String existing = findUserIdByEmail(email, token);
    return existing != null ? existing : createUser(email, displayName, token);
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

  private @Nullable String findUserIdByEmail(String email, String token) {
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
      return null;
    }
    return users[0].id();
  }

  /**
   * ローカル Keycloak ユーザーを作成し id を返す({@code username=email}、{@code enabled=true}、{@code emailVerified}
   * は後続の {@link #markEmailVerified} で設定)。作成レスポンスの {@code Location} ヘッダから id を取り出す。並行 complete による
   * {@code 409}(既存)は無視し、email 再検索でフォールバックする。
   *
   * <p>{@code firstName} / {@code lastName} は realm の user profile 必須項目であり、未設定だと初回ログインで 「Update
   * Account Information」に遮られ dashboard へ到達できない。app は表示名を単一の {@code full_name} で保持するため、両フィールドに
   * {@code displayName} を設定して必須制約を満たす(profile SoT は tasks-webapi の {@code users.full_name}。SPI
   * federation 経路では UserAdapter が {@code firstName=full_name} を返す)。
   */
  private String createUser(String email, String displayName, String token) {
    URI location =
        restClient
            .post()
            .uri("/admin/realms/{realm}/users", props.realm())
            .header(HttpHeaders.AUTHORIZATION, bearer(token))
            .contentType(MediaType.APPLICATION_JSON)
            .body(new UserCreateRequest(email, email, displayName, displayName, true))
            .retrieve()
            // 競合(既に存在)は例外にせず、下の再検索でフォールバックする
            .onStatus(status -> status.value() == 409, (req, res) -> {})
            .toBodilessEntity()
            .getHeaders()
            .getLocation();
    if (location != null) {
      String userId = extractUserId(location);
      if (userId != null && !userId.isBlank()) {
        return userId;
      }
    }
    String refetched = findUserIdByEmail(email, token);
    if (refetched == null) {
      throw new CredentialProvisioningException("Keycloak ユーザーの作成に失敗しました");
    }
    return refetched;
  }

  /** {@code .../users/{id}} 形式の Location パスから末尾の user id を取り出す。 */
  private static @Nullable String extractUserId(URI location) {
    String path = location.getPath();
    int idx = path.lastIndexOf('/');
    return idx >= 0 && idx < path.length() - 1 ? path.substring(idx + 1) : null;
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

  /**
   * ユーザー新規作成リクエスト(username=email, enabled=true)。firstName / lastName は realm profile 必須項目を 満たすため
   * displayName を設定。emailVerified は後続で設定。
   */
  public record UserCreateRequest(
      String username, String email, String firstName, String lastName, boolean enabled) {}

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
            UserCreateRequest.class,
            EmailVerifiedUpdate.class
          }) {
        hints.reflection().registerType(dto, MemberCategory.values());
      }
    }
  }
}
