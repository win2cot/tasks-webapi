package xyz.dgz48.tasks.webapi.tenant.usecase;

import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import xyz.dgz48.tasks.webapi.tenant.domain.SignupNotFoundException;
import xyz.dgz48.tasks.webapi.tenant.domain.SignupRequestStatus;
import xyz.dgz48.tasks.webapi.tenant.usecase.SignupRequestPort.SignupRequestView;

/**
 * 確認画面用のサインアップ要求照会(トークン非消費。ADR-0040 §3.3 {@code GET /api/signup/{token}})。
 *
 * <p>email と完了可否状態を返す。期限切れ / 使用済み / 失効は {@code displayStatus} に状態を載せて 200 で返し、画面側で案内分岐させる。トークンに
 * 対応する要求が無い場合のみ {@link SignupNotFoundException}(404)。
 */
@Service
@RequiredArgsConstructor
public class ViewSignupUseCase {

  private final SignupRequestPort signupRequestPort;
  private final Clock clock;

  public SignupDetail view(String rawToken) {
    String tokenHash = InviteTokenHasher.sha256Hex(rawToken);
    SignupRequestView req =
        signupRequestPort.findByTokenHash(tokenHash).orElseThrow(SignupNotFoundException::new);
    return new SignupDetail(req.email(), resolveDisplayStatus(req, LocalDateTime.now(clock)));
  }

  private static DisplayStatus resolveDisplayStatus(SignupRequestView req, LocalDateTime now) {
    if (req.status() == SignupRequestStatus.USED) {
      return DisplayStatus.USED;
    }
    if (req.status() == SignupRequestStatus.REVOKED) {
      return DisplayStatus.REVOKED;
    }
    if (req.expiresAt().isBefore(now)) {
      return DisplayStatus.EXPIRED;
    }
    return DisplayStatus.PENDING;
  }

  /** 確認画面に渡すサインアップ詳細。 */
  public record SignupDetail(String email, DisplayStatus status) {}

  /** 確認画面の状態(機微情報を含まない案内用)。PENDING のみ完了可能。 */
  public enum DisplayStatus {
    PENDING,
    EXPIRED,
    USED,
    REVOKED
  }
}
