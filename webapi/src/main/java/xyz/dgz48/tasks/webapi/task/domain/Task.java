package xyz.dgz48.tasks.webapi.task.domain;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.Getter;
import org.jspecify.annotations.Nullable;

/** タスクのドメインモデル(POJO)。JPA アノテーション禁止、Spring 非依存。 */
@Getter
public class Task {

  private final Long id;
  private final Long tenantId;
  private final String title;
  @Nullable private final String description;
  private final TaskStatus status;
  private final Priority priority;
  private final Visibility visibility;
  private final Long ownerId;
  @Nullable private final Long assigneeId;
  private final LocalDate dueDate;
  @Nullable private final OffsetDateTime completedAt;
  @Nullable private final OffsetDateTime deletedAt;
  private final OffsetDateTime createdAt;
  private final OffsetDateTime updatedAt;

  public Task(
      Long id,
      Long tenantId,
      String title,
      @Nullable String description,
      TaskStatus status,
      Priority priority,
      Visibility visibility,
      Long ownerId,
      @Nullable Long assigneeId,
      LocalDate dueDate,
      @Nullable OffsetDateTime completedAt,
      @Nullable OffsetDateTime deletedAt,
      OffsetDateTime createdAt,
      OffsetDateTime updatedAt) {
    this.id = id;
    this.tenantId = tenantId;
    this.title = title;
    this.description = description;
    this.status = status;
    this.priority = priority;
    this.visibility = visibility;
    this.ownerId = ownerId;
    this.assigneeId = assigneeId;
    this.dueDate = dueDate;
    this.completedAt = completedAt;
    this.deletedAt = deletedAt;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }
}
