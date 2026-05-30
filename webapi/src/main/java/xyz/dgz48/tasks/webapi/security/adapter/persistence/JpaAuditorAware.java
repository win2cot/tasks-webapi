package xyz.dgz48.tasks.webapi.security.adapter.persistence;

import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import xyz.dgz48.tasks.webapi.security.domain.TasksPrincipal;

@Component("jpaAuditorAware")
class JpaAuditorAware implements AuditorAware<Long> {

  @Override
  public Optional<Long> getCurrentAuditor() {
    @Nullable Authentication authentication =
        SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !authentication.isAuthenticated()) {
      return Optional.empty();
    }
    Object principal = authentication.getPrincipal();
    if (principal instanceof TasksPrincipal tasksPrincipal) {
      return Optional.of(tasksPrincipal.getId());
    }
    return Optional.empty();
  }
}
