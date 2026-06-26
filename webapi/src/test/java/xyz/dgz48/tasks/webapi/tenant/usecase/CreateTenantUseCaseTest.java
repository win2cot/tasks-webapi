package xyz.dgz48.tasks.webapi.tenant.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.dgz48.tasks.webapi.audit.domain.AuditEventType;
import xyz.dgz48.tasks.webapi.audit.usecase.AuditLogPort;
import xyz.dgz48.tasks.webapi.tenant.domain.Tenant;
import xyz.dgz48.tasks.webapi.tenant.domain.TenantCodeConflictException;
import xyz.dgz48.tasks.webapi.tenant.domain.TenantCodeGenerator;
import xyz.dgz48.tasks.webapi.tenant.domain.TenantPlan;
import xyz.dgz48.tasks.webapi.tenant.domain.TenantRole;
import xyz.dgz48.tasks.webapi.tenant.domain.TenantStatus;
import xyz.dgz48.tasks.webapi.tenant.domain.UserTenant;
import xyz.dgz48.tasks.webapi.tenant.domain.UserTenantStatus;

/** {@link CreateTenantUseCase} の単体テスト(code 一意化リトライ・初代 admin 登録・監査記録)。 */
@ExtendWith(MockitoExtension.class)
class CreateTenantUseCaseTest {

  private static final Long CALLER_ID = 42L;
  private static final Clock FIXED_CLOCK =
      Clock.fixed(Instant.parse("2026-06-26T01:00:00Z"), ZoneOffset.UTC);

  @Mock private AdminTenantRepository adminTenantRepository;
  @Mock private UserTenantManagementPort userTenantManagementPort;
  @Mock private AuditLogPort auditLogPort;

  private CreateTenantUseCase useCase() {
    return new CreateTenantUseCase(
        adminTenantRepository,
        userTenantManagementPort,
        new TenantCodeGenerator(),
        auditLogPort,
        FIXED_CLOCK);
  }

  private Tenant createdTenant(Long id, String code, String name) {
    LocalDateTime ts = LocalDateTime.of(2026, 6, 26, 10, 0);
    return new Tenant(id, code, name, TenantPlan.FREE, TenantStatus.ACTIVE, ts, ts, 0L, 0L);
  }

  private CreateTenantCommand command(String name) {
    return new CreateTenantCommand(CALLER_ID, "owner@example.com", "所有者", "開発部", name);
  }

  @Test
  void createsTenantAndRegistersCallerAsTenantAdmin() {
    given(adminTenantRepository.existsByCode("sales-team")).willReturn(false);
    given(adminTenantRepository.createTenant("sales-team", "Sales Team"))
        .willReturn(createdTenant(7L, "sales-team", "Sales Team"));
    given(userTenantManagementPort.addMember(CALLER_ID, 7L, TenantRole.TENANT_ADMIN))
        .willReturn(new UserTenant(CALLER_ID, 7L, TenantRole.TENANT_ADMIN));

    CreateTenantResult result = useCase().execute(command("Sales Team"));

    assertThat(result.tenant().getId()).isEqualTo(7L);
    assertThat(result.tenant().getCode()).isEqualTo("sales-team");
    assertThat(result.tenant().getPlan()).isEqualTo(TenantPlan.FREE);
    assertThat(result.tenant().getUserCount()).isEqualTo(1L);
    assertThat(result.tenant().getTaskCount()).isZero();

    assertThat(result.initialAdmin().userId()).isEqualTo(CALLER_ID);
    assertThat(result.initialAdmin().email()).isEqualTo("owner@example.com");
    assertThat(result.initialAdmin().role()).isEqualTo(TenantRole.TENANT_ADMIN);
    assertThat(result.initialAdmin().status()).isEqualTo(UserTenantStatus.ACTIVE);
    // joinedAt は固定 Clock(01:00 UTC)由来
    assertThat(result.initialAdmin().joinedAt())
        .isEqualTo(LocalDateTime.ofInstant(FIXED_CLOCK.instant(), ZoneOffset.UTC));

    verify(userTenantManagementPort).addMember(CALLER_ID, 7L, TenantRole.TENANT_ADMIN);
    verify(auditLogPort).record(eq(AuditEventType.TENANT_CREATED), eq(7L), eq(CALLER_ID), any());
  }

  @Test
  void retriesWithSuffixWhenBaseCodeCollides() {
    given(adminTenantRepository.existsByCode("sales-team")).willReturn(true);
    given(adminTenantRepository.existsByCode("sales-team-2")).willReturn(false);
    given(adminTenantRepository.createTenant("sales-team-2", "Sales Team"))
        .willReturn(createdTenant(8L, "sales-team-2", "Sales Team"));
    given(userTenantManagementPort.addMember(CALLER_ID, 8L, TenantRole.TENANT_ADMIN))
        .willReturn(new UserTenant(CALLER_ID, 8L, TenantRole.TENANT_ADMIN));

    CreateTenantResult result = useCase().execute(command("Sales Team"));

    assertThat(result.tenant().getCode()).isEqualTo("sales-team-2");
    verify(adminTenantRepository).createTenant("sales-team-2", "Sales Team");
  }

  @Test
  void throwsConflictWhenAllSuffixesExhausted() {
    given(adminTenantRepository.existsByCode(any())).willReturn(true);

    assertThatThrownBy(() -> useCase().execute(command("Sales Team")))
        .isInstanceOf(TenantCodeConflictException.class);

    verify(adminTenantRepository, never()).createTenant(any(), any());
    verify(userTenantManagementPort, never()).addMember(any(), any(), any());
  }
}
