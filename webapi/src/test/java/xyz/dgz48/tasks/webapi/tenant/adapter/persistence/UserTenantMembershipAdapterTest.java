package xyz.dgz48.tasks.webapi.tenant.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;
import xyz.dgz48.tasks.webapi.FixedClockConfiguration;
import xyz.dgz48.tasks.webapi.MockJwtDecoderConfiguration;
import xyz.dgz48.tasks.webapi.TestcontainersConfiguration;
import xyz.dgz48.tasks.webapi.tenant.domain.TenantRole;
import xyz.dgz48.tasks.webapi.tenant.usecase.TenantMembershipPort;
import xyz.dgz48.tasks.webapi.user.adapter.persistence.UserJpaEntity;

@SpringBootTest
@Import({
  TestcontainersConfiguration.class,
  MockJwtDecoderConfiguration.class,
  FixedClockConfiguration.class
})
@Transactional
class UserTenantMembershipAdapterTest {

  @Autowired TenantMembershipPort membershipPort;
  @Autowired EntityManager em;

  private Long userId;
  private Long tenantId;

  @BeforeEach
  void setUp() {
    var user =
        new UserJpaEntity("sub-membership-test", "member@example.com", "テスト太郎", "テストタロウ", null);
    em.persist(user);

    em.createNativeQuery(
            "INSERT INTO tenants (code, name, plan, status, created_at, updated_at) VALUES (?,?,?,?,?,?)")
        .setParameter(1, "T-MEMBERSHIP")
        .setParameter(2, "メンバーシップテストテナント")
        .setParameter(3, "STANDARD")
        .setParameter(4, "ACTIVE")
        .setParameter(5, LocalDateTime.of(2026, 1, 1, 0, 0))
        .setParameter(6, LocalDateTime.of(2026, 1, 1, 0, 0))
        .executeUpdate();

    em.flush();

    userId = user.getId();
    tenantId =
        ((Number)
                em.createNativeQuery("SELECT id FROM tenants WHERE code = 'T-MEMBERSHIP'")
                    .getSingleResult())
            .longValue();
  }

  @Test
  void returnsRoleForActiveMember() {
    insertUserTenant(userId, tenantId, "MEMBER", "ACTIVE");
    em.flush();

    Optional<TenantRole> role = membershipPort.findActiveRole(userId, tenantId);

    assertThat(role).hasValue(TenantRole.MEMBER);
  }

  @Test
  void returnsTenantAdminRoleForActiveTenantAdmin() {
    insertUserTenant(userId, tenantId, "TENANT_ADMIN", "ACTIVE");
    em.flush();

    Optional<TenantRole> role = membershipPort.findActiveRole(userId, tenantId);

    assertThat(role).hasValue(TenantRole.TENANT_ADMIN);
  }

  @Test
  void returnsEmptyForInactiveMember() {
    insertUserTenant(userId, tenantId, "MEMBER", "DISABLED");
    em.flush();

    Optional<TenantRole> role = membershipPort.findActiveRole(userId, tenantId);

    assertThat(role).isEmpty();
  }

  @Test
  void returnsEmptyForInvitedMember() {
    insertUserTenant(userId, tenantId, "MEMBER", "INVITED");
    em.flush();

    Optional<TenantRole> role = membershipPort.findActiveRole(userId, tenantId);

    assertThat(role).isEmpty();
  }

  @Test
  void returnsEmptyWhenNoRecord() {
    Optional<TenantRole> role = membershipPort.findActiveRole(userId, tenantId);

    assertThat(role).isEmpty();
  }

  private void insertUserTenant(Long uid, Long tid, String role, String status) {
    em.createNativeQuery(
            "INSERT INTO user_tenants (user_id, tenant_id, role, status, joined_at) VALUES (?,?,?,?,?)")
        .setParameter(1, uid)
        .setParameter(2, tid)
        .setParameter(3, role)
        .setParameter(4, status)
        .setParameter(5, LocalDateTime.of(2026, 1, 1, 0, 0))
        .executeUpdate();
  }
}
