package xyz.dgz48.tasks.webapi.task.adapter.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import xyz.dgz48.tasks.webapi.shared.adapter.persistence.TenantFilteredEntity;
import xyz.dgz48.tasks.webapi.task.domain.Priority;
import xyz.dgz48.tasks.webapi.task.domain.TaskStatus;
import xyz.dgz48.tasks.webapi.task.domain.Visibility;

@Entity
@Table(name = "tasks")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SuppressWarnings("NullAway.Init") // JPA requires no-args constructor; fields initialized via JPA
public class TaskJpaEntity extends TenantFilteredEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Version
  @Column(name = "version", nullable = false)
  private Long version;

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

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  @CreatedBy
  @Column(name = "created_by", nullable = false, updatable = false)
  private Long createdBy;

  @LastModifiedBy
  @Column(name = "updated_by", nullable = false)
  private Long updatedBy;

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

  public TaskJpaEntity(
      Long tenantId,
      Long ownerId,
      String title,
      @Nullable String description,
      TaskStatus status,
      Priority priority,
      Visibility visibility,
      @Nullable Long assigneeId,
      LocalDate dueDate) {
    this.tenantId = tenantId;
    this.ownerId = ownerId;
    this.title = title;
    this.description = description;
    this.status = status;
    this.priority = priority;
    this.visibility = visibility;
    this.assigneeId = assigneeId;
    this.dueDate = dueDate;
  }

  public void updateStatus(TaskStatus newStatus, @Nullable LocalDateTime newCompletedAt) {
    this.status = newStatus;
    this.completedAt = newCompletedAt;
  }

  /** PATCH 更新時のフィールド同期。Task ドメインの全可変フィールドを反映する。 */
  public void updateFields(
      String title,
      @Nullable String description,
      Priority priority,
      @Nullable Long assigneeId,
      LocalDate dueDate,
      TaskStatus status,
      @Nullable LocalDateTime completedAt,
      Visibility visibility) {
    this.title = title;
    this.description = description;
    this.priority = priority;
    this.assigneeId = assigneeId;
    this.dueDate = dueDate;
    this.status = status;
    this.completedAt = completedAt;
    this.visibility = visibility;
  }

  public void markDeleted(LocalDateTime deletedAt) {
    this.deletedAt = deletedAt;
  }
}
