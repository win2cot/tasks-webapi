package xyz.dgz48.tasks.webapi.tenant.domain;

/**
 * テナントスコープの認可判定用ロール。
 *
 * <p>DB の {@code user_tenants.role}({@code TENANT_ADMIN} / {@code MEMBER}) に Keycloak realm role
 * {@code APP_ADMIN} を合成した 3 値の enum。{@code SAAS_ADMIN} は DB には保持せず Keycloak が管理する。詳細は基本設計書 §6.2 参照。
 */
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
