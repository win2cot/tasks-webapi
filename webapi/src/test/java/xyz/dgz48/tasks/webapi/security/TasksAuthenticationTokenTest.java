package xyz.dgz48.tasks.webapi.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class TasksAuthenticationTokenTest {

  private static final TasksPrincipal PRINCIPAL =
      new TasksPrincipal(1L, "sub123", "user@example.com", "山田太郎", "ヤマダタロウ", null);

  @Test
  void isAuthenticatedAfterConstruction() {
    TasksAuthenticationToken token = new TasksAuthenticationToken(PRINCIPAL, List.of());

    assertThat(token.isAuthenticated()).isTrue();
  }

  @Test
  void getPrincipalReturnsPrincipal() {
    TasksAuthenticationToken token = new TasksAuthenticationToken(PRINCIPAL, List.of());

    assertThat(token.getPrincipal()).isSameAs(PRINCIPAL);
  }

  @Test
  void getCredentialsReturnsNull() {
    TasksAuthenticationToken token = new TasksAuthenticationToken(PRINCIPAL, List.of());

    assertThat(token.getCredentials()).isNull();
  }
}
