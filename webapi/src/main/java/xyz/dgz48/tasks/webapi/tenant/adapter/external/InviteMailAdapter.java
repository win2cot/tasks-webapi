package xyz.dgz48.tasks.webapi.tenant.adapter.external;

import lombok.RequiredArgsConstructor;
import xyz.dgz48.tasks.webapi.notification.usecase.EmailSenderPort;
import xyz.dgz48.tasks.webapi.tenant.usecase.InviteMailPort;

/**
 * {@link InviteMailPort} の実装。受諾リンク付き招待メールを {@link EmailSenderPort}(notification feature の SES /
 * ログ実装)経由で送信する(ADR-0017)。
 *
 * <p>平文トークンはメール本文(受諾 URL)にのみ載せる。DB にはハッシュのみが保存される。
 */
@RequiredArgsConstructor
public class InviteMailAdapter implements InviteMailPort {

  private static final String SUBJECT = "【Tasks】テナントへの招待";

  private final EmailSenderPort emailSenderPort;
  private final String acceptUrlBase;

  @Override
  public void sendInvitation(String email, String tenantName, String rawToken) {
    String acceptUrl = acceptUrlBase + "?token=" + rawToken;
    String body =
        """
        %s への招待が届いています。

        以下のリンクから招待を承諾してください(有効期限: 発行から7日間):
        %s

        このメールに心当たりがない場合は破棄してください。
        """
            .formatted(tenantName, acceptUrl);
    emailSenderPort.send(email, SUBJECT, body);
  }
}
