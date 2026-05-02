package xyz.dgz48.tasks.webapi.security;

import java.util.List;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.stereotype.Component;
import xyz.dgz48.tasks.webapi.user.User;
import xyz.dgz48.tasks.webapi.user.UserRepository;

@Component
public class TasksJwtAuthenticationConverter
    implements Converter<Jwt, AbstractAuthenticationToken> {

  private final UserRepository userRepository;

  public TasksJwtAuthenticationConverter(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  @Override
  public AbstractAuthenticationToken convert(Jwt jwt) {
    String sub = jwt.getSubject();
    if (sub == null) {
      throw new InvalidBearerTokenException("JWT missing sub claim");
    }
    User user =
        userRepository
            .findByOidcSub(sub)
            .orElseThrow(() -> new InvalidBearerTokenException("User not found: " + sub));
    TasksPrincipal principal =
        new TasksPrincipal(
            user.getId(),
            user.getOidcSub(),
            user.getEmail(),
            user.getFullName(),
            user.getFullNameKana(),
            user.getDepartmentName());
    return new TasksAuthenticationToken(principal, List.of());
  }
}
