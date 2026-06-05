package xyz.dgz48.tasks.webapi.tenant.usecase;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.dgz48.tasks.webapi.tenant.domain.TenantCrossBoundaryException;
import xyz.dgz48.tasks.webapi.tenant.domain.TenantRole;
import xyz.dgz48.tasks.webapi.tenant.domain.UserTenantAlreadyExistsException;

@ExtendWith(MockitoExtension.class)
class AddMemberUseCaseTest {

  @Mock UserTenantManagementPort managementPort;
  @InjectMocks AddMemberUseCase useCase;

  private static final Long CALLER_TENANT_ID = 1L;
  private static final Long TENANT_ID = 1L;
  private static final Long TARGET_USER_ID = 99L;

  @Test
  void execute_addsMember_whenValid() {
    when(managementPort.existsMember(TARGET_USER_ID, TENANT_ID)).thenReturn(false);

    useCase.execute(CALLER_TENANT_ID, TENANT_ID, TARGET_USER_ID, TenantRole.MEMBER);

    verify(managementPort).addMember(TARGET_USER_ID, TENANT_ID, TenantRole.MEMBER);
  }

  @Test
  void execute_throws_whenAlreadyExists() {
    when(managementPort.existsMember(TARGET_USER_ID, TENANT_ID)).thenReturn(true);

    assertThatThrownBy(
            () -> useCase.execute(CALLER_TENANT_ID, TENANT_ID, TARGET_USER_ID, TenantRole.MEMBER))
        .isInstanceOf(UserTenantAlreadyExistsException.class);
  }

  @Test
  void execute_throws_whenCallerTenantMismatch() {
    Long otherTenantId = 2L;

    assertThatThrownBy(
            () ->
                useCase.execute(CALLER_TENANT_ID, otherTenantId, TARGET_USER_ID, TenantRole.MEMBER))
        .isInstanceOf(TenantCrossBoundaryException.class);
  }

  @Test
  void execute_allowsSaasAdmin_whenCallerTenantIdIsNull() {
    when(managementPort.existsMember(TARGET_USER_ID, TENANT_ID)).thenReturn(false);

    useCase.execute(null, TENANT_ID, TARGET_USER_ID, TenantRole.TENANT_ADMIN);

    verify(managementPort).addMember(TARGET_USER_ID, TENANT_ID, TenantRole.TENANT_ADMIN);
  }
}
