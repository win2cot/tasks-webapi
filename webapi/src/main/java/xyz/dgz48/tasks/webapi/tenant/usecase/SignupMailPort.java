package xyz.dgz48.tasks.webapi.tenant.usecase;

/** サインアップ確認メール送信ポート(ADR-0040 §3.3 / §3.4)。確認リンクの受信そのものが email 到達証明。 */
public interface SignupMailPort {

  /** 確認リンク付きのサインアップ確認メールを送る。{@code rawToken} は平文(メール本文のみ。DB 非保存)。 */
  void sendConfirmation(String email, String rawToken);
}
