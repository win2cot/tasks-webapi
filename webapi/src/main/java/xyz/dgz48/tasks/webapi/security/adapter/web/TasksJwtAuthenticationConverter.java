package xyz.dgz48.tasks.webapi.security.adapter.web;

import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.stereotype.Component;
import xyz.dgz48.tasks.webapi.security.domain.TasksPrincipal;
import xyz.dgz48.tasks.webapi.user.adapter.persistence.UserJpaEntity;
import xyz.dgz48.tasks.webapi.user.adapter.persistence.UserRepository;

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
    UserJpaEntity user =
        userRepository
            .findByOidcSub(sub)
            .orElseThrow(
                () -> new InvalidBearerTokenException("User not found for provided sub claim"));
    TasksPrincipal principal =
        new TasksPrincipal(
            user.getId(),
            user.getOidcSub(),
            user.getEmail(),
            user.getFullName(),
            user.getFullNameKana(),
            user.getDepartmentName());
    return new TasksAuthenticationToken(principal, realmAuthorities(jwt));
  }

  private static List<GrantedAuthority> realmAuthorities(Jwt jwt) {
    @Nullable Map<String, Object> realmAccess = jwt.getClaim("realm_access");
    if (realmAccess == null) {
      return List.of();
    }
    Object rolesObj = realmAccess.get("roles");
    if (!(rolesObj instanceof List<?> roles) || !roles.contains("APP_ADMIN")) {
      return List.of();
    }
    return List.of(new SimpleGrantedAuthority("ROLE_APP_ADMIN"));
  }
}
