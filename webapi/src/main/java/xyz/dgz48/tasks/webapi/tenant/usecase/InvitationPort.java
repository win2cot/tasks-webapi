package xyz.dgz48.tasks.webapi.tenant.usecase;

import java.time.LocalDateTime;
import java.util.Optional;
import xyz.dgz48.tasks.webapi.tenant.domain.TenantRole;

/** 招待(invitations)の永続化・照会ポート(ADR-0017)。 */
public interface InvitationPort {

  /** 招待先 email に対応するユーザーが、既に当該テナントの user_tenants に登録済み(status 問わず)か。 */
  boolean isAlreadyMember(Long tenantId, String email);

  /** 当該テナント・email の PENDING 招待をすべて REVOKED にする(再送時の旧トークン失効。ADR-0017 §3.1)。 */
  void revokePending(Long tenantId, String email);

  /** PENDING 招待を 1 件作成する。 */
  void save(NewInvitation invitation);

  /** テナント名(招待メール文面用)。 */
  Optional<String> findTenantName(Long tenantId);

  /** 新規 PENDING 招待の永続化パラメータ。 */
  record NewInvitation(
      Long tenantId,
      String email,
      String tokenHash,
      TenantRole role,
      LocalDateTime expiresAt,
      Long invitedBy,
      LocalDateTime createdAt) {}
}
