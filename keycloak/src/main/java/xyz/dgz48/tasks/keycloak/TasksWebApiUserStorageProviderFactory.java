package xyz.dgz48.tasks.keycloak;

import java.util.List;
import org.keycloak.Config;
import org.keycloak.component.ComponentFactory;
import org.keycloak.component.ComponentModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.ModelException;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.provider.ProviderConfigurationBuilder;

/**
 * {@link TasksWebApiUserStorageProvider} を Keycloak の User Federation コンポーネントとして登録する。
 *
 * <p>{@code META-INF/services/org.keycloak.component.ComponentFactory} 経由で登録される。tasks-webapi の
 * {@code users} データベースへの JDBC 接続は、下記の config プロパティでコンポーネントごとに与える(realm で federation provider
 * を作成する際に設定する。 realm 側の配線は別 sub-issue)。
 */
public class TasksWebApiUserStorageProviderFactory
    implements ComponentFactory<TasksWebApiUserStorageProvider, TasksWebApiUserStorageProvider> {

  public static final String PROVIDER_ID = "tasks-webapi-user-storage";

  static final String CONFIG_JDBC_URL = "jdbcUrl";
  static final String CONFIG_DB_USERNAME = "dbUsername";
  static final String CONFIG_DB_PASSWORD = "dbPassword";

  // Admin Console は getHelpText / 各 config プロパティの label・helpText を i18n メッセージキーとして解決する。
  // 対応する翻訳はカスタム admin テーマ tasks-admin の message bundle(messages_ja/en.properties、#729)で定義する。
  static final String MSG_PREFIX = PROVIDER_ID + ".";

  @Override
  public TasksWebApiUserStorageProvider create(KeycloakSession session, ComponentModel model) {
    UserRepository repository =
        new UserRepository(
            requireConfig(model, CONFIG_JDBC_URL),
            requireConfig(model, CONFIG_DB_USERNAME),
            requireConfig(model, CONFIG_DB_PASSWORD));
    return new TasksWebApiUserStorageProvider(session, model, repository);
  }

  private static String requireConfig(ComponentModel model, String key) {
    String value = model.get(key);
    if (value == null || value.isBlank()) {
      throw new ModelException("tasks-webapi user store: required config '" + key + "' is not set");
    }
    return value;
  }

  @Override
  public String getId() {
    return PROVIDER_ID;
  }

  // label・helpText は tasks-admin テーマ(messages_ja/en.properties)の i18n キー。翻訳が無いロケールでは
  // キー文字列がそのまま表示されるため、キーを追加したら必ず両 properties へ翻訳を登録する(#729)。
  @Override
  public String getHelpText() {
    return MSG_PREFIX + "helpText";
  }

  @Override
  public List<ProviderConfigProperty> getConfigProperties() {
    return ProviderConfigurationBuilder.create()
        .property()
        .name(CONFIG_JDBC_URL)
        .label(MSG_PREFIX + CONFIG_JDBC_URL)
        .helpText(MSG_PREFIX + CONFIG_JDBC_URL + ".help")
        .type(ProviderConfigProperty.STRING_TYPE)
        .add()
        .property()
        .name(CONFIG_DB_USERNAME)
        .label(MSG_PREFIX + CONFIG_DB_USERNAME)
        .helpText(MSG_PREFIX + CONFIG_DB_USERNAME + ".help")
        .type(ProviderConfigProperty.STRING_TYPE)
        .add()
        .property()
        .name(CONFIG_DB_PASSWORD)
        .label(MSG_PREFIX + CONFIG_DB_PASSWORD)
        .helpText(MSG_PREFIX + CONFIG_DB_PASSWORD + ".help")
        .type(ProviderConfigProperty.PASSWORD)
        .secret(true)
        .add()
        .build();
  }

  @Override
  public void init(Config.Scope config) {}

  @Override
  public void postInit(KeycloakSessionFactory factory) {}

  @Override
  public void close() {}
}
