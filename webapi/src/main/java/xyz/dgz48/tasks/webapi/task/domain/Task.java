package xyz.dgz48.tasks.webapi.task.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.jspecify.annotations.Nullable;

/**
 * Task ドメインモデル(JPA 非依存 POJO)。 設計規約 §1.1 のクリーンアーキ 4 層に従い domain 層に配置。 Persistence 層では {@code
 * TaskJpaEntity} と相互変換する。
 */
@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Task {

  @EqualsAndHashCode.Include private final Long id;
  private final Long tenantId;
  private final String title;
  @Nullable private final String description;
  private final TaskStatus status;
  private final Priority priority;
  private Visibility visibility;
  private final Long ownerId;
  @Nullable private final Long assigneeId;
  private final LocalDate dueDate;
  @Nullable private final LocalDateTime completedAt;
  @Nullable private final LocalDateTime deletedAt;
  private final LocalDateTime createdAt;
  private final LocalDateTime updatedAt;

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
      @Nullable LocalDateTime completedAt,
      @Nullable LocalDateTime deletedAt,
      LocalDateTime createdAt,
      LocalDateTime updatedAt) {
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

  public void changeVisibility(Visibility newVisibility) {
    this.visibility = newVisibility;
  }
}
