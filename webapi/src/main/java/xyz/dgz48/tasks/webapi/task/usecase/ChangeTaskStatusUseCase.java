package xyz.dgz48.tasks.webapi.task.usecase;

import io.micrometer.observation.annotation.ObservationKeyValue;
import io.micrometer.observation.annotation.Observed;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.dgz48.tasks.webapi.audit.domain.AuditEventType;
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
  private final StakeholderRepository stakeholderRepository;
  private final TaskAuthorizationDomainService taskAuthorizationDomainService;
  private final Clock clock;

  @Observed(name = "task.change-status")
  @Transactional
  public Task execute(
      Long taskId, Long userId, @ObservationKeyValue("task.new-status") TaskStatus newStatus) {
    Task task =
        taskRepository.findById(taskId).orElseThrow(() -> new TaskNotFoundException(taskId));
    List<Long> stakeholderUserIds =
        stakeholderRepository.findUserIdsByTaskId(taskId, task.getTenantId());
    if (!taskAuthorizationDomainService.canBeViewedBy(task, userId, stakeholderUserIds)) {
      throw new TaskNotViewableException(taskId);
    }
    if (!taskAuthorizationDomainService.canChangeStatusBy(task, userId)) {
      throw new TaskOwnershipException(taskId, AuditEventType.STATUS_CHANGE_DENIED);
    }
    LocalDateTime now = LocalDateTime.now(clock);
    task.changeStatus(newStatus, now);
    return taskRepository.saveStatus(taskId, task.getStatus(), task.getCompletedAt(), now);
  }
}
