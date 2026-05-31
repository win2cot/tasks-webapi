package xyz.dgz48.tasks.webapi.task.adapter.web.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.jspecify.annotations.Nullable;
import xyz.dgz48.tasks.webapi.task.domain.Priority;
import xyz.dgz48.tasks.webapi.task.domain.Task;
import xyz.dgz48.tasks.webapi.task.domain.TaskStatus;
import xyz.dgz48.tasks.webapi.task.domain.Visibility;

public record TaskResponse(
    Long id,
    Long tenantId,
    String title,
    @Nullable String description,
    Priority priority,
    TaskStatus status,
    Visibility visibility,
    @Nullable Long assigneeId,
    LocalDate dueDate,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {

  private static final ZoneId JST = ZoneId.of("Asia/Tokyo");

  public static TaskResponse from(Task task) {
    return new TaskResponse(
        task.getId(),
        task.getTenantId(),
        task.getTitle(),
        task.getDescription(),
        task.getPriority(),
        task.getStatus(),
        task.getVisibility(),
        task.getAssigneeId(),
        task.getDueDate(),
        task.getCreatedAt().atZone(JST).toOffsetDateTime(),
        task.getUpdatedAt().atZone(JST).toOffsetDateTime());
  }
}
