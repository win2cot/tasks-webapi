package xyz.dgz48.tasks.webapi.tenant.adapter.external;

import lombok.RequiredArgsConstructor;
import xyz.dgz48.tasks.webapi.notification.usecase.EmailSenderPort;
import xyz.dgz48.tasks.webapi.tenant.usecase.SignupMailPort;

/**
 * {@link SignupMailPort} の実装。確認リンク付きのサインアップ確認メールを {@link EmailSenderPort}(notification feature の
 * SES / ログ実装)経由で送信する(ADR-0040 §3.3 / §3.4)。
 *
 * <p>平文トークンはメール本文(確認 URL)にのみ載せる。DB にはハッシュのみが保存される。
 */
@RequiredArgsConstructor
public class SignupMailAdapter implements SignupMailPort {

  private static final String SUBJECT = "【Tasks】アカウント登録の確認";

  private final EmailSenderPort emailSenderPort;
  private final String confirmUrlBase;

  @Override
  public void sendConfirmation(String email, String rawToken) {
    String confirmUrl = confirmUrlBase + "?token=" + rawToken;
    String body =
        """
        Tasks へのアカウント登録の確認です。

        以下のリンクから登録を完了してください(有効期限: 発行から24時間):
        %s

        このメールに心当たりがない場合は破棄してください。リンクを操作しない限りアカウントは作成されません。
        """
            .formatted(confirmUrl);
    emailSenderPort.send(email, SUBJECT, body);
  }
}
