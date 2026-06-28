package xyz.dgz48.tasks.webapi.user.infra;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import xyz.dgz48.tasks.webapi.user.adapter.external.KeycloakAdminCredentialAdapter;
import xyz.dgz48.tasks.webapi.user.adapter.external.LoggingCredentialProvisioningAdapter;
import xyz.dgz48.tasks.webapi.user.usecase.CredentialProvisioningPort;

class CredentialProvisioningConfigTest {

  private final CredentialProvisioningConfig config = new CredentialProvisioningConfig();

  @Test
  void buildsKeycloakAdapterWhenEnabled() {
    var props = new KeycloakAdminProperties(true, "http://kc.local", "tasks", "cid", "secret");

    CredentialProvisioningPort port = config.keycloakAdminCredentialProvisioningAdapter(props);

    assertThat(port).isInstanceOf(KeycloakAdminCredentialAdapter.class);
  }

  @Test
  void fallsBackToLoggingAdapter() {
    assertThat(config.loggingCredentialProvisioningAdapter())
        .isInstanceOf(LoggingCredentialProvisioningAdapter.class);
  }
}
