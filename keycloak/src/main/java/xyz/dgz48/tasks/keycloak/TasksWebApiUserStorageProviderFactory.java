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
 * Registers {@link TasksWebApiUserStorageProvider} as a Keycloak User Federation component.
 *
 * <p>Registered via {@code META-INF/services/org.keycloak.component.ComponentFactory}. The JDBC
 * connection to the tasks-webapi {@code users} database is supplied per-component through the
 * config properties below (set when the federation provider is created in the realm — realm-side
 * wiring is a separate sub-issue).
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
