package xyz.dgz48.tasks.webapi.security.adapter.web;

import java.util.Arrays;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithSecurityContextFactory;
import xyz.dgz48.tasks.webapi.security.domain.TasksPrincipal;

/** {@link WithMockJwt} の SecurityContext を構築するファクトリ。 */
public class WithMockJwtSecurityContextFactory implements WithSecurityContextFactory<WithMockJwt> {

  @Override
  public SecurityContext createSecurityContext(WithMockJwt annotation) {
    List<GrantedAuthority> authorities =
        Arrays.stream(annotation.roles())
            .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role))
            .toList();

    @Nullable String departmentName =
        annotation.departmentName().isEmpty() ? null : annotation.departmentName();

    TasksPrincipal principal =
        new TasksPrincipal(
            annotation.id(),
            annotation.sub(),
            annotation.email(),
            annotation.fullName(),
            annotation.fullNameKana(),
            departmentName);

    TasksAuthenticationToken authentication = new TasksAuthenticationToken(principal, authorities);

    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(authentication);
    return context;
  }
}
