package xyz.dgz48.tasks.webapi.task.adapter.persistence;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import xyz.dgz48.tasks.webapi.task.domain.Task;
import xyz.dgz48.tasks.webapi.task.usecase.TaskRepository;

@Component
@RequiredArgsConstructor
class TaskJpaRepositoryAdapter implements TaskRepository {

  private final TaskJpaRepository jpaRepository;

  @Override
  public Optional<Task> findById(Long id) {
    return jpaRepository.findById(id).map(this::toDomain);
  }

  private Task toDomain(TaskJpaEntity entity) {
    return new Task(
        entity.getId(),
        entity.getTenantId(),
        entity.getTitle(),
        entity.getDescription(),
        entity.getStatus(),
        entity.getPriority(),
        entity.getVisibility(),
        entity.getOwnerId(),
        entity.getAssigneeId(),
        entity.getDueDate(),
        entity.getCompletedAt(),
        entity.getDeletedAt(),
        entity.getCreatedAt(),
        entity.getUpdatedAt());
  }
}
