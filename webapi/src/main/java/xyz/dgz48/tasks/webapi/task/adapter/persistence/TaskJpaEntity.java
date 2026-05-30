package xyz.dgz48.tasks.webapi.task.adapter.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import xyz.dgz48.tasks.webapi.task.domain.Priority;
import xyz.dgz48.tasks.webapi.task.domain.TaskStatus;
import xyz.dgz48.tasks.webapi.task.domain.Visibility;

@Entity
@Table(name = "tasks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SuppressWarnings("NullAway.Init") // JPA requires no-args constructor; fields initialized via JPA
public class TaskJpaEntity {

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
  @Column(nullable = false, columnDefinition = "ENUM('NOT_STARTED','IN_PROGRESS','DONE','ON_HOLD')")
  private TaskStatus status;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, columnDefinition = "ENUM('HIGH','MEDIUM','LOW')")
  private Priority priority;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, columnDefinition = "ENUM('TENANT','STAKEHOLDERS','PRIVATE')")
  private Visibility visibility;

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

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  public TaskJpaEntity(
      Long tenantId,
      Long ownerId,
      String title,
      @Nullable String description,
      TaskStatus status,
      Priority priority,
      LocalDate dueDate) {
    this.tenantId = tenantId;
    this.ownerId = ownerId;
    this.title = title;
    this.description = description;
    this.status = status;
    this.priority = priority;
    this.visibility = Visibility.TENANT;
    this.dueDate = dueDate;
  }

  @PrePersist
  void onCreate() {
    LocalDateTime now = LocalDateTime.now();
    this.createdAt = now;
    this.updatedAt = now;
  }

  @PreUpdate
  void onUpdate() {
    this.updatedAt = LocalDateTime.now();
  }
}
