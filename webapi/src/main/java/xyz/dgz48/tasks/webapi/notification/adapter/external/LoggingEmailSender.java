package xyz.dgz48.tasks.webapi.notification.adapter.external;

import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.dgz48.tasks.webapi.notification.usecase.EmailSenderPort;

/**
 * SES 未設定環境(ローカル開発・テスト)向けのフォールバック送出実装。
 *
 * <p>実際には送出せず、送出が試みられた事実のみをログに残す。PII 保護のため宛先はマスクし、本文・件名は出力しない。
 */
public class LoggingEmailSender implements EmailSenderPort {

  private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

  @Override
  public void send(String toAddress, String subject, String body) {
    log.info("メール送出(ログフォールバック、実送出なし) to={}", mask(toAddress));
  }

  /** メールアドレスをマスクする(先頭1文字 + "***@" + ドメイン)。 */
  private static String mask(String address) {
    int at = address.indexOf('@');
    if (at <= 0) {
      return "***";
    }
    String domain = address.substring(at + 1).toLowerCase(Locale.ROOT);
    return address.charAt(0) + "***@" + domain;
  }
}
