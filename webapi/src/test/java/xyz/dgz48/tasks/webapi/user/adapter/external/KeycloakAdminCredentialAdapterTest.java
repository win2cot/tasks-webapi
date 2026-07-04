package xyz.dgz48.tasks.webapi.user.adapter.external;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withCreatedEntity;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URI;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import xyz.dgz48.tasks.webapi.user.infra.KeycloakAdminProperties;
import xyz.dgz48.tasks.webapi.user.usecase.CredentialProvisioningException;

/**
 * {@link KeycloakAdminCredentialAdapter} の contract テスト(MockRestServiceServer)。Keycloak Admin REST
 * API への HTTP 呼び出し列(トークン取得 → ユーザー検索 → reset-password → emailVerified 更新)を検証する。ADR-0040 §6 に従い、実
 * Keycloak を用いる full-IT の代替として contract で固定し、実機疎通は dev native で確認する。
 */
class KeycloakAdminCredentialAdapterTest {

  private static final String BASE = "http://kc.local";

  private MockRestServiceServer server;
  private KeycloakAdminCredentialAdapter adapter;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
    server = MockRestServiceServer.bindTo(builder).build();
    var props = new KeycloakAdminProperties(true, BASE, "tasks", "tasks-webapi-admin", "secret");
    adapter = new KeycloakAdminCredentialAdapter(builder.build(), props);
  }

  @Test
  void provisionsPasswordThenMarksEmailVerifiedInOrder() {
    server
        .expect(requestTo(BASE + "/realms/tasks/protocol/openid-connect/token"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withSuccess("{\"access_token\":\"tok-123\"}", MediaType.APPLICATION_JSON));
    server
        .expect(requestTo(Matchers.startsWith(BASE + "/admin/realms/tasks/users?")))
        .andExpect(method(HttpMethod.GET))
        .andExpect(queryParam("email", "a@example.com"))
        .andExpect(queryParam("exact", "true"))
        .andExpect(header("Authorization", "Bearer tok-123"))
        .andRespond(withSuccess("[{\"id\":\"42\"}]", MediaType.APPLICATION_JSON));
    server
        .expect(requestTo(BASE + "/admin/realms/tasks/users/42/reset-password"))
        .andExpect(method(HttpMethod.PUT))
        .andExpect(header("Authorization", "Bearer tok-123"))
        .andRespond(withSuccess());
    server
        .expect(requestTo(BASE + "/admin/realms/tasks/users/42"))
        .andExpect(method(HttpMethod.PUT))
        .andExpect(header("Authorization", "Bearer tok-123"))
        .andRespond(withSuccess());

    adapter.provisionCredential("a@example.com", "raw-password");

    server.verify();
  }

  @Test
  void createsUserWhenNotFoundThenProvisions() {
    server
        .expect(requestTo(BASE + "/realms/tasks/protocol/openid-connect/token"))
        .andRespond(withSuccess("{\"access_token\":\"tok-123\"}", MediaType.APPLICATION_JSON));
    // ① email 検索: ヒットなし → 新規作成へ
    server
        .expect(requestTo(Matchers.startsWith(BASE + "/admin/realms/tasks/users?")))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
    // ② 作成: username=email, enabled=true。201 + Location ヘッダで id を返す
    server
        .expect(requestTo(BASE + "/admin/realms/tasks/users"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("Authorization", "Bearer tok-123"))
        .andExpect(jsonPath("$.username").value("new@example.com"))
        .andExpect(jsonPath("$.email").value("new@example.com"))
        .andExpect(jsonPath("$.enabled").value(true))
        .andRespond(withCreatedEntity(URI.create(BASE + "/admin/realms/tasks/users/99")));
    // ③ reset-password → ④ emailVerified を新規 id に対して実施
    server
        .expect(requestTo(BASE + "/admin/realms/tasks/users/99/reset-password"))
        .andExpect(method(HttpMethod.PUT))
        .andRespond(withSuccess());
    server
        .expect(requestTo(BASE + "/admin/realms/tasks/users/99"))
        .andExpect(method(HttpMethod.PUT))
        .andRespond(withSuccess());

    adapter.provisionCredential("new@example.com", "raw-password");

    server.verify();
  }

  @Test
  void recoversWhenCreateConflicts409() {
    server
        .expect(requestTo(BASE + "/realms/tasks/protocol/openid-connect/token"))
        .andRespond(withSuccess("{\"access_token\":\"tok-123\"}", MediaType.APPLICATION_JSON));
    // ① 初回検索: ヒットなし(競合相手がまだ作成していない)→ 作成へ
    server
        .expect(requestTo(Matchers.startsWith(BASE + "/admin/realms/tasks/users?")))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
    // ② 作成: 並行 complete が先に作成済 → 409(Location 無し)。例外化せず再検索へ
    server
        .expect(requestTo(BASE + "/admin/realms/tasks/users"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withStatus(HttpStatus.CONFLICT));
    // ③ 再検索: 相手が作成したユーザーがヒット → その id で provisioning 継続
    server
        .expect(requestTo(Matchers.startsWith(BASE + "/admin/realms/tasks/users?")))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withSuccess("[{\"id\":\"77\"}]", MediaType.APPLICATION_JSON));
    server
        .expect(requestTo(BASE + "/admin/realms/tasks/users/77/reset-password"))
        .andExpect(method(HttpMethod.PUT))
        .andRespond(withSuccess());
    server
        .expect(requestTo(BASE + "/admin/realms/tasks/users/77"))
        .andExpect(method(HttpMethod.PUT))
        .andRespond(withSuccess());

    adapter.provisionCredential("race@example.com", "raw-password");

    server.verify();
  }

  @Test
  void throwsWhenUserCreationYieldsNoId() {
    server
        .expect(requestTo(BASE + "/realms/tasks/protocol/openid-connect/token"))
        .andRespond(withSuccess("{\"access_token\":\"tok-123\"}", MediaType.APPLICATION_JSON));
    server
        .expect(requestTo(Matchers.startsWith(BASE + "/admin/realms/tasks/users?")))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
    // 作成が Location を返さず(異常)、再検索も空 → プロビジョニング失敗
    server
        .expect(requestTo(BASE + "/admin/realms/tasks/users"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withSuccess());
    server
        .expect(requestTo(Matchers.startsWith(BASE + "/admin/realms/tasks/users?")))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> adapter.provisionCredential("missing@example.com", "pw"))
        .isInstanceOf(CredentialProvisioningException.class);
  }

  @Test
  void throwsWhenTokenEndpointReturnsNoAccessToken() {
    server
        .expect(requestTo(BASE + "/realms/tasks/protocol/openid-connect/token"))
        .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> adapter.provisionCredential("a@example.com", "pw"))
        .isInstanceOf(CredentialProvisioningException.class);
  }
}
