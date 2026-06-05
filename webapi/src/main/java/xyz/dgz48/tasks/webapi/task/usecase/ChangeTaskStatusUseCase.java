package xyz.dgz48.tasks.webapi.task.usecase;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.dgz48.tasks.webapi.task.domain.Task;
import xyz.dgz48.tasks.webapi.task.domain.TaskAuthorizationDomainService;
import xyz.dgz48.tasks.webapi.task.domain.TaskNotFoundException;
import xyz.dgz48.tasks.webapi.task.domain.TaskNotViewableException;
import xyz.dgz48.tasks.webapi.task.domain.TaskOwnershipException;
import xyz.dgz48.tasks.webapi.task.domain.TaskStatus;

@Service
@RequiredArgsConstructor
public class ChangeTaskStatusUseCase {

  private final TaskRepository taskRepository;
  private final TaskAuthorizationDomainService taskAuthorizationDomainService;
  private final Clock clock;

  @Transactional
  public Task execute(Long taskId, Long userId, TaskStatus newStatus, Long ifMatchVersion) {
    Task task =
        taskRepository.findById(taskId).orElseThrow(() -> new TaskNotFoundException(taskId));
    if (!taskAuthorizationDomainService.canBeViewedBy(task, userId, List.of())) {
      throw new TaskNotViewableException(taskId);
    }
    if (!taskAuthorizationDomainService.canChangeStatusBy(task, userId)) {
      throw new TaskOwnershipException(taskId);
    }
    if (!task.getVersion().equals(ifMatchVersion)) {
      throw new ObjectOptimisticLockingFailureException(Task.class, taskId);
    }
    task.changeStatus(newStatus, LocalDateTime.now(clock));
    return taskRepository.save(task);
  }
}
