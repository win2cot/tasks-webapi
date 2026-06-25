package xyz.dgz48.tasks.webapi.notification.adapter.external;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;
import software.amazon.awssdk.services.sesv2.model.SendEmailResponse;
import software.amazon.awssdk.services.sesv2.model.SesV2Exception;
import xyz.dgz48.tasks.webapi.notification.usecase.EmailSenderPort.EmailSendException;

class SesEmailSenderTest {

  private final SesV2Client client = mock(SesV2Client.class);
  private final SesEmailSender sender = new SesEmailSender(client, "from@example.com");

  @Test
  void send_buildsSesRequestWithFromToSubjectBody() {
    when(client.sendEmail(any(SendEmailRequest.class)))
        .thenReturn(SendEmailResponse.builder().messageId("mid").build());

    sender.send("to@example.com", "件名", "本文");

    ArgumentCaptor<SendEmailRequest> captor = ArgumentCaptor.forClass(SendEmailRequest.class);
    verify(client).sendEmail(captor.capture());
    SendEmailRequest req = captor.getValue();
    assertThat(req.fromEmailAddress()).isEqualTo("from@example.com");
    assertThat(req.destination().toAddresses()).containsExactly("to@example.com");
    assertThat(req.content().simple().subject().data()).isEqualTo("件名");
    assertThat(req.content().simple().body().text().data()).isEqualTo("本文");
  }

  @Test
  void send_wrapsSesFailureInEmailSendException() {
    when(client.sendEmail(any(SendEmailRequest.class)))
        .thenThrow(SesV2Exception.builder().message("ses down").build());

    assertThatThrownBy(() -> sender.send("to@example.com", "s", "b"))
        .isInstanceOf(EmailSendException.class);
  }
}
