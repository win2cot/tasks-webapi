package xyz.dgz48.tasks.webapi.tenant.usecase;

import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import xyz.dgz48.tasks.webapi.tenant.domain.InvitationNotFoundException;
import xyz.dgz48.tasks.webapi.tenant.domain.InvitationStatus;
import xyz.dgz48.tasks.webapi.tenant.domain.TenantRole;
import xyz.dgz48.tasks.webapi.tenant.usecase.InvitationPort.InvitationView;

/**
 * 受諾画面用の招待照会(トークン非消費。ADR-0040 §3.3 {@code GET /api/invitations/{token}})。
 *
 * <p>テナント名・email・ロール・受諾可否状態を返す。期限切れ / 使用済み / 失効は {@code displayStatus} に状態を載せて 200 で返し(404 にしない)、
 * 画面側で案内分岐させる。トークンに対応する招待が無い場合のみ {@link InvitationNotFoundException}(404)。
 */
@Service
@RequiredArgsConstructor
public class ViewInvitationUseCase {

  private final InvitationPort invitationPort;
  private final Clock clock;

  public InvitationDetail view(String rawToken) {
    String tokenHash = InviteTokenHasher.sha256Hex(rawToken);
    InvitationView inv =
        invitationPort.findByTokenHash(tokenHash).orElseThrow(InvitationNotFoundException::new);

    DisplayStatus display = resolveDisplayStatus(inv, LocalDateTime.now(clock));
    String tenantName = invitationPort.findTenantName(inv.tenantId()).orElse("テナント");
    boolean alreadyRegistered = invitationPort.findRegisteredUserId(inv.email()).isPresent();

    return new InvitationDetail(inv.email(), tenantName, inv.role(), display, alreadyRegistered);
  }

  private static DisplayStatus resolveDisplayStatus(InvitationView inv, LocalDateTime now) {
    if (inv.status() == InvitationStatus.USED) {
      return DisplayStatus.USED;
    }
    if (inv.status() == InvitationStatus.REVOKED) {
      return DisplayStatus.REVOKED;
    }
    if (inv.expiresAt().isBefore(now)) {
      return DisplayStatus.EXPIRED;
    }
    return DisplayStatus.PENDING;
  }

  /** 受諾画面に渡す招待詳細。 */
  public record InvitationDetail(
      String email,
      String tenantName,
      TenantRole role,
      DisplayStatus status,
      boolean alreadyRegistered) {}

  /** 受諾画面の状態(機微情報を含まない案内用)。PENDING のみ受諾可能。 */
  public enum DisplayStatus {
    PENDING,
    EXPIRED,
    USED,
    REVOKED
  }
}
