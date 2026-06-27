package xyz.dgz48.tasks.webapi.tenant.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.dgz48.tasks.webapi.tenant.domain.TenantRole;
import xyz.dgz48.tasks.webapi.tenant.domain.UserAlreadyMemberException;

@ExtendWith(MockitoExtension.class)
class InviteUserUseCaseTest {

  private static final Long TENANT_ID = 10L;
  private static final Long CALLER_ID = 1L;
  private static final String EMAIL = "invitee@example.com";
  private static final String RAW_TOKEN = "RAW-TOKEN-abc123";

  private static final Clock FIXED_CLOCK =
      Clock.fixed(Instant.parse("2026-06-27T00:00:00Z"), ZoneOffset.UTC);

  @Mock InvitationPort invitationPort;
  @Mock InviteTokenGenerator tokenGenerator;
  @Mock InviteMailPort inviteMailPort;

  private InviteUserUseCase useCase() {
    return new InviteUserUseCase(invitationPort, tokenGenerator, inviteMailPort, FIXED_CLOCK);
  }

  @Test
  void execute_createsInvitationAndSendsMail_whenNotMember() {
    when(invitationPort.isAlreadyMember(TENANT_ID, EMAIL)).thenReturn(false);
    when(tokenGenerator.generate()).thenReturn(RAW_TOKEN);
    when(invitationPort.findTenantName(TENANT_ID)).thenReturn(Optional.of("Acme"));

    useCase().execute(TENANT_ID, CALLER_ID, EMAIL, TenantRole.MEMBER);

    // 再送のため旧 PENDING を失効
    verify(invitationPort).revokePending(TENANT_ID, EMAIL);

    ArgumentCaptor<InvitationPort.NewInvitation> captor =
        ArgumentCaptor.forClass(InvitationPort.NewInvitation.class);
    verify(invitationPort).save(captor.capture());
    InvitationPort.NewInvitation saved = captor.getValue();
    LocalDateTime now = LocalDateTime.now(FIXED_CLOCK);
    assertThat(saved.tenantId()).isEqualTo(TENANT_ID);
    assertThat(saved.email()).isEqualTo(EMAIL);
    assertThat(saved.role()).isEqualTo(TenantRole.MEMBER);
    assertThat(saved.invitedBy()).isEqualTo(CALLER_ID);
    assertThat(saved.createdAt()).isEqualTo(now);
    assertThat(saved.expiresAt()).isEqualTo(now.plusDays(7));
    // DB にはハッシュのみ。平文は保存されない
    assertThat(saved.tokenHash()).isEqualTo(InviteTokenHasher.sha256Hex(RAW_TOKEN));
    assertThat(saved.tokenHash()).hasSize(64).isNotEqualTo(RAW_TOKEN);

    // メールには平文トークンとテナント名を渡す
    verify(inviteMailPort).sendInvitation(EMAIL, "Acme", RAW_TOKEN);
  }

  @Test
  void execute_usesFallbackTenantName_whenTenantNameMissing() {
    when(invitationPort.isAlreadyMember(TENANT_ID, EMAIL)).thenReturn(false);
    when(tokenGenerator.generate()).thenReturn(RAW_TOKEN);
    when(invitationPort.findTenantName(TENANT_ID)).thenReturn(Optional.empty());

    useCase().execute(TENANT_ID, CALLER_ID, EMAIL, TenantRole.TENANT_ADMIN);

    verify(inviteMailPort).sendInvitation(eq(EMAIL), eq("テナント"), eq(RAW_TOKEN));
  }

  @Test
  void execute_throwsConflict_whenAlreadyMember() {
    when(invitationPort.isAlreadyMember(TENANT_ID, EMAIL)).thenReturn(true);

    assertThatThrownBy(() -> useCase().execute(TENANT_ID, CALLER_ID, EMAIL, TenantRole.MEMBER))
        .isInstanceOf(UserAlreadyMemberException.class);

    verify(invitationPort, never()).save(org.mockito.ArgumentMatchers.any());
    verifyNoInteractions(tokenGenerator, inviteMailPort);
  }
}
