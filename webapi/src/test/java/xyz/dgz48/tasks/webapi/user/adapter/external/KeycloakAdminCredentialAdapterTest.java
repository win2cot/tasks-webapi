package xyz.dgz48.tasks.webapi.user.adapter.external;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
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
  void throwsWhenUserNotFound() {
    server
        .expect(requestTo(BASE + "/realms/tasks/protocol/openid-connect/token"))
        .andRespond(withSuccess("{\"access_token\":\"tok-123\"}", MediaType.APPLICATION_JSON));
    server
        .expect(requestTo(Matchers.startsWith(BASE + "/admin/realms/tasks/users?")))
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
