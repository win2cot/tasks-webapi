package xyz.dgz48.tasks.webapi.security.adapter.web;

import java.util.List;
import java.util.Optional;
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
    UserJpaEntity user = resolveUser(sub, jwt).orElseThrow(UserNotRegisteredException::new);
    if (user.isAnonymized()) {
      throw new UserAnonymizedException();
    }
    if (user.isInactive()) {
      throw new UserInactiveException();
    }
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

  /**
   * sub で登録ユーザーを解決する。{@code findByOidcSub} がヒットすれば correlation 済み(通常ログイン)。ヒットしない場合は 初回ログインの
   * oidc_sub correlation(ADR-0040 §3.2 / ADR-0006 §3.2)を試みる: email クレームで pending placeholder
   * 行を突合し、本物の {@code sub} を書き戻す。会員登録(ADR-0040)/SPI insert で作られた行はこの経路で初めてログイン可能になる。
   *
   * <p>email が一致しても correlation 済み(別 Keycloak アカウントの sub に紐付く)/匿名化済みの行は突合しない(なりすまし防止)。
   */
  private Optional<UserJpaEntity> resolveUser(String sub, Jwt jwt) {
    Optional<UserJpaEntity> bySub = userRepository.findByOidcSub(sub);
    if (bySub.isPresent()) {
      return bySub;
    }
    String email = jwt.getClaimAsString("email");
    if (email == null || email.isBlank()) {
      return Optional.empty();
    }
    return userRepository
        .findByEmail(email)
        .filter(UserJpaEntity::isPendingCorrelation)
        .map(
            user -> {
              user.correlateOidcSub(sub);
              return userRepository.save(user);
            });
  }

  private List<GrantedAuthority> appAdminAuthorities(String sub) {
    if (appAdminUserRepository.existsByOidcSub(sub)) {
      return List.of(new SimpleGrantedAuthority("ROLE_APP_ADMIN"));
    }
    return List.of();
  }
}
