package xyz.dgz48.tasks.webapi.tenant.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class TenantAuditDiffDomainServiceTest {

  private final TenantAuditDiffDomainService service = new TenantAuditDiffDomainService();

  private static Tenant tenant(String name) {
    return new Tenant(
        1L,
        "test-tenant",
        name,
        TenantPlan.STANDARD,
        TenantStatus.ACTIVE,
        LocalDateTime.of(2026, 6, 1, 0, 0),
        LocalDateTime.of(2026, 6, 1, 0, 0),
        0L,
        0L);
  }

  @Test
  void diff_returnsEmpty_whenNameUnchanged() {
    Tenant previous = tenant("旧テナント名");
    TenantUpdateCommand cmd = new TenantUpdateCommand("旧テナント名");

    assertThat(service.diff(previous, cmd)).isEmpty();
  }

  @Test
  void diff_detectsNameChange() {
    Tenant previous = tenant("旧テナント名");
    TenantUpdateCommand cmd = new TenantUpdateCommand("新テナント名");

    List<FieldChange> changes = service.diff(previous, cmd);

    assertThat(changes).hasSize(1);
    assertThat(changes.get(0).field()).isEqualTo("name");
    assertThat(changes.get(0).oldValue()).isEqualTo("旧テナント名");
    assertThat(changes.get(0).newValue()).isEqualTo("新テナント名");
  }
}
