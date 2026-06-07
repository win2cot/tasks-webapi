package xyz.dgz48.tasks.webapi.task.adapter.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import xyz.dgz48.tasks.webapi.shared.adapter.persistence.TenantFilteredEntity;

@Entity
@Table(name = "task_stakeholders")
@IdClass(TaskStakeholderJpaEntityId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SuppressWarnings("NullAway.Init") // JPA requires no-args constructor; fields initialized via JPA
class TaskStakeholderJpaEntity extends TenantFilteredEntity {

  @Id
  @Column(name = "task_id", nullable = false)
  private Long taskId;

  @Id
  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "tenant_id", nullable = false)
  private Long tenantId;

  @Column(name = "added_by", nullable = false)
  private Long addedBy;

  @Column(name = "added_at", nullable = false)
  private LocalDateTime addedAt;
}
