package xyz.dgz48.tasks.webapi.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import xyz.dgz48.tasks.webapi.user.User;
import xyz.dgz48.tasks.webapi.user.UserRepository;

@ExtendWith(MockitoExtension.class)
class TasksJwtAuthenticationConverterTest {

  @Mock UserRepository userRepository;
  @Mock Jwt jwt;

  @InjectMocks TasksJwtAuthenticationConverter converter;

  @Test
  void convertsJwtToAuthenticationToken() {
    User user = new User("sub123", "user@example.com", "山田太郎", "ヤマダタロウ", "開発部");
    when(jwt.getSubject()).thenReturn("sub123");
    when(userRepository.findByOidcSub("sub123")).thenReturn(Optional.of(user));

    AbstractAuthenticationToken token = converter.convert(jwt);

    assertThat(token).isInstanceOf(TasksAuthenticationToken.class);
    assertThat(token.isAuthenticated()).isTrue();
    TasksPrincipal principal = (TasksPrincipal) token.getPrincipal();
    assertThat(principal.getOidcSub()).isEqualTo("sub123");
    assertThat(principal.getEmail()).isEqualTo("user@example.com");
    assertThat(principal.getFullName()).isEqualTo("山田太郎");
    assertThat(principal.getDepartmentName()).isEqualTo("開発部");
  }

  @Test
  void throwsWhenUserNotFound() {
    when(jwt.getSubject()).thenReturn("unknown-sub");
    when(userRepository.findByOidcSub("unknown-sub")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> converter.convert(jwt))
        .isInstanceOf(InvalidBearerTokenException.class);
  }

  @Test
  void throwsWhenSubClaimMissing() {
    when(jwt.getSubject()).thenReturn(null);

    assertThatThrownBy(() -> converter.convert(jwt))
        .isInstanceOf(InvalidBearerTokenException.class);
  }
}
