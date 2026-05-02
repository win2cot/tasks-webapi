package xyz.dgz48.tasks.webapi.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.Jwt;
import xyz.dgz48.tasks.webapi.user.User;
import xyz.dgz48.tasks.webapi.user.UserRepository;

class TasksJwtAuthenticationConverterTest {

  private final UserRepository userRepository = mock(UserRepository.class);
  private final TasksJwtAuthenticationConverter converter =
      new TasksJwtAuthenticationConverter(userRepository);

  @Test
  void convertsJwtToTasksAuthenticationToken() {
    var user = mock(User.class);
    when(user.getId()).thenReturn(1L);
    when(user.getOidcSub()).thenReturn("sub-001");
    when(user.getEmail()).thenReturn("user@example.com");
    when(user.getFullName()).thenReturn("山田 太郎");
    when(user.getFullNameKana()).thenReturn("ヤマダ タロウ");
    when(user.getDepartmentName()).thenReturn("開発部");
    when(userRepository.findByOidcSub("sub-001")).thenReturn(Optional.of(user));

    var jwt =
        Jwt.withTokenValue("token")
            .header("alg", "RS256")
            .subject("sub-001")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build();

    var auth = converter.convert(jwt);

    assertThat(auth).isInstanceOf(TasksAuthenticationToken.class);
    var principal = ((TasksAuthenticationToken) auth).getPrincipal();
    assertThat(principal.getName()).isEqualTo("sub-001");
    assertThat(principal.getEmail()).isEqualTo("user@example.com");
    assertThat(principal.getFullName()).isEqualTo("山田 太郎");
    assertThat(principal.getDepartmentName()).isEqualTo("開発部");
  }

  @Test
  void throwsWhenUserNotFound() {
    when(userRepository.findByOidcSub("unknown-sub")).thenReturn(Optional.empty());

    var jwt =
        Jwt.withTokenValue("token")
            .header("alg", "RS256")
            .subject("unknown-sub")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build();

    assertThatThrownBy(() -> converter.convert(jwt)).isInstanceOf(BadCredentialsException.class);
  }
}
