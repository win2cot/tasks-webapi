package xyz.dgz48.tasks.webapi.user.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import xyz.dgz48.tasks.webapi.shared.exception.DomainException;

class CredentialProvisioningExceptionTest {

  @Test
  void isDomainException() {
    assertThat(new CredentialProvisioningException("msg")).isInstanceOf(DomainException.class);
  }

  @Test
  void preservesCauseWhenWrapping() {
    var cause = new RuntimeException("keycloak http 500");
    var ex = new CredentialProvisioningException("credential 設定に失敗しました", cause);

    assertThat(ex.getMessage()).isEqualTo("credential 設定に失敗しました");
    assertThat(ex.getCause()).isSameAs(cause);
  }
}
