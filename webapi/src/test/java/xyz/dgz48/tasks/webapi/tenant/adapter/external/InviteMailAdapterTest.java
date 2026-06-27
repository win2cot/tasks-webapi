package xyz.dgz48.tasks.webapi.tenant.adapter.external;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.dgz48.tasks.webapi.notification.usecase.EmailSenderPort;

@ExtendWith(MockitoExtension.class)
class InviteMailAdapterTest {

  @Mock EmailSenderPort emailSenderPort;

  @Test
  void sendInvitation_buildsAcceptLinkWithRawToken() {
    var adapter =
        new InviteMailAdapter(emailSenderPort, "https://app.example.com/invitations/accept");

    adapter.sendInvitation("invitee@example.com", "Acme テナント", "RAW-TOKEN-xyz");

    ArgumentCaptor<String> to = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
    verify(emailSenderPort).send(to.capture(), subject.capture(), body.capture());

    assertThat(to.getValue()).isEqualTo("invitee@example.com");
    assertThat(subject.getValue()).isNotBlank();
    assertThat(body.getValue())
        .contains("Acme テナント")
        .contains("https://app.example.com/invitations/accept?token=RAW-TOKEN-xyz");
  }
}
