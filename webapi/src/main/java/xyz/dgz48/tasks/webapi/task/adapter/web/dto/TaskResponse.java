package xyz.dgz48.tasks.webapi.task.adapter.web.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.jspecify.annotations.Nullable;
import xyz.dgz48.tasks.webapi.task.domain.Priority;
import xyz.dgz48.tasks.webapi.task.domain.Task;
import xyz.dgz48.tasks.webapi.task.domain.TaskStatus;
import xyz.dgz48.tasks.webapi.task.domain.Visibility;

public record TaskResponse(
    Long id,
    String title,
    @Nullable String description,
    TaskStatus status,
    Priority priority,
    Visibility visibility,
    Long ownerId,
    @Nullable Long assigneeId,
    LocalDate dueDate,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {

  public static TaskResponse from(Task task) {
    return new TaskResponse(
        task.getId(),
        task.getTitle(),
        task.getDescription(),
        task.getStatus(),
        task.getPriority(),
        task.getVisibility(),
        task.getOwnerId(),
        task.getAssigneeId(),
        task.getDueDate(),
        task.getCreatedAt(),
        task.getUpdatedAt());
  }
}
