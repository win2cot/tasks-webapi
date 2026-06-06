package xyz.dgz48.tasks.keycloak;

import java.util.List;
import org.keycloak.Config;
import org.keycloak.component.ComponentFactory;
import org.keycloak.component.ComponentModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ProviderConfigProperty;

/**
 * Registers {@link TasksWebApiUserStorageProvider} as a Keycloak User Federation component.
 *
 * <p>Registered via {@code META-INF/services/org.keycloak.component.ComponentFactory}.
 */
public class TasksWebApiUserStorageProviderFactory
    implements ComponentFactory<TasksWebApiUserStorageProvider, TasksWebApiUserStorageProvider> {

  public static final String PROVIDER_ID = "tasks-webapi-user-storage";

  @Override
  public TasksWebApiUserStorageProvider create(KeycloakSession session, ComponentModel model) {
    return new TasksWebApiUserStorageProvider(session, model);
  }

  @Override
  public String getId() {
    return PROVIDER_ID;
  }

  @Override
  public String getHelpText() {
    return "Federates tasks-webapi users table (read-only profile, email writable via Update-Email)";
  }

  @Override
  public List<ProviderConfigProperty> getConfigProperties() {
    return List.of();
  }

  @Override
  public void init(Config.Scope config) {}

  @Override
  public void postInit(KeycloakSessionFactory factory) {}

  @Override
  public void close() {}
}
