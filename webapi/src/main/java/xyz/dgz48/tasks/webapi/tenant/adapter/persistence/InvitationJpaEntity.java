package xyz.dgz48.tasks.webapi.tenant.adapter.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import xyz.dgz48.tasks.webapi.shared.adapter.persistence.TenantFilteredEntity;
import xyz.dgz48.tasks.webapi.tenant.domain.InvitationStatus;
import xyz.dgz48.tasks.webapi.tenant.domain.TenantRole;

/** 招待(invitations)JPA エンティティ。業務テーブルのため Hibernate tenantFilter 対象(ADR-0010 / ADR-0017)。 */
@Entity
@Table(name = "invitations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SuppressWarnings("NullAway.Init") // JPA requires no-args constructor; fields initialized via JPA
class InvitationJpaEntity extends TenantFilteredEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "tenant_id", nullable = false)
  private Long tenantId;

  @Column(nullable = false, length = 255)
  private String email;

  @Column(name = "token_hash", nullable = false, length = 64)
  private String tokenHash;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, columnDefinition = "ENUM('PENDING','USED','REVOKED')")
  private InvitationStatus status;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, columnDefinition = "ENUM('TENANT_ADMIN','MEMBER')")
  private TenantRole role;

  @Column(name = "expires_at", nullable = false)
  private LocalDateTime expiresAt;

  @Column(name = "invited_by", nullable = false)
  private Long invitedBy;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @Nullable
  @Column(name = "consumed_at")
  private LocalDateTime consumedAt;

  /** 新規 PENDING 招待を生成する。 */
  InvitationJpaEntity(
      Long tenantId,
      String email,
      String tokenHash,
      TenantRole role,
      LocalDateTime expiresAt,
      Long invitedBy,
      LocalDateTime createdAt) {
    this.tenantId = tenantId;
    this.email = email;
    this.tokenHash = tokenHash;
    this.status = InvitationStatus.PENDING;
    this.role = role;
    this.expiresAt = expiresAt;
    this.invitedBy = invitedBy;
    this.createdAt = createdAt;
  }

  /** PENDING を REVOKED に遷移させる(再送・取消)。 */
  void revoke() {
    this.status = InvitationStatus.REVOKED;
  }

  /** PENDING を USED に遷移させ consumed_at を記録する(受諾確定。ADR-0040 §3.3)。 */
  void markUsed(LocalDateTime consumedAt) {
    this.status = InvitationStatus.USED;
    this.consumedAt = consumedAt;
  }
}
