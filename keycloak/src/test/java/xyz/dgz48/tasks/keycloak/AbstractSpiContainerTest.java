package xyz.dgz48.tasks.keycloak;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import java.io.File;
import java.util.List;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.testcontainers.utility.MountableFile;

/**
 * {@link AbstractMySqlContainerTest} の MySQL に加え、SPI を配備した Keycloak 26.6.3 を共有 singleton
 * として起動する統合テスト基盤 (ADR-0006 §6)。
 *
 * <p>Keycloak の {@code providers/} へは「Gradle が build した本 SPI の JAR」と「MySQL JDBC ドライバ」を投入し、{@code
 * tasks-test-realm.json} の User Storage コンポーネント(JDBC で同一 Docker network 上の {@code mysql} へ接続)を
 * import する。JAR のパスは Gradle test タスクがシステムプロパティ({@code tasks.spi.jar} / {@code
 * tasks.mysql.driver.jar})で渡す。
 */
abstract class AbstractSpiContainerTest extends AbstractMySqlContainerTest {

  static final String REALM = "tasks-test";
  static final String CLI_CLIENT = "tasks-cli";

  // 親クラス(AbstractMySqlContainerTest)の static 初期化で MYSQL が先に起動してから評価される。
  static final KeycloakContainer KEYCLOAK = startKeycloak();

  @SuppressWarnings("resource") // singleton: Ryuk が JVM 終了時に破棄する
  private static KeycloakContainer startKeycloak() {
    KeycloakContainer keycloak =
        new KeycloakContainer("quay.io/keycloak/keycloak:26.6.3")
            .withNetwork(NETWORK)
            .withProviderLibsFrom(List.of(spiJar(), mysqlDriverJar()))
            .withRealmImportFile("/tasks-test-realm.json")
            // tasks-test-realm の loginTheme=tasks-login(#832)を解決できるよう、テーマを焼き込み済イメージと
            // 同じ /opt/keycloak/themes/ へ投入する(本イメージは vanilla なため明示コピーが要る)。
            .withCopyFileToContainer(
                MountableFile.forHostPath("themes/tasks-login"), "/opt/keycloak/themes/tasks-login")
            .withFeaturesEnabled("update-email");
    keycloak.start();
    return keycloak;
  }

  private static File spiJar() {
    return requiredJar("tasks.spi.jar");
  }

  private static File mysqlDriverJar() {
    return requiredJar("tasks.mysql.driver.jar");
  }

  private static File requiredJar(String property) {
    String path = System.getProperty(property);
    if (path == null || path.isBlank()) {
      throw new IllegalStateException(
          "system property '" + property + "' is not set (Gradle test task が JAR パスを渡す必要がある)");
    }
    File jar = new File(path);
    if (!jar.isFile()) {
      throw new IllegalStateException(
          "JAR not found for '" + property + "': " + jar.getAbsolutePath());
    }
    return jar;
  }

  // --- Keycloak Admin REST client ---

  static Keycloak adminClient() {
    return KeycloakBuilder.builder()
        .serverUrl(KEYCLOAK.getAuthServerUrl())
        .realm("master")
        .clientId("admin-cli")
        .username(KEYCLOAK.getAdminUsername())
        .password(KEYCLOAK.getAdminPassword())
        .build();
  }

  /** 指定 user の資格(password)で {@code tasks-cli} 経由の token を取得する admin client。失効検証に使う。 */
  static Keycloak userClient(String email, String password) {
    return KeycloakBuilder.builder()
        .serverUrl(KEYCLOAK.getAuthServerUrl())
        .realm(REALM)
        .clientId(CLI_CLIENT)
        .grantType("password")
        .username(email)
        .password(password)
        .build();
  }
}
