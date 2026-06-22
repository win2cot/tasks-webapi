package xyz.dgz48.tasks.keycloak;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;
import org.keycloak.component.ComponentFactory;

class TasksWebApiUserStorageProviderFactoryTest {

  @Test
  void getId_returnsExpectedProviderId() {
    var factory = new TasksWebApiUserStorageProviderFactory();
    assertEquals(TasksWebApiUserStorageProviderFactory.PROVIDER_ID, factory.getId());
  }

  @Test
  void serviceLoader_registersProvider() {
    var loader =
        ServiceLoader.load(
            ComponentFactory.class,
            TasksWebApiUserStorageProviderFactoryTest.class.getClassLoader());
    boolean found =
        loader.stream().anyMatch(p -> TasksWebApiUserStorageProviderFactory.class.equals(p.type()));
    assertTrue(
        found, "TasksWebApiUserStorageProviderFactory must be registered in META-INF/services");
  }
}
