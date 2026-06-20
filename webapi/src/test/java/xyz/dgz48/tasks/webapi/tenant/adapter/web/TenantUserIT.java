package xyz.dgz48.tasks.webapi.tenant.adapter.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import xyz.dgz48.tasks.webapi.FixedClockConfiguration;
import xyz.dgz48.tasks.webapi.MockJwtDecoderConfiguration;
import xyz.dgz48.tasks.webapi.TestcontainersConfiguration;
import xyz.dgz48.tasks.webapi.security.adapter.web.TasksAuthenticationToken;
import xyz.dgz48.tasks.webapi.security.domain.TasksPrincipal;
import xyz.dgz48.tasks.webapi.tenant.adapter.persistence.TenantJpaEntity;
import xyz.dgz48.tasks.webapi.user.adapter.persistence.UserJpaEntity;

/**
 * GET /api/tenant/users の統合テスト。
 *
 * <p>Member / Tenant Admin がテナント内のユーザー一覧を取得でき、SaaS Admin が 403 になることを検証する。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({
  TestcontainersConfiguration.class,
  MockJwtDecoderConfiguration.class,
  FixedClockConfiguration.class
})
class TenantUserIT {

  @Autowired MockMvc mockMvc;
  @Autowired EntityManager em;
  @Autowired TransactionTemplate txTemplate;

  private Long memberUserId;
  private Long adminUserId;
  private Long tenantId;
  private Long otherTenantId;

  private TasksAuthenticationToken memberToken;
  private TasksAuthenticationToken tenantAdminToken;

  @BeforeEach
  void setUp() {
    txTemplate.execute(
        ignored -> {
          var memberUser =
              new UserJpaEntity(
                  "sub-tu-it-member", "tu-it-member@example.com", "メンバー一郎", "メンバーイチロウ", "開発部");
          em.persist(memberUser);

          var adminUser =
              new UserJpaEntity(
                  "sub-tu-it-admin", "tu-it-admin@example.com", "管理者花子", "カンリシャハナコ", null);
          em.persist(adminUser);

          em.flush();
          memberUserId = memberUser.getId();
          adminUserId = adminUser.getId();

          var tenant = new TenantJpaEntity("TU-IT-1", "ユーザー一覧ITテナント");
          var other = new TenantJpaEntity("TU-IT-2", "別テナント");
          em.persist(tenant);
          em.persist(other);
          em.flush();
          tenantId = tenant.getId();
          otherTenantId = other.getId();

          em.createNativeQuery(
                  "INSERT INTO user_tenants (user_id, tenant_id, role, status, joined_at)"
                      + " VALUES (?,?,?,?,?)")
              .setParameter(1, memberUserId)
              .setParameter(2, tenantId)
              .setParameter(3, "MEMBER")
              .setParameter(4, "ACTIVE")
              .setParameter(5, LocalDateTime.of(2026, 1, 1, 0, 0))
              .executeUpdate();

          em.createNativeQuery(
                  "INSERT INTO user_tenants (user_id, tenant_id, role, status, joined_at)"
                      + " VALUES (?,?,?,?,?)")
              .setParameter(1, adminUserId)
              .setParameter(2, tenantId)
              .setParameter(3, "TENANT_ADMIN")
              .setParameter(4, "ACTIVE")
              .setParameter(5, LocalDateTime.of(2026, 1, 2, 0, 0))
              .executeUpdate();

          return null;
        });

    SecurityContextHolder.clearContext();

    var memberPrincipal =
        new TasksPrincipal(
            memberUserId,
            "sub-tu-it-member",
            "tu-it-member@example.com",
            "メンバー一郎",
            "メンバーイチロウ",
            "開発部");
    memberToken =
        new TasksAuthenticationToken(
            memberPrincipal, List.of(new SimpleGrantedAuthority("ROLE_MEMBER")));

    var adminPrincipal =
        new TasksPrincipal(
            adminUserId, "sub-tu-it-admin", "tu-it-admin@example.com", "管理者花子", "カンリシャハナコ", null);
    tenantAdminToken =
        new TasksAuthenticationToken(
            adminPrincipal, List.of(new SimpleGrantedAuthority("ROLE_TENANT_ADMIN")));
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
    if (tenantId == null) {
      return;
    }
    txTemplate.execute(
        ignored -> {
          em.createNativeQuery("DELETE FROM user_tenants WHERE tenant_id IN (?, ?)")
              .setParameter(1, tenantId)
              .setParameter(2, otherTenantId)
              .executeUpdate();
          em.createNativeQuery("DELETE FROM tenants WHERE id IN (?, ?)")
              .setParameter(1, tenantId)
              .setParameter(2, otherTenantId)
              .executeUpdate();
          em.createNativeQuery("DELETE FROM users WHERE id IN (?, ?)")
              .setParameter(1, memberUserId)
              .setParameter(2, adminUserId)
              .executeUpdate();
          return null;
        });
  }

  @Test
  void listTenantUsers_returns200WithBothUsers_whenMember() throws Exception {
    mockMvc
        .perform(
            get("/api/tenant/users")
                .header("X-Tenant-Id", tenantId)
                .with(authentication(memberToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].email").value("tu-it-member@example.com"))
        .andExpect(jsonPath("$[0].role").value("MEMBER"))
        .andExpect(jsonPath("$[0].status").value("ACTIVE"))
        .andExpect(jsonPath("$[0].departmentName").value("開発部"))
        .andExpect(jsonPath("$[1].email").value("tu-it-admin@example.com"))
        .andExpect(jsonPath("$[1].role").value("TENANT_ADMIN"));
  }

  @Test
  void listTenantUsers_returns200WithBothUsers_whenTenantAdmin() throws Exception {
    mockMvc
        .perform(
            get("/api/tenant/users")
                .header("X-Tenant-Id", tenantId)
                .with(authentication(tenantAdminToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2));
  }

  @Test
  void listTenantUsers_returns200_whenTenantAutoResolved() throws Exception {
    mockMvc
        .perform(get("/api/tenant/users").with(authentication(memberToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2));
  }

  @Test
  void listTenantUsers_returns401_whenUnauthenticated() throws Exception {
    mockMvc
        .perform(get("/api/tenant/users").header("X-Tenant-Id", tenantId))
        .andExpect(status().isUnauthorized());
  }
}
