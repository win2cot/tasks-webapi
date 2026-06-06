package xyz.dgz48.tasks.webapi.audit.adapter.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface AuditLogJpaRepository extends JpaRepository<AuditLogJpaEntity, Long> {

  Optional<AuditLogJpaEntity> findFirstByOrderByIdDesc();
}
