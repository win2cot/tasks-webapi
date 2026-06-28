package xyz.dgz48.tasks.webapi.tenant.usecase;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import xyz.dgz48.tasks.webapi.notification.usecase.EmailSenderPort.EmailSendException;

/**
 * セルフサインアップ要求(ADR-0040 §3.3 {@code POST /api/signup/request})。double opt-in の前段。
 *
 * <p>email のみを受け取り、自前 one-time トークンを発行して PENDING の signup_requests を作成し、確認リンクを SES
 * で送る。確認リンクの受信そのものが email 到達証明(§3.4)。**email の存在有無で分岐せず常に同一の結果**(列挙耐性)。確認リンクを操作して complete しない限り
 * users 行も credential も作られない。
 *
 * <p>メール送信失敗は列挙耐性・UX のため呼び出し側に伝播させず、構造化ログに記録して握りつぶす(レスポンスは常に同一)。
 */
@Service
@RequiredArgsConstructor
public class RequestSignupUseCase {

  private static final Logger log = LoggerFactory.getLogger(RequestSignupUseCase.class);

  /** 確認トークンの有効期間。 */
  private static final Duration TOKEN_TTL = Duration.ofHours(24);

  private final SignupRequestPort signupRequestPort;
  private final SignupMailPort signupMailPort;
  private final InviteTokenGenerator tokenGenerator;
  private final Clock clock;

  public void request(String email) {
    String rawToken = tokenGenerator.generate();
    String tokenHash = InviteTokenHasher.sha256Hex(rawToken);
    LocalDateTime now = LocalDateTime.now(clock);

    signupRequestPort.replacePending(email, tokenHash, now.plus(TOKEN_TTL), now);

    try {
      signupMailPort.sendConfirmation(email, rawToken);
    } catch (EmailSendException e) {
      // 列挙耐性のためレスポンスは常に同一。送信失敗はログのみ(PII は EmailSenderPort 側でマスク)。
      log.warn("サインアップ確認メールの送信に失敗しました", e);
    }
  }
}
