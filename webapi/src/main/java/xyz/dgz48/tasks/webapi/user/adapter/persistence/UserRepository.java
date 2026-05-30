package xyz.dgz48.tasks.webapi.user.adapter.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<UserJpaEntity, Long> {
  Optional<UserJpaEntity> findByOidcSub(String oidcSub);
}
