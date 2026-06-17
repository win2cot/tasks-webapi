package xyz.dgz48.tasks.webapi.shared.infra;

import org.jspecify.annotations.Nullable;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

@Configuration(proxyBeanMethods = false)
@ImportRuntimeHints(NativeImageHintsConfig.Registrar.class)
class NativeImageHintsConfig {

  static class Registrar implements RuntimeHintsRegistrar {

    @Override
    public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
      // JBoss Logging annotation-generated impls; not reached by Spring Boot AOT hints
      for (String cls :
          new String[] {
            "org.hibernate.validator.internal.util.logging.Log_$logger",
            "org.hibernate.validator.internal.util.logging.Messages_$bundle",
          }) {
        try {
          hints.reflection().registerType(Class.forName(cls), MemberCategory.values());
        } catch (ClassNotFoundException ignored) {
        }
      }
      // Flyway classpath scanner cannot enumerate directories under the native-image resource:
      // protocol
      hints.resources().registerPattern("db/migration/*.sql");
    }
  }
}
