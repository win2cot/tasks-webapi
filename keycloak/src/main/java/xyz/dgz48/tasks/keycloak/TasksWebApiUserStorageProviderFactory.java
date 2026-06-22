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

  // TODO(#729): getHelpText / 下記 getConfigProperties の label・helpText は Admin Console 上で i18n キーとして
  // 扱われ、対応する翻訳が無ければリテラル(英語)がそのまま全ロケールで表示される。ロケール連動させるにはカスタム admin テーマの
  // message bundle(messages_ja/en.properties)へキーと翻訳を登録する。当面は英語リテラルのまま運用し、テーマ/realm 整備
  // (#729)で message キー化と翻訳をまとめて行う。
  @Override
  public String getHelpText() {
    return "Federates tasks-webapi users table (read-only profile, email writable via"
        + " Update-Email)";
  }

  @Override
  public List<ProviderConfigProperty> getConfigProperties() {
    return ProviderConfigurationBuilder.create()
        .property()
        .name(CONFIG_JDBC_URL)
        .label("JDBC URL")
        .helpText("JDBC URL of the tasks-webapi database (e.g. jdbc:mysql://host:3306/tasks)")
        .type(ProviderConfigProperty.STRING_TYPE)
        .add()
        .property()
        .name(CONFIG_DB_USERNAME)
        .label("DB username")
        .helpText("Database username for the tasks-webapi users table")
        .type(ProviderConfigProperty.STRING_TYPE)
        .add()
        .property()
        .name(CONFIG_DB_PASSWORD)
        .label("DB password")
        .helpText("Database password for the tasks-webapi users table")
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
