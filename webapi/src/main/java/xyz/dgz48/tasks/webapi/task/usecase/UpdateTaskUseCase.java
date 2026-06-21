package xyz.dgz48.tasks.webapi.task.usecase;

import io.micrometer.observation.annotation.Observed;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.dgz48.tasks.webapi.audit.domain.AuditEventType;
import xyz.dgz48.tasks.webapi.audit.usecase.AuditFieldChange;
import xyz.dgz48.tasks.webapi.audit.usecase.AuditLogPort;
import xyz.dgz48.tasks.webapi.shared.exception.PreconditionFailedException;
import xyz.dgz48.tasks.webapi.task.domain.FieldChange;
import xyz.dgz48.tasks.webapi.task.domain.Task;
import xyz.dgz48.tasks.webapi.task.domain.TaskAuditDiffDomainService;
import xyz.dgz48.tasks.webapi.task.domain.TaskAuthorizationDomainService;
import xyz.dgz48.tasks.webapi.task.domain.TaskNotFoundException;
import xyz.dgz48.tasks.webapi.task.domain.TaskNotViewableException;
import xyz.dgz48.tasks.webapi.task.domain.TaskOwnershipException;
import xyz.dgz48.tasks.webapi.task.domain.TaskPatchCommand;

/** PATCH /api/tasks/{id} — タスク部分更新ユースケース(ADR-0012 / ADR-0013 / ADR-0014)。 */
@Service
@RequiredArgsConstructor
public class UpdateTaskUseCase {

  private final TaskRepository taskRepository;
  private final StakeholderRepository stakeholderRepository;
  private final TaskAuthorizationDomainService taskAuthorizationDomainService;
  private final TaskAuditDiffDomainService taskAuditDiffDomainService;
  private final AuditLogPort auditLogPort;

  @Observed(name = "task.update")
  @Transactional(readOnly = false)
  public Task execute(Long taskId, Long userId, TaskPatchCommand cmd, Long ifMatchVersion) {
    Task task =
        taskRepository.findById(taskId).orElseThrow(() -> new TaskNotFoundException(taskId));

    List<Long> stakeholderUserIds =
        stakeholderRepository.findUserIdsByTaskId(taskId, task.getTenantId());

    if (!taskAuthorizationDomainService.canBeViewedBy(task, userId, stakeholderUserIds)) {
      throw new TaskNotViewableException(taskId);
    }
    if (!taskAuthorizationDomainService.canBeEditedBy(task, userId)) {
      throw new TaskOwnershipException(taskId);
    }
    if (!task.getVersion().equals(ifMatchVersion)) {
      throw new PreconditionFailedException("バージョンが競合しています: task=" + taskId);
    }

    List<FieldChange> changes = taskAuditDiffDomainService.diff(task, cmd);
    if (changes.isEmpty()) {
      return task;
    }

    task.applyPatch(cmd);
    Task saved = taskRepository.save(task);

    List<AuditFieldChange> auditChanges =
        changes.stream()
            .map(c -> new AuditFieldChange(c.field(), c.oldValue(), c.newValue()))
            .toList();
    auditLogPort.record(AuditEventType.TASK_UPDATED, task.getTenantId(), userId, auditChanges);

    return saved;
  }
}
