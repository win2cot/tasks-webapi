package xyz.dgz48.tasks.webapi.task.usecase;

import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.dgz48.tasks.webapi.audit.domain.AuditEventType;
import xyz.dgz48.tasks.webapi.audit.usecase.AuditLogPort;
import xyz.dgz48.tasks.webapi.task.domain.StakeholderNotFoundException;
import xyz.dgz48.tasks.webapi.task.domain.Task;
import xyz.dgz48.tasks.webapi.task.domain.TaskAuthorizationDomainService;
import xyz.dgz48.tasks.webapi.task.domain.TaskNotFoundException;
import xyz.dgz48.tasks.webapi.task.domain.TaskNotViewableException;
import xyz.dgz48.tasks.webapi.task.domain.TaskOwnershipException;

@Service
@RequiredArgsConstructor
public class RemoveStakeholderUseCase {

  private final TaskRepository taskRepository;
  private final StakeholderRepository stakeholderRepository;
  private final TaskAuthorizationDomainService taskAuthorizationDomainService;
  private final AuditLogPort auditLogPort;

  @Transactional
  public void execute(Long taskId, Long operatorUserId, Long targetUserId) {
    Task task =
        taskRepository.findById(taskId).orElseThrow(() -> new TaskNotFoundException(taskId));
    List<Long> stakeholderUserIds =
        stakeholderRepository.findUserIdsByTaskId(taskId, task.getTenantId());
    if (!taskAuthorizationDomainService.canBeViewedBy(task, operatorUserId, stakeholderUserIds)) {
      throw new TaskNotViewableException(taskId);
    }
    if (!taskAuthorizationDomainService.canManageStakeholdersBy(task, operatorUserId)) {
      throw new TaskOwnershipException(taskId);
    }
    if (!stakeholderRepository.existsByTaskIdAndUserId(taskId, targetUserId)) {
      throw new StakeholderNotFoundException(taskId, targetUserId);
    }
    stakeholderRepository.removeByTaskIdAndUserId(taskId, targetUserId, task.getTenantId());
    auditLogPort.record(
        AuditEventType.STAKEHOLDER_REMOVED,
        task.getTenantId(),
        operatorUserId,
        Map.of("taskId", taskId, "userId", targetUserId));
  }
}
