package xyz.dgz48.tasks.webapi.tenant.usecase;

import java.time.LocalDateTime;
import java.util.Optional;
import xyz.dgz48.tasks.webapi.tenant.domain.InvitationStatus;
import xyz.dgz48.tasks.webapi.tenant.domain.TenantRole;

/** 招待(invitations)の永続化・照会ポート(ADR-0017 / ADR-0040)。 */
public interface InvitationPort {

  /** 招待先 email に対応するユーザーが、既に当該テナントの user_tenants に登録済み(status 問わず)か。 */
  boolean isAlreadyMember(Long tenantId, String email);

  /** 当該テナント・email の PENDING 招待をすべて REVOKED にする(再送時の旧トークン失効。ADR-0017 §3.1)。 */
  void revokePending(Long tenantId, String email);

  /** PENDING 招待を 1 件作成する。 */
  void save(NewInvitation invitation);

  /** テナント名(招待メール文面・受諾画面用)。 */
  Optional<String> findTenantName(Long tenantId);

  /**
   * token_hash で招待 1 件を引く(受諾フロー用)。token_hash は全テナント一意のため tenantFilter 非依存で照会する(受諾は X-Tenant-Id
   * 未選択・未認証で到達するため TenantContext は null。ADR-0040 §3.3)。
   */
  Optional<InvitationView> findByTokenHash(String tokenHash);

  /**
   * email に対応する「登録済み(correlation 済の実アカウント)」ユーザー id を返す。pending correlation 行(会員登録のみで初回ログイン未了)は
   * 未登録扱いとし空を返す(ADR-0040 §3.2 / §3.3 の「登録済みならログインして参加」判定用)。
   */
  Optional<Long> findRegisteredUserId(String email);

  /** 招待を USED に遷移させ consumed_at を記録する(受諾確定。ADR-0040 §3.3)。 */
  void markUsed(Long invitationId, LocalDateTime consumedAt);

  /** 新規 PENDING 招待の永続化パラメータ。 */
  record NewInvitation(
      Long tenantId,
      String email,
      String tokenHash,
      TenantRole role,
      LocalDateTime expiresAt,
      Long invitedBy,
      LocalDateTime createdAt) {}

  /** 受諾フローで参照する招待ビュー(token_hash 引きの結果)。 */
  record InvitationView(
      Long invitationId,
      Long tenantId,
      String email,
      TenantRole role,
      InvitationStatus status,
      LocalDateTime expiresAt) {}
}
