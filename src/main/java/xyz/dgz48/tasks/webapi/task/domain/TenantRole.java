package xyz.dgz48.tasks.webapi.task.domain;

/** テナントスコープのロール。基本設計書 §6.2 参照。 */
public enum TenantRole {
  /** Keycloak realm role APP_ADMIN を持つ SaaS 管理者。DB の user_tenants.role には保持しない。 */
  SAAS_ADMIN,
  TENANT_ADMIN,
  MEMBER;

  /** SaaS Admin または Tenant Admin であるかを返す。 */
  public boolean isAdmin() {
    return this == SAAS_ADMIN || this == TENANT_ADMIN;
  }
}
