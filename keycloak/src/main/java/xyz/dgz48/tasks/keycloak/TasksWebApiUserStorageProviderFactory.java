package xyz.dgz48.tasks.keycloak;

import java.util.List;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.keycloak.Config;
import org.keycloak.component.ComponentModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.ModelException;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.provider.ProviderConfigurationBuilder;
import org.keycloak.storage.UserStorageProviderFactory;

/**
 * {@link TasksWebApiUserStorageProvider} を Keycloak の User Federation コンポーネントとして登録する。
 *
 * <p>{@code META-INF/services/org.keycloak.storage.UserStorageProviderFactory} 経由で登録される(Keycloak は
 * User Storage の provider をこの SPI サービス名で discover する。{@code ComponentFactory} 直接登録では User
 * Federation として解決されない)。tasks-webapi の {@code users} データベースへの JDBC 接続は、下記の config
 * プロパティでコンポーネントごとに与える(realm で federation provider を作成する際に設定する。 realm 側の配線は別 sub-issue)。
 */
public class TasksWebApiUserStorageProviderFactory
    implements UserStorageProviderFactory<TasksWebApiUserStorageProvider> {

  public static final String PROVIDER_ID = "tasks-webapi-user-storage";

  static final String CONFIG_JDBC_URL = "jdbcUrl";
  static final String CONFIG_DB_USERNAME = "dbUsername";
  static final String CONFIG_DB_PASSWORD = "dbPassword";

  // コンポーネント config が空の場合のフォールバック環境変数。realm-export ではコンポーネントで provider を
  // 有効化するだけとし、接続情報は環境ごと(compose / dev / stg / prd)に下記の環境変数で与える。realm JSON は
  // import 時に ${ENV} 置換されない(UserProfile の ${username} 等テーマキーと衝突するため有効化できない)ので、
  // 環境差のある接続情報はここで解決する。IT は config を明示設定するため従来通り動作する。ADR-0006 / #862。
  static final String ENV_JDBC_URL = "SPI_DB_JDBC_URL";
  static final String ENV_DB_USERNAME = "SPI_DB_USERNAME";
  static final String ENV_DB_PASSWORD = "SPI_DB_PASSWORD";

  // Admin Console は getHelpText / 各 config プロパティの label・helpText を i18n メッセージキーとして解決する。
  // 対応する翻訳はカスタム admin テーマ tasks-admin の message bundle(messages_ja/en.properties、#729)で定義する。
  static final String MSG_PREFIX = PROVIDER_ID + ".";

  @Override
  public TasksWebApiUserStorageProvider create(KeycloakSession session, ComponentModel model) {
    return create(session, model, System::getenv);
  }

  /** env 解決を注入可能にした {@link #create} 本体(env フォールバックのテスト用に package-private)。 */
  TasksWebApiUserStorageProvider create(
      KeycloakSession session, ComponentModel model, Function<String, @Nullable String> env) {
    UserRepository repository =
        new UserRepository(
            resolveConfig(model, CONFIG_JDBC_URL, ENV_JDBC_URL, env),
            resolveConfig(model, CONFIG_DB_USERNAME, ENV_DB_USERNAME, env),
            resolveConfig(model, CONFIG_DB_PASSWORD, ENV_DB_PASSWORD, env));
    return new TasksWebApiUserStorageProvider(session, model, repository);
  }

  /** コンポーネント config を優先し、未設定(null/空)なら env へフォールバックして解決する。両方とも未設定なら {@link ModelException} を投げる。 */
  private static String resolveConfig(
      ComponentModel model,
      String configKey,
      String envKey,
      Function<String, @Nullable String> env) {
    String value = model.get(configKey);
    if (value == null || value.isBlank()) {
      value = env.apply(envKey);
    }
    if (value == null || value.isBlank()) {
      throw new ModelException(
          "tasks-webapi user store: config '" + configKey + "' (or env " + envKey + ") is not set");
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
