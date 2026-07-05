package xyz.dgz48.tasks.webapi.user.adapter.external;

import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;

class LoggingCredentialProvisioningAdapterTest {

  private final LoggingCredentialProvisioningAdapter adapter =
      new LoggingCredentialProvisioningAdapter();

  @Test
  void provisionCredentialDoesNotThrow() {
    assertThatCode(() -> adapter.provisionCredential("user@example.com", "表示 名", "raw-password"))
        .doesNotThrowAnyException();
  }
}
