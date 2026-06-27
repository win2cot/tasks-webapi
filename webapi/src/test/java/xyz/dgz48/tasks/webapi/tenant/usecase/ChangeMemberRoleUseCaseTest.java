package xyz.dgz48.tasks.webapi.tenant.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.dgz48.tasks.webapi.tenant.domain.TenantRole;
import xyz.dgz48.tasks.webapi.tenant.domain.TenantUserInfo;
import xyz.dgz48.tasks.webapi.tenant.domain.UserTenantNotFoundException;
import xyz.dgz48.tasks.webapi.tenant.domain.UserTenantSelfOperationException;
import xyz.dgz48.tasks.webapi.tenant.domain.UserTenantStatus;

@ExtendWith(MockitoExtension.class)
class ChangeMemberRoleUseCaseTest {

  @Mock UserTenantManagementPort managementPort;
  @Mock ListTenantUsersPort listTenantUsersPort;
  @InjectMocks ChangeMemberRoleUseCase useCase;

  private static final Long CALLER_ID = 1L;
  private static final Long TENANT_ID = 10L;
  private static final Long TARGET_USER_ID = 99L;

  private static final TenantUserInfo UPDATED =
      new TenantUserInfo(
          TARGET_USER_ID,
          "bob@example.com",
          "Bob",
          null,
          TenantRole.TENANT_ADMIN,
          UserTenantStatus.ACTIVE,
          LocalDateTime.of(2026, 1, 1, 0, 0));

  @Test
  void execute_changesRoleAndReturnsUpdatedMember_whenValid() {
    when(managementPort.changeActiveMemberRole(TARGET_USER_ID, TENANT_ID, TenantRole.TENANT_ADMIN))
        .thenReturn(true);
    when(listTenantUsersPort.findActiveTenantUser(TARGET_USER_ID, TENANT_ID))
        .thenReturn(Optional.of(UPDATED));

    TenantUserInfo result =
        useCase.execute(CALLER_ID, TENANT_ID, TARGET_USER_ID, TenantRole.TENANT_ADMIN);

    assertThat(result).isEqualTo(UPDATED);
    verify(managementPort)
        .changeActiveMemberRole(TARGET_USER_ID, TENANT_ID, TenantRole.TENANT_ADMIN);
  }

  @Test
  void execute_throws_whenMemberNotFound() {
    when(managementPort.changeActiveMemberRole(TARGET_USER_ID, TENANT_ID, TenantRole.MEMBER))
        .thenReturn(false);

    assertThatThrownBy(
            () -> useCase.execute(CALLER_ID, TENANT_ID, TARGET_USER_ID, TenantRole.MEMBER))
        .isInstanceOf(UserTenantNotFoundException.class);
  }

  @Test
  void execute_throws_whenSelfRoleChange() {
    assertThatThrownBy(() -> useCase.execute(CALLER_ID, TENANT_ID, CALLER_ID, TenantRole.MEMBER))
        .isInstanceOf(UserTenantSelfOperationException.class);
    verifyNoInteractions(managementPort);
  }

  @Test
  void execute_throws_whenUpdatedMemberDisappears() {
    when(managementPort.changeActiveMemberRole(TARGET_USER_ID, TENANT_ID, TenantRole.MEMBER))
        .thenReturn(true);
    when(listTenantUsersPort.findActiveTenantUser(TARGET_USER_ID, TENANT_ID))
        .thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> useCase.execute(CALLER_ID, TENANT_ID, TARGET_USER_ID, TenantRole.MEMBER))
        .isInstanceOf(UserTenantNotFoundException.class);
  }
}
