package xyz.dgz48.tasks.webapi.tenant.usecase;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.dgz48.tasks.webapi.tenant.domain.TenantRole;
import xyz.dgz48.tasks.webapi.tenant.domain.UserAlreadyMemberException;

/**
 * ユーザー招待ユースケース(A-09 / ADR-0017 選択肢 B)。
 *
 * <p>現在のテナント(X-Tenant-Id 駆動)に対して email を招待する。既に user_tenants に登録済みの email は重複招待として 409 ({@link
 * UserAlreadyMemberException})。再送のため同一テナント・email の PENDING 招待は失効(REVOKED)させてから、新しい SecureRandom
 * トークンを発行し、ハッシュのみを永続化して SES で招待メールを送る。
 *
 * <p>受諾(トークン消費)・user_tenants 紐付けは受諾画面フロー(別 Issue)で実装する。
 */
@Service
@RequiredArgsConstructor
public class InviteUserUseCase {

  /** 招待トークンの有効期間(ADR-0017 §3.1)。 */
  private static final Duration TOKEN_TTL = Duration.ofDays(7);

  private final InvitationPort invitationPort;
  private final InviteTokenGenerator tokenGenerator;
  private final InviteMailPort inviteMailPort;
  private final Clock clock;

  @Transactional
  public void execute(Long tenantId, Long callerId, String email, TenantRole role) {
    if (invitationPort.isAlreadyMember(tenantId, email)) {
      throw new UserAlreadyMemberException(email, tenantId);
    }

    invitationPort.revokePending(tenantId, email);

    String rawToken = tokenGenerator.generate();
    String tokenHash = InviteTokenHasher.sha256Hex(rawToken);
    LocalDateTime now = LocalDateTime.now(clock);
    LocalDateTime expiresAt = now.plus(TOKEN_TTL);

    invitationPort.save(
        new InvitationPort.NewInvitation(
            tenantId, email, tokenHash, role, expiresAt, callerId, now));

    String tenantName = invitationPort.findTenantName(tenantId).orElse("テナント");
    inviteMailPort.sendInvitation(email, tenantName, rawToken);
  }
}
