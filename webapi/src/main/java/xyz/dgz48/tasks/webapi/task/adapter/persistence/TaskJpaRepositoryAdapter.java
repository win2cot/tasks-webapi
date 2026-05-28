package xyz.dgz48.tasks.webapi.task.adapter.persistence;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import xyz.dgz48.tasks.webapi.task.domain.Task;
import xyz.dgz48.tasks.webapi.task.usecase.TaskRepository;

@Repository
@RequiredArgsConstructor
public class TaskJpaRepositoryAdapter implements TaskRepository {

  private final TaskJpaRepository jpaRepository;

  @Override
  public Optional<Task> findByTenantIdAndId(Long tenantId, Long id) {
    return jpaRepository.findByTenantIdAndId(tenantId, id).map(TaskJpaEntity::toDomain);
  }
}
