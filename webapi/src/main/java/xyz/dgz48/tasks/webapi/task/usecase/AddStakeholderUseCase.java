package xyz.dgz48.tasks.webapi.task.usecase;

import io.micrometer.observation.annotation.Observed;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.dgz48.tasks.webapi.audit.domain.AuditEventType;
import xyz.dgz48.tasks.webapi.audit.usecase.AuditLogPort;
import xyz.dgz48.tasks.webapi.task.domain.StakeholderAlreadyExistsException;
import xyz.dgz48.tasks.webapi.task.domain.Task;
import xyz.dgz48.tasks.webapi.task.domain.TaskAuthorizationDomainService;
import xyz.dgz48.tasks.webapi.task.domain.TaskNotFoundException;
import xyz.dgz48.tasks.webapi.task.domain.TaskNotViewableException;
import xyz.dgz48.tasks.webapi.task.domain.TaskOwnershipException;
import xyz.dgz48.tasks.webapi.task.domain.TaskStakeholder;
import xyz.dgz48.tasks.webapi.tenant.usecase.TenantMembershipPort;

@Service
@RequiredArgsConstructor
public class AddStakeholderUseCase {

  private final TaskRepository taskRepository;
  private final StakeholderRepository stakeholderRepository;
  private final TaskAuthorizationDomainService taskAuthorizationDomainService;
  private final TenantMembershipPort tenantMembershipPort;
  private final AuditLogPort auditLogPort;
  private final Clock clock;

  @Observed(name = "task.stakeholder.add")
  @Transactional
  public TaskStakeholder execute(Long taskId, Long operatorUserId, Long targetUserId) {
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
    // クロステナント登録を拒否(同一テナントの ACTIVE メンバーのみ登録可)
    if (tenantMembershipPort.findActiveRole(targetUserId, task.getTenantId()).isEmpty()) {
      throw new TaskOwnershipException(taskId);
    }
    if (stakeholderRepository.existsByTaskIdAndUserId(taskId, targetUserId)) {
      throw new StakeholderAlreadyExistsException(taskId, targetUserId);
    }
    TaskStakeholder stakeholder =
        stakeholderRepository.add(
            taskId, task.getTenantId(), targetUserId, operatorUserId, LocalDateTime.now(clock));
    auditLogPort.record(
        AuditEventType.STAKEHOLDER_ADDED,
        task.getTenantId(),
        operatorUserId,
        Map.of("taskId", taskId, "userId", targetUserId));
    return stakeholder;
  }
}
