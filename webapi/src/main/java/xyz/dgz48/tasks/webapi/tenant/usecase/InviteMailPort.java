package xyz.dgz48.tasks.webapi.tenant.usecase;

/** 招待メール送信ポート(ADR-0017)。平文トークンを受け取り、受諾リンクを含むメールを送信する。 */
public interface InviteMailPort {

  /**
   * 招待メールを送信する。
   *
   * @param email 招待先メールアドレス
   * @param tenantName 招待元テナント名(文面に表示)
   * @param rawToken 平文トークン(受諾 URL に埋め込む。DB には保存されない)
   */
  void sendInvitation(String email, String tenantName, String rawToken);
}
