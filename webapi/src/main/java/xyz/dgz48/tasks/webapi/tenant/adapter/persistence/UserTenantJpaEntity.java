package xyz.dgz48.tasks.webapi.tenant.adapter.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import xyz.dgz48.tasks.webapi.tenant.domain.TenantRole;
import xyz.dgz48.tasks.webapi.tenant.domain.UserTenantStatus;

@Entity
@Table(name = "user_tenants")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SuppressWarnings("NullAway.Init") // JPA requires no-args constructor; fields initialized via JPA
class UserTenantJpaEntity {

  @EmbeddedId private UserTenantId id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, columnDefinition = "ENUM('TENANT_ADMIN','MEMBER')")
  private TenantRole role;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, columnDefinition = "ENUM('ACTIVE','INVITED','DISABLED')")
  private UserTenantStatus status;

  @Column(name = "joined_at", nullable = false)
  private LocalDateTime joinedAt;

  UserTenantJpaEntity(Long userId, Long tenantId, TenantRole role, LocalDateTime joinedAt) {
    this.id = new UserTenantId(userId, tenantId);
    this.role = role;
    this.status = UserTenantStatus.ACTIVE;
    this.joinedAt = joinedAt;
  }

  void updateRole(TenantRole newRole) {
    this.role = newRole;
  }
}
