package xyz.dgz48.tasks.webapi.tenant.usecase;

import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import xyz.dgz48.tasks.webapi.tenant.domain.InvitationLoginRequiredException;
import xyz.dgz48.tasks.webapi.tenant.domain.InvitationNotAcceptableException;
import xyz.dgz48.tasks.webapi.tenant.domain.InvitationNotFoundException;
import xyz.dgz48.tasks.webapi.tenant.domain.InvitationStatus;
import xyz.dgz48.tasks.webapi.tenant.usecase.InvitationPort.InvitationView;
import xyz.dgz48.tasks.webapi.user.usecase.RegisterMemberCommand;
import xyz.dgz48.tasks.webapi.user.usecase.RegisterMemberUseCase;

/**
 * 招待受諾の確定(トークン消費。ADR-0040 §3.3 {@code POST /api/invitations/{token}/accept})。
 *
 * <p>分岐(ADR-0040 §3.2 / §3.3):
 *
 * <ul>
 *   <li><b>未登録</b>(users 行なし or pending correlation): 共有プリミティブ {@link RegisterMemberUseCase}
 *       で会員登録(profile + Keycloak credential)→ user_tenants 紐付け + USED 消費。
 *   <li><b>登録済み</b>(correlation 済の実アカウント): 会員登録は不要。{@link
 *       InvitationLoginRequiredException}(409)で「ログインして参加」 へ誘導する。認証済みユーザーの参加紐付けは後続フローで実装(本ユースケースを
 *       security に依存させると循環するため principal を扱わない)。
 * </ul>
 *
 * <p>トランザクション境界: 会員登録プリミティブは内部に project DB↔Keycloak のリモート境界を持つため本メソッドを {@code @Transactional} で
 * 束ねない(ADR-0040 §3.5)。原子性が必要な「user_tenants 紐付け + USED 消費」は {@link MembershipFinalizer} に分離して 1
 * トランザクション で確定する。会員登録は冪等(pending 行を再 upsert)なため、確定前に失敗しても同トークンの再受諾で回復できる。
 */
@Service
@RequiredArgsConstructor
public class AcceptInvitationUseCase {

  private final InvitationPort invitationPort;
  private final RegisterMemberUseCase registerMemberUseCase;
  private final MembershipFinalizer membershipFinalizer;
  private final Clock clock;

  /**
   * 招待を受諾する。
   *
   * @param rawToken 受諾トークン(平文)
   * @param command 会員登録用の profile + password(未登録時に使用)
   * @return 受諾結果(参加ユーザー id・テナント id)
   */
  public AcceptInvitationResult accept(String rawToken, AcceptInvitationCommand command) {
    String tokenHash = InviteTokenHasher.sha256Hex(rawToken);
    InvitationView inv =
        invitationPort.findByTokenHash(tokenHash).orElseThrow(InvitationNotFoundException::new);

    LocalDateTime now = LocalDateTime.now(clock);
    if (inv.status() != InvitationStatus.PENDING) {
      throw new InvitationNotAcceptableException(inv.status().name());
    }
    if (inv.expiresAt().isBefore(now)) {
      throw new InvitationNotAcceptableException("EXPIRED");
    }

    if (invitationPort.findRegisteredUserId(inv.email()).isPresent()) {
      // 登録済みアカウントはログインして参加(認証済み紐付けは後続フロー)。
      throw new InvitationLoginRequiredException();
    }

    Long userId =
        registerMemberUseCase.register(
            new RegisterMemberCommand(
                inv.email(),
                command.fullName(),
                command.fullNameKana(),
                command.departmentName(),
                command.password()));

    membershipFinalizer.linkAndConsume(userId, inv.tenantId(), inv.role(), inv.invitationId(), now);
    return new AcceptInvitationResult(userId, inv.tenantId());
  }

  /** 受諾時の会員登録入力(未登録ユーザーのみ使用。登録済みユーザーでは無視される)。 */
  public record AcceptInvitationCommand(
      String fullName, String fullNameKana, @Nullable String departmentName, String password) {}

  /** 受諾結果。 */
  public record AcceptInvitationResult(Long userId, Long tenantId) {}
}
