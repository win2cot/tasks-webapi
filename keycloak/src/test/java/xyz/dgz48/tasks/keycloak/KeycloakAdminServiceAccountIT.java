package xyz.dgz48.tasks.keycloak;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;

/**
 * service-account クライアント {@code tasks-webapi-admin}(ADR-0040 §3.1、会員登録の credential プロビジョニング用)が、
 * {@code client_credentials} grant で {@code realm-management} の {@code manage-users}/{@code
 * view-users} を行使できることを実 Keycloak(Testcontainers)で検証する。
 *
 * <p>realm JSON の service-account ロール割当表現({@code serviceAccountClientId} + {@code clientRoles})が
 * import で実際に適用されることの裏取り。誤設定は実行時 403 の silent-fail(JVM 単体テストでは非検出)になるため、ここで CI 固定する。webapi 側の HTTP
 * 呼び出し列は {@code KeycloakAdminCredentialAdapterTest}(MockRestServiceServer)で別途 contract 固定している。
 */
class KeycloakAdminServiceAccountIT extends AbstractSpiContainerTest {

  @Test
  void serviceAccountClientCanAccessAdminUsersApi() {
    try (Keycloak kc = serviceAccountClient()) {
      // role 未割当なら 403(ForbiddenException)で失敗する。値そのものは問わない。
      Integer count = kc.realm(REALM).users().count();
      assertThat(count).isNotNull();
    }
  }

  private static Keycloak serviceAccountClient() {
    return KeycloakBuilder.builder()
        .serverUrl(KEYCLOAK.getAuthServerUrl())
        .realm(REALM)
        .clientId("tasks-webapi-admin")
        .clientSecret("test-admin-secret")
        .grantType("client_credentials")
        .build();
  }
}
