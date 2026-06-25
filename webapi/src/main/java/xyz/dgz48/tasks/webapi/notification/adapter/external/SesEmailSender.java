package xyz.dgz48.tasks.webapi.notification.adapter.external;

import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.Body;
import software.amazon.awssdk.services.sesv2.model.Content;
import software.amazon.awssdk.services.sesv2.model.Destination;
import software.amazon.awssdk.services.sesv2.model.EmailContent;
import software.amazon.awssdk.services.sesv2.model.Message;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;
import xyz.dgz48.tasks.webapi.notification.usecase.EmailSenderPort;

/** Amazon SES v2 によるメール送出実装(基本設計書 §2 メール送信 = Amazon SES)。 */
@RequiredArgsConstructor
public class SesEmailSender implements EmailSenderPort {

  private static final String UTF_8 = "UTF-8";

  private final SesV2Client client;
  private final String fromAddress;

  @Override
  public void send(String toAddress, String subject, String body) {
    try {
      SendEmailRequest request =
          SendEmailRequest.builder()
              .fromEmailAddress(fromAddress)
              .destination(Destination.builder().toAddresses(toAddress).build())
              .content(
                  EmailContent.builder()
                      .simple(
                          Message.builder()
                              .subject(Content.builder().data(subject).charset(UTF_8).build())
                              .body(
                                  Body.builder()
                                      .text(Content.builder().data(body).charset(UTF_8).build())
                                      .build())
                              .build())
                      .build())
              .build();
      client.sendEmail(request);
    } catch (RuntimeException e) {
      throw new EmailSendException("SES 送出に失敗しました", e);
    }
  }
}
