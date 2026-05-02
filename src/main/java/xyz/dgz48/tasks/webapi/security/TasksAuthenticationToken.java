package xyz.dgz48.tasks.webapi.security;

import java.util.Collection;
import org.jspecify.annotations.Nullable;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

public class TasksAuthenticationToken extends AbstractAuthenticationToken {

  private final TasksPrincipal principal;

  public TasksAuthenticationToken(
      TasksPrincipal principal, Collection<? extends GrantedAuthority> authorities) {
    super(authorities);
    this.principal = principal;
    setAuthenticated(true);
  }

  @Override
  @Nullable
  public Object getCredentials() {
    return null;
  }

  @Override
  public TasksPrincipal getPrincipal() {
    return principal;
  }
}
