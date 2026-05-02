package xyz.dgz48.tasks.webapi.security;

import java.io.Serial;
import java.util.Collection;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

public final class TasksAuthenticationToken extends AbstractAuthenticationToken {

  @Serial private static final long serialVersionUID = 1L;

  private final TasksPrincipal principal;
  private final Jwt credentials;

  public TasksAuthenticationToken(
      TasksPrincipal principal,
      Jwt credentials,
      Collection<? extends GrantedAuthority> authorities) {
    super(authorities);
    this.principal = principal;
    this.credentials = credentials;
    setAuthenticated(true);
  }

  @Override
  public TasksPrincipal getPrincipal() {
    return principal;
  }

  @Override
  public Jwt getCredentials() {
    return credentials;
  }
}
