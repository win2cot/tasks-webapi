package xyz.dgz48.tasks.webapi.tenant.usecase;

/** テナントメンバーシップ検証ポート。 */
public interface TenantMembershipPort {

  /** 指定ユーザーが指定テナントの ACTIVE メンバーかを返す。 */
  boolean isActiveMember(Long userId, Long tenantId);
}
