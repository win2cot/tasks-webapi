package xyz.dgz48.tasks.webapi.security.adapter.persistence;

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

/**
 * SaaS Admin の正本テーブル entity。tenant_id を持たない例外テーブルのため {@code TenantFilteredEntity} を継承しない
 * (ADR-0010)。
 */
@Entity
@Table(name = "app_admin_users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SuppressWarnings("NullAway.Init") // JPA requires no-args constructor; fields initialized via JPA
public class AppAdminUserJpaEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "oidc_sub", nullable = false, unique = true, length = 255)
  private String oidcSub;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  public AppAdminUserJpaEntity(String oidcSub, LocalDateTime createdAt) {
    this.oidcSub = oidcSub;
    this.createdAt = createdAt;
  }
}
