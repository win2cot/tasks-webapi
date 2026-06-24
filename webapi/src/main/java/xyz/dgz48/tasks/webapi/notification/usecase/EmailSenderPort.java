package xyz.dgz48.tasks.webapi.notification.usecase;

/** メール送出の out port。実装は adapter.external(SES もしくはログ出力フォールバック)。 */
public interface EmailSenderPort {

  /**
   * メールを 1 通送出する。
   *
   * @param toAddress 宛先メールアドレス
   * @param subject 件名
   * @param body 本文(プレーンテキスト)
   * @throws EmailSendException 送出に失敗した場合
   */
  void send(String toAddress, String subject, String body);

  /** メール送出失敗を表す実行時例外。呼び出し側は受信者単位で捕捉し処理を継続する。 */
  class EmailSendException extends RuntimeException {
    public EmailSendException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
