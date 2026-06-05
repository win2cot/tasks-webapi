package xyz.dgz48.tasks.webapi.security.adapter.web;

import java.util.List;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.stereotype.Component;
import xyz.dgz48.tasks.webapi.security.adapter.persistence.AppAdminUserRepository;
import xyz.dgz48.tasks.webapi.security.domain.TasksPrincipal;
import xyz.dgz48.tasks.webapi.user.adapter.persistence.UserJpaEntity;
import xyz.dgz48.tasks.webapi.user.adapter.persistence.UserRepository;

@Component
public class TasksJwtAuthenticationConverter
    implements Converter<Jwt, AbstractAuthenticationToken> {

  private final UserRepository userRepository;
  private final AppAdminUserRepository appAdminUserRepository;

  public TasksJwtAuthenticationConverter(
      UserRepository userRepository, AppAdminUserRepository appAdminUserRepository) {
    this.userRepository = userRepository;
    this.appAdminUserRepository = appAdminUserRepository;
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
    return new TasksAuthenticationToken(principal, appAdminAuthorities(sub));
  }

  private List<GrantedAuthority> appAdminAuthorities(String sub) {
    if (appAdminUserRepository.existsByOidcSub(sub)) {
      return List.of(new SimpleGrantedAuthority("ROLE_APP_ADMIN"));
    }
    return List.of();
  }
}
