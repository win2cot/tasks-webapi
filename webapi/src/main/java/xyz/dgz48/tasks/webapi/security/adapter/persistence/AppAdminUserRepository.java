package xyz.dgz48.tasks.webapi.security.adapter.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AppAdminUserRepository extends JpaRepository<AppAdminUserJpaEntity, Long> {
  boolean existsByOidcSub(String oidcSub);
}
