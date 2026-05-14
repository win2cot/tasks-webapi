package xyz.dgz48.tasks.webapi.task;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "tasks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SuppressWarnings("NullAway.Init")
public class Task {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "tenant_id", nullable = false)
  private Long tenantId;

  @Column(nullable = false, length = 100)
  private String title;

  @Nullable
  @Column(columnDefinition = "TEXT")
  private String description;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private TaskStatus status;

  @Column(nullable = false, length = 10)
  private String priority;

  @Column(nullable = false, length = 20)
  private String visibility;

  @Column(name = "owner_id", nullable = false)
  private Long ownerId;

  @Nullable
  @Column(name = "assignee_id")
  private Long assigneeId;

  @Column(name = "due_date", nullable = false)
  private LocalDate dueDate;

  @Nullable
  @Column(name = "completed_at")
  private LocalDateTime completedAt;

  @Nullable
  @Column(name = "deleted_at")
  private LocalDateTime deletedAt;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  public Task(
      Long tenantId,
      TaskStatus status,
      String priority,
      Long ownerId,
      String title,
      @Nullable String description,
      LocalDate dueDate) {
    this.tenantId = tenantId;
    this.title = title;
    this.description = description;
    this.status = status;
    this.priority = priority;
    this.visibility = "TENANT";
    this.ownerId = ownerId;
    this.dueDate = dueDate;
    this.createdAt = LocalDateTime.now();
    this.updatedAt = LocalDateTime.now();
  }
}
