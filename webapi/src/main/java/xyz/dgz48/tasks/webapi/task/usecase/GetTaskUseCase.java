package xyz.dgz48.tasks.webapi.task.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.dgz48.tasks.webapi.task.adapter.persistence.TaskJpaRepository;
import xyz.dgz48.tasks.webapi.task.domain.Task;
import xyz.dgz48.tasks.webapi.task.domain.TaskNotFoundException;

@Service
@RequiredArgsConstructor
public class GetTaskUseCase {

  private final TaskJpaRepository taskJpaRepository;

  @Transactional(readOnly = true)
  public Task getTask(Long tenantId, Long taskId) {
    return taskJpaRepository
        .findByTenantIdAndId(tenantId, taskId)
        .map(e -> e.toDomain())
        .orElseThrow(() -> new TaskNotFoundException(taskId));
  }
}
