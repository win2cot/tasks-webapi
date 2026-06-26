package xyz.dgz48.tasks.webapi.notification.adapter.persistence;

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

/**
 * {@code user_notification_settings} テーブルの JPA エンティティ(S-10)。
 *
 * <p>{@code tenant_id BIGINT NOT NULL} を持つ業務テーブルのため {@link TenantFilteredEntity} を継承し、Hibernate
 * Filter "tenantFilter"(ADR-0010)が SELECT にテナント条件を自動付与する。複合主キーは (user_id, tenant_id)。
 */
@Entity
@Table(name = "user_notification_settings")
@IdClass(NotificationSettingsId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SuppressWarnings("NullAway.Init") // JPA requires no-args constructor; fields initialized via JPA
class NotificationSettingsJpaEntity extends TenantFilteredEntity {

  @Id
  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Id
  @Column(name = "tenant_id", nullable = false)
  private Long tenantId;

  @Column(name = "email_due_today", nullable = false)
  private boolean emailDueToday;

  @Column(name = "email_overdue", nullable = false)
  private boolean emailOverdue;

  @Column(name = "email_stakeholder", nullable = false)
  private boolean emailStakeholder;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  NotificationSettingsJpaEntity(
      Long userId,
      Long tenantId,
      boolean emailDueToday,
      boolean emailOverdue,
      boolean emailStakeholder,
      LocalDateTime updatedAt) {
    this.userId = userId;
    this.tenantId = tenantId;
    this.emailDueToday = emailDueToday;
    this.emailOverdue = emailOverdue;
    this.emailStakeholder = emailStakeholder;
    this.updatedAt = updatedAt;
  }

  void update(
      boolean emailDueToday,
      boolean emailOverdue,
      boolean emailStakeholder,
      LocalDateTime updatedAt) {
    this.emailDueToday = emailDueToday;
    this.emailOverdue = emailOverdue;
    this.emailStakeholder = emailStakeholder;
    this.updatedAt = updatedAt;
  }
}
