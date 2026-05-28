package xyz.dgz48.tasks.webapi.task.adapter.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskJpaRepository extends JpaRepository<TaskJpaEntity, Long> {

  Optional<TaskJpaEntity> findByTenantIdAndId(Long tenantId, Long id);
}
