package xyz.dgz48.tasks.webapi.task.usecase;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.dgz48.tasks.webapi.audit.domain.AuditEventType;
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

    auditLogPort.record(
        AuditEventType.TASK_UPDATED, task.getTenantId(), userId, buildAuditDetail(changes));

    return saved;
  }

  private static String buildAuditDetail(List<FieldChange> changes) {
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < changes.size(); i++) {
      FieldChange c = changes.get(i);
      if (i > 0) sb.append(",");
      sb.append("{\"field\":\"")
          .append(c.field())
          .append("\",\"old\":")
          .append(toJsonValue(c.oldValue()))
          .append(",\"new\":")
          .append(toJsonValue(c.newValue()))
          .append("}");
    }
    sb.append("]");
    return sb.toString();
  }

  private static String toJsonValue(@Nullable Object value) {
    if (value == null) return "null";
    if (value instanceof String s) {
      return "\"" + escapeJsonString(s) + "\"";
    }
    if (value instanceof Enum<?> e) return "\"" + escapeJsonString(e.name()) + "\"";
    if (value instanceof Number || value instanceof Boolean) return String.valueOf(value);
    return "\"" + escapeJsonString(String.valueOf(value)) + "\"";
  }

  private static String escapeJsonString(String s) {
    StringBuilder sb = new StringBuilder(s.length() + 16);
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      switch (c) {
        case '"' -> sb.append("\\\"");
        case '\\' -> sb.append("\\\\");
        case '\n' -> sb.append("\\n");
        case '\r' -> sb.append("\\r");
        case '\t' -> sb.append("\\t");
        case '\b' -> sb.append("\\b");
        case '\f' -> sb.append("\\f");
        default -> {
          if (c < 0x20) {
            sb.append(String.format("\\u%04X", (int) c));
          } else {
            sb.append(c);
          }
        }
      }
    }
    return sb.toString();
  }
}
