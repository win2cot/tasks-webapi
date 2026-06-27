package xyz.dgz48.tasks.webapi.tenant.usecase;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.dgz48.tasks.webapi.tenant.domain.UserTenantNotFoundException;
import xyz.dgz48.tasks.webapi.tenant.domain.UserTenantSelfOperationException;

@ExtendWith(MockitoExtension.class)
class RemoveMemberUseCaseTest {

  @Mock UserTenantManagementPort managementPort;
  @InjectMocks RemoveMemberUseCase useCase;

  private static final Long CALLER_ID = 1L;
  private static final Long TENANT_ID = 10L;
  private static final Long TARGET_USER_ID = 99L;

  @Test
  void execute_removesMember_whenValid() {
    when(managementPort.removeActiveMember(TARGET_USER_ID, TENANT_ID)).thenReturn(true);

    useCase.execute(CALLER_ID, TENANT_ID, TARGET_USER_ID);

    verify(managementPort).removeActiveMember(TARGET_USER_ID, TENANT_ID);
  }

  @Test
  void execute_throws_whenMemberNotFound() {
    when(managementPort.removeActiveMember(TARGET_USER_ID, TENANT_ID)).thenReturn(false);

    assertThatThrownBy(() -> useCase.execute(CALLER_ID, TENANT_ID, TARGET_USER_ID))
        .isInstanceOf(UserTenantNotFoundException.class);
  }

  @Test
  void execute_throws_whenSelfRemoval() {
    assertThatThrownBy(() -> useCase.execute(CALLER_ID, TENANT_ID, CALLER_ID))
        .isInstanceOf(UserTenantSelfOperationException.class);
  }
}
