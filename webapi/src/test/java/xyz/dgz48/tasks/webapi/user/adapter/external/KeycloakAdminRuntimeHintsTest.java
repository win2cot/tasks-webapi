package xyz.dgz48.tasks.webapi.user.adapter.external;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.aot.hint.predicate.RuntimeHintsPredicates.reflection;

import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.RuntimeHints;

/**
 * Keycloak Admin DTO の native reflection hints が登録されていることを固定する(ADR-0008)。RestClient + Jackson の
 * record シリアライズは JVM CI では reflection 不要だが native image では必要。[[native-hibernate-validator-hints]]
 * と同種の native-only 退行を防ぐ。
 */
class KeycloakAdminRuntimeHintsTest {

  @Test
  void registersReflectionHintsForKeycloakAdminDtos() {
    RuntimeHints hints = new RuntimeHints();

    new KeycloakAdminCredentialAdapter.Hints().registerHints(hints, getClass().getClassLoader());

    assertThat(reflection().onType(KeycloakAdminCredentialAdapter.TokenResponse.class))
        .accepts(hints);
    assertThat(reflection().onType(KeycloakAdminCredentialAdapter.KeycloakUserRef.class))
        .accepts(hints);
    assertThat(reflection().onType(KeycloakAdminCredentialAdapter.PasswordCredential.class))
        .accepts(hints);
    assertThat(reflection().onType(KeycloakAdminCredentialAdapter.EmailVerifiedUpdate.class))
        .accepts(hints);
  }
}
