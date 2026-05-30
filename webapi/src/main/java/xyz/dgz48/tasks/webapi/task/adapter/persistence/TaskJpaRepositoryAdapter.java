package xyz.dgz48.tasks.webapi.task.adapter.persistence;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import xyz.dgz48.tasks.webapi.task.domain.Task;
import xyz.dgz48.tasks.webapi.task.usecase.TaskRepository;

/**
 * {@link TaskRepository} ポートを Spring Data JPA で実装。 {@link TaskJpaEntity} と {@link Task}
 * ドメインモデル間の変換責務を担う。
 */
@Component
@RequiredArgsConstructor
class TaskJpaRepositoryAdapter implements TaskRepository {

  private final TaskJpaRepository jpaRepository;

  @Override
  public Optional<Task> findByIdAndTenantId(Long id, Long tenantId) {
    return jpaRepository.findByIdAndTenantId(id, tenantId).map(this::toDomain);
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
