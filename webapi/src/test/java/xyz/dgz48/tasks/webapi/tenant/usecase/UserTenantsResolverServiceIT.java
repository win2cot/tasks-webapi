package xyz.dgz48.tasks.webapi.tenant.usecase;

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
import xyz.dgz48.tasks.webapi.tenant.domain.TenantMembership;
import xyz.dgz48.tasks.webapi.tenant.domain.TenantRole;
import xyz.dgz48.tasks.webapi.user.adapter.persistence.UserJpaEntity;

@SpringBootTest
@Import({
  TestcontainersConfiguration.class,
  MockJwtDecoderConfiguration.class,
  FixedClockConfiguration.class
})
@Transactional
class UserTenantsResolverServiceIT {

  @Autowired UserTenantsResolverService service;
  @Autowired EntityManager em;

  private Long userId;
  private Long tenantId1;
  private Long tenantId2;

  @BeforeEach
  void setUp() {
    var user =
        new UserJpaEntity("sub-resolver-it", "resolver-it@example.com", "リゾルバテスト", "リゾルバテスト", null);
    em.persist(user);
    userId = user.getId();

    tenantId1 = insertTenant("TR-T1", "リゾルバテナント1");
    tenantId2 = insertTenant("TR-T2", "リゾルバテナント2");
    em.flush();
  }

  @Test
  void noMemberships_returnsEmpty() {
    Optional<TenantMembership> result = service.resolveInitial(userId);

    assertThat(result).isEmpty();
  }

  @Test
  void oneMembership_returnsThat() {
    insertUserTenant(userId, tenantId1, "MEMBER", "ACTIVE", LocalDateTime.of(2026, 1, 1, 0, 0));
    em.flush();

    Optional<TenantMembership> result = service.resolveInitial(userId);

    assertThat(result)
        .hasValueSatisfying(
            m -> {
              assertThat(m.tenantId()).isEqualTo(tenantId1);
              assertThat(m.role()).isEqualTo(TenantRole.MEMBER);
            });
  }

  @Test
  void multipleMemberships_returnsEarliest() {
    insertUserTenant(userId, tenantId2, "MEMBER", "ACTIVE", LocalDateTime.of(2026, 2, 1, 0, 0));
    insertUserTenant(
        userId, tenantId1, "TENANT_ADMIN", "ACTIVE", LocalDateTime.of(2026, 1, 1, 0, 0));
    em.flush();

    Optional<TenantMembership> result = service.resolveInitial(userId);

    assertThat(result)
        .hasValueSatisfying(
            m -> {
              assertThat(m.tenantId()).isEqualTo(tenantId1);
              assertThat(m.role()).isEqualTo(TenantRole.TENANT_ADMIN);
            });
  }

  @Test
  void invitedMembershipsAreIgnored() {
    insertUserTenant(userId, tenantId1, "MEMBER", "INVITED", LocalDateTime.of(2026, 1, 1, 0, 0));
    em.flush();

    Optional<TenantMembership> result = service.resolveInitial(userId);

    assertThat(result).isEmpty();
  }

  private Long insertTenant(String code, String name) {
    em.createNativeQuery(
            "INSERT INTO tenants (code, name, plan, status, created_at, updated_at) VALUES (?,?,?,?,?,?)")
        .setParameter(1, code)
        .setParameter(2, name)
        .setParameter(3, "STANDARD")
        .setParameter(4, "ACTIVE")
        .setParameter(5, LocalDateTime.of(2026, 1, 1, 0, 0))
        .setParameter(6, LocalDateTime.of(2026, 1, 1, 0, 0))
        .executeUpdate();
    return ((Number)
            em.createNativeQuery("SELECT id FROM tenants WHERE code = ?")
                .setParameter(1, code)
                .getSingleResult())
        .longValue();
  }

  private void insertUserTenant(
      Long uid, Long tid, String role, String status, LocalDateTime joinedAt) {
    em.createNativeQuery(
            "INSERT INTO user_tenants (user_id, tenant_id, role, status, joined_at) VALUES (?,?,?,?,?)")
        .setParameter(1, uid)
        .setParameter(2, tid)
        .setParameter(3, role)
        .setParameter(4, status)
        .setParameter(5, joinedAt)
        .executeUpdate();
  }
}
