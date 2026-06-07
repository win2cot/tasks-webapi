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
import xyz.dgz48.tasks.webapi.task.domain.Priority;
import xyz.dgz48.tasks.webapi.task.domain.Task;
import xyz.dgz48.tasks.webapi.task.domain.Visibility;

/** タスク作成ユースケース(POST /api/tasks)。 */
@Service
@RequiredArgsConstructor
public class CreateTaskUseCase {

  private final TaskRepository taskRepository;
  private final StakeholderRepository stakeholderRepository;
  private final AuditLogPort auditLogPort;
  private final Clock clock;

  /**
   * タスクを作成する。
   *
   * @param tenantId 現在テナント ID(TenantContext から取得)
   * @param ownerUserId 作成ユーザー ID(認証トークンから取得)
   * @param title タイトル
   * @param description 説明(nullable)
   * @param priority 優先度
   * @param visibility 公開範囲
   * @param assigneeId 担当者 ID(nullable)
   * @param dueDate 期限
   * @param stakeholderUserIds 関係者 ID リスト(visibility=STAKEHOLDERS 時に反映)
   * @return 作成されたタスク
   */
  @Transactional
  public Task execute(
      Long tenantId,
      Long ownerUserId,
      String title,
      @Nullable String description,
      Priority priority,
      Visibility visibility,
      @Nullable Long assigneeId,
      java.time.LocalDate dueDate,
      @Nullable List<Long> stakeholderUserIds) {

    Task task =
        taskRepository.create(
            tenantId, ownerUserId, title, description, priority, visibility, assigneeId, dueDate);

    if (visibility == Visibility.STAKEHOLDERS && stakeholderUserIds != null) {
      LocalDateTime now = LocalDateTime.now(clock);
      for (Long userId : stakeholderUserIds) {
        stakeholderRepository.add(task.getId(), tenantId, userId, ownerUserId, now);
      }
    }

    auditLogPort.record(
        AuditEventType.TASK_CREATED, tenantId, ownerUserId, "{\"taskId\":" + task.getId() + "}");

    return task;
  }
}
