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
import xyz.dgz48.tasks.webapi.tenant.domain.UserTenantNotFoundException;
import xyz.dgz48.tasks.webapi.tenant.domain.UserTenantSelfOperationException;

@ExtendWith(MockitoExtension.class)
class ChangeMemberRoleUseCaseTest {

  @Mock UserTenantManagementPort managementPort;
  @InjectMocks ChangeMemberRoleUseCase useCase;

  private static final Long CALLER_ID = 1L;
  private static final Long CALLER_TENANT_ID = 10L;
  private static final Long TENANT_ID = 10L;
  private static final Long TARGET_USER_ID = 99L;

  @Test
  void execute_changesRole_whenValid() {
    when(managementPort.changeActiveMemberRole(TARGET_USER_ID, TENANT_ID, TenantRole.TENANT_ADMIN))
        .thenReturn(true);

    useCase.execute(
        CALLER_ID, CALLER_TENANT_ID, TENANT_ID, TARGET_USER_ID, TenantRole.TENANT_ADMIN);

    verify(managementPort)
        .changeActiveMemberRole(TARGET_USER_ID, TENANT_ID, TenantRole.TENANT_ADMIN);
  }

  @Test
  void execute_throws_whenMemberNotFound() {
    when(managementPort.changeActiveMemberRole(TARGET_USER_ID, TENANT_ID, TenantRole.MEMBER))
        .thenReturn(false);

    assertThatThrownBy(
            () ->
                useCase.execute(
                    CALLER_ID, CALLER_TENANT_ID, TENANT_ID, TARGET_USER_ID, TenantRole.MEMBER))
        .isInstanceOf(UserTenantNotFoundException.class);
  }

  @Test
  void execute_throws_whenSelfRoleChange() {
    assertThatThrownBy(
            () ->
                useCase.execute(
                    CALLER_ID, CALLER_TENANT_ID, TENANT_ID, CALLER_ID, TenantRole.MEMBER))
        .isInstanceOf(UserTenantSelfOperationException.class);
  }

  @Test
  void execute_throws_whenCallerTenantMismatch() {
    Long otherTenantId = 2L;

    assertThatThrownBy(
            () ->
                useCase.execute(
                    CALLER_ID, CALLER_TENANT_ID, otherTenantId, TARGET_USER_ID, TenantRole.MEMBER))
        .isInstanceOf(TenantCrossBoundaryException.class);
  }

  @Test
  void execute_allowsSaasAdmin_whenCallerTenantIdIsNull() {
    when(managementPort.changeActiveMemberRole(TARGET_USER_ID, TENANT_ID, TenantRole.TENANT_ADMIN))
        .thenReturn(true);

    useCase.execute(CALLER_ID, null, TENANT_ID, TARGET_USER_ID, TenantRole.TENANT_ADMIN);

    verify(managementPort)
        .changeActiveMemberRole(TARGET_USER_ID, TENANT_ID, TenantRole.TENANT_ADMIN);
  }
}
