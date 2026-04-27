package xyz.dgz48.tasks.webapi;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModularityTests {

  static final ApplicationModules modules = ApplicationModules.of(TasksWebapiApplication.class);

  @Test
  void verifyModularity() {
    modules.verify();
  }
}
