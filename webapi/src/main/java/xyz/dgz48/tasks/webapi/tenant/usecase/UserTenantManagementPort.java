package xyz.dgz48.tasks.webapi.tenant.usecase;

import xyz.dgz48.tasks.webapi.tenant.domain.TenantRole;
import xyz.dgz48.tasks.webapi.tenant.domain.UserTenant;

/** テナントメンバー追加・削除・ロール変更の永続化ポート。 */
public interface UserTenantManagementPort {

  /** 指定ユーザーが既に登録済み(status 問わず)か。 */
  boolean existsMember(Long userId, Long tenantId);

  /** メンバーを追加し、登録済み UserTenant を返す。 */
  UserTenant addMember(Long userId, Long tenantId, TenantRole role);

  /** ACTIVE メンバーを削除する。成功時 {@code true}、ACTIVE メンバーが存在しない場合は {@code false}。 */
  boolean removeActiveMember(Long userId, Long tenantId);

  /** ACTIVE メンバーのロールを変更する。成功時 {@code true}、ACTIVE メンバー不在の場合は {@code false}。 */
  boolean changeActiveMemberRole(Long userId, Long tenantId, TenantRole newRole);
}
