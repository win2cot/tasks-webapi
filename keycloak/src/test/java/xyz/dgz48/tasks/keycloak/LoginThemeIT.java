package xyz.dgz48.tasks.keycloak;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * ログイン画面テーマ {@code tasks-login}(ADR-0040 / #832)が realm の {@code loginTheme} 経由で適用され、セルフサインアップ導線用の
 * スクリプト({@code signup-link.js})がログインページに注入されることを実 Keycloak(Testcontainers)で検証する。
 *
 * <p>テーマ未適用 / theme.properties の {@code scripts} 設定ミス / {@code loginTheme} 名の不一致は、いずれもログインページのレンダリングが
 * 静かに壊れる(JVM 単体テストでは非検出)ため、ここで CI 固定する。スクリプトの DOM 注入挙動はブラウザ実行を要するため本テストの対象外で、テーマ配線(=スクリプト
 * タグが出力されること)のみを裏取りする。
 */
class LoginThemeIT extends AbstractSpiContainerTest {

  @Test
  void loginPageIncludesSignupLinkScript() throws Exception {
    String redirectUri = URLEncoder.encode("http://localhost/cb", StandardCharsets.UTF_8);
    String authUrl =
        KEYCLOAK.getAuthServerUrl()
            + "/realms/"
            + REALM
            + "/protocol/openid-connect/auth"
            + "?client_id="
            + CLI_CLIENT
            + "&redirect_uri="
            + redirectUri
            + "&response_type=code&scope=openid&state=test";

    HttpClient client = HttpClient.newHttpClient();
    HttpResponse<String> response =
        client.send(
            HttpRequest.newBuilder(URI.create(authUrl)).GET().build(),
            HttpResponse.BodyHandlers.ofString());

    assertThat(response.statusCode()).isEqualTo(200);
    // theme.properties の scripts=js/signup-link.js が template に出力される = loginTheme が適用された証跡。
    assertThat(response.body()).contains("signup-link.js");
  }
}
