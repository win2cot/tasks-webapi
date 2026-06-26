package xyz.dgz48.tasks.webapi.tenant.adapter.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import xyz.dgz48.tasks.webapi.tenant.domain.TenantPlan;
import xyz.dgz48.tasks.webapi.tenant.domain.TenantStatus;

@Entity
@Table(name = "tenants")
@EntityListeners(AuditingEntityListener.class)
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

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  public TenantJpaEntity(String code, String name) {
    this(code, name, TenantPlan.STANDARD);
  }

  public TenantJpaEntity(String code, String name, TenantPlan plan) {
    this.code = code;
    this.name = name;
    this.plan = plan;
    this.status = TenantStatus.ACTIVE;
  }

  public void updateName(String name) {
    this.name = name;
  }

  public void updateStatus(TenantStatus status) {
    this.status = status;
  }
}
