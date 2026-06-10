package xyz.dgz48.tasks.webapi.tenant.domain;

import java.time.LocalDateTime;

/** テナントドメインモデル(JPA 非依存 POJO)。 */
public class Tenant {

  private final Long id;
  private final String code;
  private String name;
  private final TenantPlan plan;
  private TenantStatus status;
  private final LocalDateTime createdAt;
  private final LocalDateTime updatedAt;
  private final long userCount;
  private final long taskCount;

  public Tenant(
      Long id,
      String code,
      String name,
      TenantPlan plan,
      TenantStatus status,
      LocalDateTime createdAt,
      LocalDateTime updatedAt,
      long userCount,
      long taskCount) {
    this.id = id;
    this.code = code;
    this.name = name;
    this.plan = plan;
    this.status = status;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
    this.userCount = userCount;
    this.taskCount = taskCount;
  }

  public Long getId() {
    return id;
  }

  public String getCode() {
    return code;
  }

  public String getName() {
    return name;
  }

  public TenantPlan getPlan() {
    return plan;
  }

  public TenantStatus getStatus() {
    return status;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }

  public long getUserCount() {
    return userCount;
  }

  public long getTaskCount() {
    return taskCount;
  }
}
