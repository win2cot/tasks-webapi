package xyz.dgz48.tasks.webapi.audit.adapter.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * audit_logs テーブルの JPA エンティティ。
 *
 * <p>{@code tenant_id} が nullable(システム横断イベントは NULL)であり、テナント範囲を超えた参照が必要なため {@code
 * TenantFilteredEntity} を継承しない(ADR-0010 §6.1)。
 */
@Entity
@Table(name = "audit_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SuppressWarnings("NullAway.Init")
class AuditLogJpaEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Nullable
  @Column(name = "tenant_id")
  private Long tenantId;

  @Nullable
  @Column(name = "user_id")
  private Long userId;

  @Column(name = "action", nullable = false, length = 50)
  private String action;

  @Nullable
  @Column(name = "entity_type", length = 50)
  private String entityType;

  @Nullable
  @Column(name = "entity_id")
  private Long entityId;

  @Nullable
  @Column(name = "detail", columnDefinition = "JSON")
  private String detail;

  @Nullable
  @Column(name = "ip_address", length = 45)
  private String ipAddress;

  @Column(name = "hash_chain", nullable = false, length = 64)
  private String hashChain;

  @Column(name = "chain_seq", nullable = false)
  private Long chainSeq;

  @Column(name = "hash_key_id", nullable = false, length = 32)
  private String hashKeyId;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  AuditLogJpaEntity(
      @Nullable Long tenantId,
      @Nullable Long userId,
      String action,
      @Nullable String detail,
      long chainSeq,
      String hashChain,
      String hashKeyId,
      LocalDateTime createdAt) {
    this.tenantId = tenantId;
    this.userId = userId;
    this.action = action;
    this.detail = detail;
    this.chainSeq = chainSeq;
    this.hashChain = hashChain;
    this.hashKeyId = hashKeyId;
    this.createdAt = createdAt;
  }
}
