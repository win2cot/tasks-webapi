package xyz.dgz48.tasks.webapi.tenant.adapter.persistence;

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
import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import xyz.dgz48.tasks.webapi.tenant.domain.TenantPlan;
import xyz.dgz48.tasks.webapi.tenant.domain.TenantStatus;

@Entity
@Table(name = "tenants")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SuppressWarnings("NullAway.Init") // JPA requires no-args constructor; fields initialized via JPA
public class TenantJpaEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true, length = 50)
  private String code;

  @Column(nullable = false, length = 255)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, columnDefinition = "ENUM('FREE','STANDARD','ENTERPRISE')")
  private TenantPlan plan;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, columnDefinition = "ENUM('ACTIVE','SUSPENDED','DELETED')")
  private TenantStatus status;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  public TenantJpaEntity(String code, String name) {
    this.code = code;
    this.name = name;
    this.plan = TenantPlan.STANDARD;
    this.status = TenantStatus.ACTIVE;
  }

  @PrePersist
  void onCreate() {
    LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Tokyo"));
    this.createdAt = now;
    this.updatedAt = now;
  }

  @PreUpdate
  void onUpdate() {
    this.updatedAt = LocalDateTime.now(ZoneId.of("Asia/Tokyo"));
  }
}
