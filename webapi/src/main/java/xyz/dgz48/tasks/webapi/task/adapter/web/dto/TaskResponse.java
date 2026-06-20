package xyz.dgz48.tasks.webapi.task.adapter.web.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import xyz.dgz48.tasks.webapi.shared.infra.AppZones;
import xyz.dgz48.tasks.webapi.task.domain.Priority;
import xyz.dgz48.tasks.webapi.task.domain.Task;
import xyz.dgz48.tasks.webapi.task.domain.TaskStatus;
import xyz.dgz48.tasks.webapi.task.domain.Visibility;
import xyz.dgz48.tasks.webapi.user.adapter.persistence.UserJpaEntity;

public record TaskResponse(
    Long id,
    Long tenantId,
    String title,
    @Nullable String description,
    Priority priority,
    TaskStatus status,
    Visibility visibility,
    UserSummaryResponse owner,
    @Nullable UserSummaryResponse assignee,
    LocalDate dueDate,
    @Nullable OffsetDateTime completedAt,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    Long version,
    boolean editable,
    boolean deletable) {

  public static TaskResponse from(Task task, Long currentUserId, Map<Long, UserJpaEntity> userMap) {
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

    return new TaskResponse(
        task.getId(),
        task.getTenantId(),
        task.getTitle(),
        task.getDescription(),
        task.getPriority(),
        task.getStatus(),
        task.getVisibility(),
        owner,
        assignee,
        task.getDueDate(),
        task.getCompletedAt() != null
            ? task.getCompletedAt().atZone(AppZones.JST).toOffsetDateTime()
            : null,
        task.getCreatedAt().atZone(AppZones.JST).toOffsetDateTime(),
        task.getUpdatedAt().atZone(AppZones.JST).toOffsetDateTime(),
        task.getVersion(),
        isOwner,
        isOwner);
  }
}
