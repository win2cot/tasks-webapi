package xyz.dgz48.tasks.keycloak;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;
import org.keycloak.component.ComponentModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.ModelException;
import org.keycloak.storage.UserStorageProviderFactory;

class TasksWebApiUserStorageProviderFactoryTest {

  @Test
  void getId_returnsExpectedProviderId() {
    var factory = new TasksWebApiUserStorageProviderFactory();
    assertEquals(TasksWebApiUserStorageProviderFactory.PROVIDER_ID, factory.getId());
  }

  @Test
  void serviceLoader_registersProvider() {
    // Keycloak は User Federation を UserStorageProviderFactory の SPI サービス名で discover する。
    // ここで解決できないと realm import 時に "No such provider" となり SPI が一切ロードされない。
    var loader =
        ServiceLoader.load(
            UserStorageProviderFactory.class,
            TasksWebApiUserStorageProviderFactoryTest.class.getClassLoader());
    boolean found =
        loader.stream().anyMatch(p -> TasksWebApiUserStorageProviderFactory.class.equals(p.type()));
    assertTrue(
        found, "TasksWebApiUserStorageProviderFactory must be registered in META-INF/services");
  }

  @Test
  void create_buildsProvider_fromConfig() {
    var factory = new TasksWebApiUserStorageProviderFactory();
    KeycloakSession session = mock(KeycloakSession.class);
    ComponentModel model = mock(ComponentModel.class);
    when(model.get(TasksWebApiUserStorageProviderFactory.CONFIG_JDBC_URL))
        .thenReturn("jdbc:mysql://localhost:3306/tasks");
    when(model.get(TasksWebApiUserStorageProviderFactory.CONFIG_DB_USERNAME)).thenReturn("tasks");
    when(model.get(TasksWebApiUserStorageProviderFactory.CONFIG_DB_PASSWORD)).thenReturn("tasks");
    // JDBC 接続は遅延生成のため、create 自体は DB へ接続しない。
    var provider = factory.create(session, model);
    assertNotNull(provider);
    provider.close();
  }

  @Test
  void create_throws_whenRequiredConfigMissing() {
    var factory = new TasksWebApiUserStorageProviderFactory();
    KeycloakSession session = mock(KeycloakSession.class);
    ComponentModel model = mock(ComponentModel.class);
    when(model.get(TasksWebApiUserStorageProviderFactory.CONFIG_JDBC_URL)).thenReturn("  ");
    // config も env も未設定 → 例外(env は常に null を返すダミー)。
    assertThrows(ModelException.class, () -> factory.create(session, model, envKey -> null));
  }

  @Test
  void create_buildsProvider_fromEnvFallback_whenConfigBlank() {
    var factory = new TasksWebApiUserStorageProviderFactory();
    KeycloakSession session = mock(KeycloakSession.class);
    ComponentModel model = mock(ComponentModel.class);
    // コンポーネント config は空(未設定)。realm-export で provider を有効化しただけの状態を模す。
    when(model.get(TasksWebApiUserStorageProviderFactory.CONFIG_JDBC_URL)).thenReturn("");
    when(model.get(TasksWebApiUserStorageProviderFactory.CONFIG_DB_USERNAME)).thenReturn(null);
    when(model.get(TasksWebApiUserStorageProviderFactory.CONFIG_DB_PASSWORD)).thenReturn("  ");
    // 接続情報は環境変数から解決される(env キー名の typo 回帰も検知する)。
    var env =
        Map.of(
            TasksWebApiUserStorageProviderFactory.ENV_JDBC_URL,
            "jdbc:mysql://mysql:3306/tasks",
            TasksWebApiUserStorageProviderFactory.ENV_DB_USERNAME,
            "tasks_webapi",
            TasksWebApiUserStorageProviderFactory.ENV_DB_PASSWORD,
            "tasks_webapi");
    // JDBC 接続は遅延生成のため create 自体は DB へ接続しない。
    var provider = factory.create(session, model, env::get);
    assertNotNull(provider);
    provider.close();
  }

  @Test
  void getConfigProperties_declaresJdbcAndCredentialProps() {
    var props = new TasksWebApiUserStorageProviderFactory().getConfigProperties();
    assertEquals(3, props.size());
  }

  @Test
  void getHelpText_andLifecycleHooks_areSafe() {
    var factory = new TasksWebApiUserStorageProviderFactory();
    assertTrue(factory.getHelpText().startsWith(TasksWebApiUserStorageProviderFactory.PROVIDER_ID));
    // ライフサイクル hook は no-op(例外を投げない)。
    factory.init(null);
    factory.postInit(null);
    factory.close();
  }
}
