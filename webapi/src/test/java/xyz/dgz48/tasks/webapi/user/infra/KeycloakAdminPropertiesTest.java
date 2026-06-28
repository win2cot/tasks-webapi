package xyz.dgz48.tasks.webapi.user.infra;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class KeycloakAdminPropertiesTest {

  @Test
  void exposesAllConfiguredValues() {
    var props =
        new KeycloakAdminProperties(
            true, "https://auth.example", "tasks", "tasks-webapi-admin", "s3cr3t");

    assertThat(props.enabled()).isTrue();
    assertThat(props.serverUrl()).isEqualTo("https://auth.example");
    assertThat(props.realm()).isEqualTo("tasks");
    assertThat(props.clientId()).isEqualTo("tasks-webapi-admin");
    assertThat(props.clientSecret()).isEqualTo("s3cr3t");
  }
}
