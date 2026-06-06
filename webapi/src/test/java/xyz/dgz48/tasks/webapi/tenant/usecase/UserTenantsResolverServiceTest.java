package xyz.dgz48.tasks.webapi.tenant.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.dgz48.tasks.webapi.tenant.domain.TenantMembership;
import xyz.dgz48.tasks.webapi.tenant.domain.TenantRole;

@ExtendWith(MockitoExtension.class)
class UserTenantsResolverServiceTest {

  @Mock TenantMembershipPort tenantMembershipPort;
  @InjectMocks UserTenantsResolverService service;

  @Test
  void noMemberships_returnsEmpty() {
    given(tenantMembershipPort.findActiveMemberships(1L)).willReturn(List.of());

    Optional<TenantMembership> result = service.resolveInitial(1L);

    assertThat(result).isEmpty();
  }

  @Test
  void oneMembership_returnsThat() {
    TenantMembership membership = new TenantMembership(10L, TenantRole.MEMBER);
    given(tenantMembershipPort.findActiveMemberships(1L)).willReturn(List.of(membership));

    Optional<TenantMembership> result = service.resolveInitial(1L);

    assertThat(result).hasValue(membership);
  }

  @Test
  void multipleMemberships_returnsFirst() {
    TenantMembership first = new TenantMembership(10L, TenantRole.MEMBER);
    TenantMembership second = new TenantMembership(20L, TenantRole.TENANT_ADMIN);
    given(tenantMembershipPort.findActiveMemberships(1L)).willReturn(List.of(first, second));

    Optional<TenantMembership> result = service.resolveInitial(1L);

    assertThat(result).hasValue(first);
  }
}
