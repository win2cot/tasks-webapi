package xyz.dgz48.tasks.webapi.task.adapter.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface TaskJpaRepository extends JpaRepository<TaskJpaEntity, Long> {

  Optional<TaskJpaEntity> findByIdAndTenantId(Long id, Long tenantId);
}
