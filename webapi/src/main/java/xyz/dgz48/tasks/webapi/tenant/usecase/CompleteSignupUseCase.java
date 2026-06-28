package xyz.dgz48.tasks.webapi.tenant.usecase;

import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import xyz.dgz48.tasks.webapi.tenant.domain.SignupNotAcceptableException;
import xyz.dgz48.tasks.webapi.tenant.domain.SignupNotFoundException;
import xyz.dgz48.tasks.webapi.tenant.domain.SignupRequestStatus;
import xyz.dgz48.tasks.webapi.tenant.usecase.SignupRequestPort.SignupRequestView;
import xyz.dgz48.tasks.webapi.user.usecase.RegisterMemberCommand;
import xyz.dgz48.tasks.webapi.user.usecase.RegisterMemberUseCase;

/**
 * セルフサインアップの確定(トークン消費。ADR-0040 §3.3 {@code POST /api/signup/{token}/complete})。
 *
 * <p>確認トークンを検証し、共有プリミティブ {@link RegisterMemberUseCase} で会員登録(profile + Keycloak
 * credential)を行い、トークンを USED 消費する。テナント紐付けは行わない(登録後にログイン → 既存 {@code POST /api/tenants}
 * でテナント作成。ADR-0040 §3.5)。既に登録済みの email は {@link RegisterMemberUseCase} が {@code
 * UserAlreadyRegisteredException}(グローバルに 409)を投げる。
 *
 * <p>トランザクション境界(ADR-0040 §3.5): 会員登録プリミティブは内部に project DB↔Keycloak のリモート境界を持つため本メソッドを
 * {@code @Transactional} で束ねない。会員登録は冪等(pending 行を再 upsert)なため、USED 消費前に失敗しても同トークンの再 complete
 * で回復できる。Keycloak 失敗時はトークンを消費しない({@link RegisterMemberUseCase} が例外送出 → markUsed に到達しない)。
 */
@Service
@RequiredArgsConstructor
public class CompleteSignupUseCase {

  private final SignupRequestPort signupRequestPort;
  private final RegisterMemberUseCase registerMemberUseCase;
  private final Clock clock;

  /**
   * サインアップを完了する。
   *
   * @param rawToken 確認トークン(平文)
   * @param command 会員登録用の profile + password
   * @return 登録されたユーザー id
   */
  public Long complete(String rawToken, CompleteSignupCommand command) {
    String tokenHash = InviteTokenHasher.sha256Hex(rawToken);
    SignupRequestView req =
        signupRequestPort.findByTokenHash(tokenHash).orElseThrow(SignupNotFoundException::new);

    LocalDateTime now = LocalDateTime.now(clock);
    if (req.status() != SignupRequestStatus.PENDING) {
      throw new SignupNotAcceptableException(req.status().name());
    }
    if (req.expiresAt().isBefore(now)) {
      throw new SignupNotAcceptableException("EXPIRED");
    }

    Long userId =
        registerMemberUseCase.register(
            new RegisterMemberCommand(
                req.email(),
                command.fullName(),
                command.fullNameKana(),
                command.departmentName(),
                command.password()));

    signupRequestPort.markUsed(req.id(), now);
    return userId;
  }

  /** サインアップ完了時の会員登録入力。 */
  public record CompleteSignupCommand(
      String fullName, String fullNameKana, @Nullable String departmentName, String password) {}
}
