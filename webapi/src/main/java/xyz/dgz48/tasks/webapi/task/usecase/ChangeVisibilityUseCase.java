package xyz.dgz48.tasks.webapi.task.usecase;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.dgz48.tasks.webapi.audit.domain.AuditEventType;
import xyz.dgz48.tasks.webapi.audit.usecase.AuditLogPort;
import xyz.dgz48.tasks.webapi.task.domain.Task;
import xyz.dgz48.tasks.webapi.task.domain.TaskAuthorizationDomainService;
import xyz.dgz48.tasks.webapi.task.domain.TaskNotFoundException;
import xyz.dgz48.tasks.webapi.task.domain.TaskNotViewableException;
import xyz.dgz48.tasks.webapi.task.domain.TaskOwnershipException;
import xyz.dgz48.tasks.webapi.task.domain.Visibility;

/** PATCH /api/tasks/{id}/visibility — 公開範囲変更ユースケース(ADR-0005 / ADR-0013)。 */
@Service
@RequiredArgsConstructor
public class ChangeVisibilityUseCase {

  private final TaskRepository taskRepository;
  private final StakeholderRepository stakeholderRepository;
  private final TaskAuthorizationDomainService taskAuthorizationDomainService;
  private final AuditLogPort auditLogPort;
  private final Clock clock;

  @Transactional
  public Task execute(
      Long taskId, Long userId, Visibility newVisibility, @Nullable List<Long> stakeholderUserIds) {

    Task task =
        taskRepository.findById(taskId).orElseThrow(() -> new TaskNotFoundException(taskId));
    List<Long> currentStakeholderUserIds =
        stakeholderRepository.findUserIdsByTaskId(taskId, task.getTenantId());

    if (!taskAuthorizationDomainService.canBeViewedBy(task, userId, currentStakeholderUserIds)) {
      throw new TaskNotViewableException(taskId);
    }
    if (!taskAuthorizationDomainService.canChangeVisibilityBy(task, userId)) {
      throw new TaskOwnershipException(taskId);
    }

    Visibility oldVisibility = task.getVisibility();
    task.changeVisibility(newVisibility);
    Task saved = taskRepository.save(task);

    LocalDateTime now = LocalDateTime.now(clock);
    switch (newVisibility) {
      case STAKEHOLDERS -> {
        List<Long> newIds = stakeholderUserIds != null ? stakeholderUserIds : List.of();
        stakeholderRepository.replaceAll(taskId, task.getTenantId(), newIds, userId, now);
      }
      case PRIVATE -> {
        int purgedCount = stakeholderRepository.deleteAllByTaskId(taskId, task.getTenantId());
        if (purgedCount > 0) {
          auditLogPort.record(
              AuditEventType.STAKEHOLDER_PURGED,
              task.getTenantId(),
              userId,
              "{\"taskId\":" + taskId + ",\"purgedCount\":" + purgedCount + "}");
        }
      }
      case TENANT -> {
        // 関係者レコードを保持(将来 STAKEHOLDERS 再昇格時に流用可)
      }
    }

    auditLogPort.record(
        AuditEventType.VISIBILITY_CHANGED,
        task.getTenantId(),
        userId,
        "{\"taskId\":"
            + taskId
            + ",\"from\":\""
            + oldVisibility
            + "\",\"to\":\""
            + newVisibility
            + "\"}");

    return saved;
  }
}
