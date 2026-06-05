package xyz.dgz48.tasks.webapi.tenant.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.Optional;
import org.hibernate.Session;
import org.junit.jupiter.api.AfterEach;
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

/**
 * Hibernate Filter 除外テーブルのクエリが tenant_id WHERE 句を付与されないことを確認する IT。
 *
 * <p>設計規約 §3.3.1 / ADR-0010 §6.1 の受け入れ条件: tenantFilter が有効な状態で除外テーブルを参照しても、テナントをまたいだ結果が返ること。
 */
@SpringBootTest
@Import({
  TestcontainersConfiguration.class,
  MockJwtDecoderConfiguration.class,
  FixedClockConfiguration.class
})
@Transactional
class FilterExclusionIT {

  @Autowired EntityManager entityManager;
  @Autowired TenantMembershipPort tenantMembershipPort;

  @AfterEach
  void disableFilter() {
    entityManager.unwrap(Session.class).disableFilter("tenantFilter");
  }

  @Test
  void tenantMasterTableIsAccessibleAcrossTenantsWithFilterEnabled() {
    var tenantA = new TenantJpaEntity("FE-A", "除外テストテナントA");
    var tenantB = new TenantJpaEntity("FE-B", "除外テストテナントB");
    entityManager.persist(tenantA);
    entityManager.persist(tenantB);
    entityManager.flush();
    entityManager.clear();

    entityManager
        .unwrap(Session.class)
        .enableFilter("tenantFilter")
        .setParameter("tenantId", tenantA.getId());

    // tenants テーブルは @Filter 対象外 — tenantB は tenantA の filter があっても参照可能
    TenantJpaEntity found = entityManager.find(TenantJpaEntity.class, tenantB.getId());
    assertThat(found).isNotNull();
    assertThat(found.getCode()).isEqualTo("FE-B");
  }

  @Test
  void platformUserIsAccessibleWithFilterEnabled() {
    var tenant = new TenantJpaEntity("FE-C", "除外テストテナントC");
    entityManager.persist(tenant);

    var user = new UserJpaEntity("sub-fe-excl", "fe-excl@example.com", "除外テスト", "ジョガイテスト", null);
    entityManager.persist(user);
    entityManager.flush();
    entityManager.clear();

    entityManager
        .unwrap(Session.class)
        .enableFilter("tenantFilter")
        .setParameter("tenantId", tenant.getId());

    // users テーブルは @Filter 対象外 — tenant filter があってもユーザーは参照可能
    UserJpaEntity found = entityManager.find(UserJpaEntity.class, user.getId());
    assertThat(found).isNotNull();
    assertThat(found.getOidcSub()).isEqualTo("sub-fe-excl");
  }

  @Test
  void userTenantsMembershipIsAccessibleAcrossTenantsWithFilterEnabled() {
    var tenantA = new TenantJpaEntity("FE-D", "除外テストテナントD");
    var tenantB = new TenantJpaEntity("FE-E", "除外テストテナントE");
    entityManager.persist(tenantA);
    entityManager.persist(tenantB);

    var user = new UserJpaEntity("sub-fe-member", "fe-member@example.com", "メンバー", "メンバー", null);
    entityManager.persist(user);
    entityManager.flush();

    // user は tenantA のメンバー
    entityManager
        .createNativeQuery(
            "INSERT INTO user_tenants (user_id, tenant_id, role, status, joined_at)"
                + " VALUES (?,?,?,?,?)")
        .setParameter(1, user.getId())
        .setParameter(2, tenantA.getId())
        .setParameter(3, "MEMBER")
        .setParameter(4, "ACTIVE")
        .setParameter(5, LocalDateTime.of(2026, 1, 1, 0, 0))
        .executeUpdate();
    entityManager.flush();
    entityManager.clear();

    // tenantB のコンテキストで filter を有効化
    entityManager
        .unwrap(Session.class)
        .enableFilter("tenantFilter")
        .setParameter("tenantId", tenantB.getId());

    // user_tenants は @Filter 対象外 — tenantB の filter が有効でも tenantA のメンバーシップを参照可能
    Optional<TenantRole> role = tenantMembershipPort.findActiveRole(user.getId(), tenantA.getId());
    assertThat(role).hasValue(TenantRole.MEMBER);
  }
}
