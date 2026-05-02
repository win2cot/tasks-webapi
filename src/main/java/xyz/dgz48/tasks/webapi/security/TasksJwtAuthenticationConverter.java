package xyz.dgz48.tasks.webapi.security;

import java.util.Collections;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.stereotype.Component;
import xyz.dgz48.tasks.webapi.user.UserRepository;

@Component
public final class TasksJwtAuthenticationConverter
    implements Converter<Jwt, AbstractAuthenticationToken> {

  private final UserRepository userRepository;
  private final JwtGrantedAuthoritiesConverter jwtGrantedAuthoritiesConverter;

  public TasksJwtAuthenticationConverter(UserRepository userRepository) {
    this.userRepository = userRepository;
    this.jwtGrantedAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();
  }

  @Override
  public AbstractAuthenticationToken convert(Jwt jwt) {
    String sub = jwt.getSubject();
    if (sub == null) {
      throw new BadCredentialsException("JWT missing sub claim");
    }
    var user =
        userRepository
            .findByOidcSub(sub)
            .orElseThrow(() -> new BadCredentialsException("User not found: " + sub));
    var principal =
        new TasksPrincipal(
            user.getId(),
            user.getOidcSub(),
            user.getEmail(),
            user.getFullName(),
            user.getFullNameKana(),
            user.getDepartmentName());
    var authorities = jwtGrantedAuthoritiesConverter.convert(jwt);
    return new TasksAuthenticationToken(
        principal, jwt, authorities != null ? authorities : Collections.emptyList());
  }
}
