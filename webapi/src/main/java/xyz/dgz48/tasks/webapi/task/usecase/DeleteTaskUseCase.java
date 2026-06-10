package xyz.dgz48.tasks.webapi.task.usecase;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.dgz48.tasks.webapi.audit.domain.AuditEventType;
import xyz.dgz48.tasks.webapi.audit.usecase.AuditLogPort;
import xyz.dgz48.tasks.webapi.shared.exception.PreconditionFailedException;
import xyz.dgz48.tasks.webapi.task.domain.Task;
import xyz.dgz48.tasks.webapi.task.domain.TaskAuthorizationDomainService;
import xyz.dgz48.tasks.webapi.task.domain.TaskNotFoundException;
import xyz.dgz48.tasks.webapi.task.domain.TaskNotViewableException;
import xyz.dgz48.tasks.webapi.task.domain.TaskOwnershipException;

/** DELETE /api/tasks/{id} — タスク論理削除ユースケース(ADR-0005 / ADR-0012 / ADR-0013)。 */
@Service
@RequiredArgsConstructor
public class DeleteTaskUseCase {

  private final TaskRepository taskRepository;
  private final StakeholderRepository stakeholderRepository;
  private final TaskAuthorizationDomainService taskAuthorizationDomainService;
  private final AuditLogPort auditLogPort;
  private final Clock clock;

  @Transactional
  public void execute(Long taskId, Long userId, Long ifMatchVersion) {
    Task task =
        taskRepository.findById(taskId).orElseThrow(() -> new TaskNotFoundException(taskId));
    List<Long> stakeholderUserIds =
        stakeholderRepository.findUserIdsByTaskId(taskId, task.getTenantId());

    if (!taskAuthorizationDomainService.canBeViewedBy(task, userId, stakeholderUserIds)) {
      throw new TaskNotViewableException(taskId);
    }
    if (!taskAuthorizationDomainService.canBeDeletedBy(task, userId)) {
      throw new TaskOwnershipException(taskId);
    }
    if (!task.getVersion().equals(ifMatchVersion)) {
      throw new PreconditionFailedException("バージョンが競合しています: task=" + taskId);
    }

    taskRepository.softDelete(task, LocalDateTime.now(clock));

    auditLogPort.record(
        AuditEventType.TASK_DELETED, task.getTenantId(), userId, "{\"taskId\":" + taskId + "}");
  }
}
