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
import xyz.dgz48.tasks.webapi.tenant.domain.SignupRequestStatus;

/**
 * サインアップ要求(signup_requests)JPA エンティティ。テナント未所属の登録を担うため tenant_id を持たず、Hibernate tenantFilter
 * 対象外(ADR-0040 §3.3)。
 */
@Entity
@Table(name = "signup_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SuppressWarnings("NullAway.Init") // JPA requires no-args constructor; fields initialized via JPA
class SignupRequestJpaEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 255)
  private String email;

  @Column(name = "token_hash", nullable = false, length = 64)
  private String tokenHash;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, columnDefinition = "ENUM('PENDING','USED','REVOKED')")
  private SignupRequestStatus status;

  @Column(name = "expires_at", nullable = false)
  private LocalDateTime expiresAt;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @Nullable
  @Column(name = "consumed_at")
  private LocalDateTime consumedAt;

  /** 新規 PENDING サインアップ要求を生成する。 */
  SignupRequestJpaEntity(
      String email, String tokenHash, LocalDateTime expiresAt, LocalDateTime createdAt) {
    this.email = email;
    this.tokenHash = tokenHash;
    this.status = SignupRequestStatus.PENDING;
    this.expiresAt = expiresAt;
    this.createdAt = createdAt;
  }

  /** PENDING を REVOKED に遷移させる(再要求時の旧トークン失効)。 */
  void revoke() {
    this.status = SignupRequestStatus.REVOKED;
  }

  /** PENDING を USED に遷移させ consumed_at を記録する(complete 確定)。 */
  void markUsed(LocalDateTime consumedAt) {
    this.status = SignupRequestStatus.USED;
    this.consumedAt = consumedAt;
  }
}
