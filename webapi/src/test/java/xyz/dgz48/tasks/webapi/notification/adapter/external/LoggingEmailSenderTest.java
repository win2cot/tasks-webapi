package xyz.dgz48.tasks.webapi.notification.adapter.external;

import org.junit.jupiter.api.Test;

/** ログフォールバック送出が例外を投げずに完了することの確認(マスク処理は内部実装)。 */
class LoggingEmailSenderTest {

  private final LoggingEmailSender sender = new LoggingEmailSender();

  @Test
  void send_doesNotThrow_forNormalAddress() {
    sender.send("user@example.com", "件名", "本文");
  }

  @Test
  void send_doesNotThrow_forAddressWithoutAtSign() {
    sender.send("invalid-address", "件名", "本文");
  }
}
