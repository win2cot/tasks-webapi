package xyz.dgz48.tasks.webapi.task.adapter.web.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import xyz.dgz48.tasks.webapi.task.domain.Priority;
import xyz.dgz48.tasks.webapi.task.domain.Task;
import xyz.dgz48.tasks.webapi.task.domain.TaskStatus;
import xyz.dgz48.tasks.webapi.task.domain.Visibility;
import xyz.dgz48.tasks.webapi.user.adapter.persistence.UserJpaEntity;

/** OpenAPI Task スキーマに対応するレスポンス DTO。 */
public record TaskListItemResponse(
    Long id,
    Long version,
    String title,
    @Nullable String description,
    TaskStatus status,
    Priority priority,
    Visibility visibility,
    UserSummaryResponse owner,
    @Nullable UserSummaryResponse assignee,
    LocalDate dueDate,
    @Nullable OffsetDateTime completedAt,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    boolean editable,
    boolean deletable) {

  private static final ZoneId JST = ZoneId.of("Asia/Tokyo");

  public static TaskListItemResponse from(
      Task task, Long currentUserId, Map<Long, UserJpaEntity> userMap) {
    UserJpaEntity ownerEntity = userMap.get(task.getOwnerId());
    UserSummaryResponse owner =
        ownerEntity != null
            ? new UserSummaryResponse(ownerEntity.getId(), ownerEntity.getFullName())
            : new UserSummaryResponse(task.getOwnerId(), "");

    @Nullable UserSummaryResponse assignee = null;
    if (task.getAssigneeId() != null) {
      UserJpaEntity assigneeEntity = userMap.get(task.getAssigneeId());
      assignee =
          assigneeEntity != null
              ? new UserSummaryResponse(assigneeEntity.getId(), assigneeEntity.getFullName())
              : new UserSummaryResponse(task.getAssigneeId(), "");
    }

    boolean isOwner = task.getOwnerId().equals(currentUserId);

    return new TaskListItemResponse(
        task.getId(),
        task.getVersion(),
        task.getTitle(),
        task.getDescription(),
        task.getStatus(),
        task.getPriority(),
        task.getVisibility(),
        owner,
        assignee,
        task.getDueDate(),
        task.getCompletedAt() != null ? task.getCompletedAt().atZone(JST).toOffsetDateTime() : null,
        task.getCreatedAt().atZone(JST).toOffsetDateTime(),
        task.getUpdatedAt().atZone(JST).toOffsetDateTime(),
        isOwner,
        isOwner);
  }
}
