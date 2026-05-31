package xyz.dgz48.tasks.webapi.task.usecase;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.dgz48.tasks.webapi.task.domain.Task;
import xyz.dgz48.tasks.webapi.task.domain.TaskAuthorizationDomainService;
import xyz.dgz48.tasks.webapi.task.domain.TaskNotFoundException;
import xyz.dgz48.tasks.webapi.task.domain.TaskNotViewableException;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GetTaskUseCase {

  private final TaskRepository taskRepository;
  private final TaskAuthorizationDomainService taskAuthorizationDomainService;

  public Task execute(Long taskId, Long userId) {
    Task task =
        taskRepository.findById(taskId).orElseThrow(() -> new TaskNotFoundException(taskId));
    if (!taskAuthorizationDomainService.canBeViewedBy(task, userId, List.of())) {
      throw new TaskNotViewableException(taskId);
    }
    return task;
  }
}
