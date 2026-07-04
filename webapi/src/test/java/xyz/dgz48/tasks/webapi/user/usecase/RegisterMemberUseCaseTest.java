package xyz.dgz48.tasks.webapi.user.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RegisterMemberUseCaseTest {

  @Mock UserRegistrationPort userRegistrationPort;
  @Mock CredentialProvisioningPort credentialProvisioningPort;

  @InjectMocks RegisterMemberUseCase useCase;

  @Test
  void upsertsUserThenProvisionsCredentialInOrder() {
    var cmd = new RegisterMemberCommand("a@example.com", "氏名", "シメイ", null, "pw");
    when(userRegistrationPort.upsertPendingMember("a@example.com", "氏名", "シメイ", null))
        .thenReturn(42L);

    Long id = useCase.register(cmd);

    assertThat(id).isEqualTo(42L);
    InOrder ordered = inOrder(userRegistrationPort, credentialProvisioningPort);
    ordered.verify(userRegistrationPort).upsertPendingMember("a@example.com", "氏名", "シメイ", null);
    ordered.verify(credentialProvisioningPort).provisionCredential("a@example.com", "氏名", "pw");
  }

  @Test
  void propagatesCredentialFailureAfterUserRowIsWritten() {
    var cmd = new RegisterMemberCommand("a@example.com", "氏名", "シメイ", "営業部", "pw");
    when(userRegistrationPort.upsertPendingMember(any(), any(), any(), any())).thenReturn(1L);
    doThrow(new CredentialProvisioningException("keycloak down"))
        .when(credentialProvisioningPort)
        .provisionCredential(any(), any(), any());

    assertThatThrownBy(() -> useCase.register(cmd))
        .isInstanceOf(CredentialProvisioningException.class);
    // project 先 → Keycloak 後: ① の users 行 upsert は実行済み(行は残る=未完了状態)
    verify(userRegistrationPort).upsertPendingMember("a@example.com", "氏名", "シメイ", "営業部");
  }
}
