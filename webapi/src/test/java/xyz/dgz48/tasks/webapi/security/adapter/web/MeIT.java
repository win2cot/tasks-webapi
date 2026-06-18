package xyz.dgz48.tasks.webapi.security.adapter.web;

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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import xyz.dgz48.tasks.webapi.FixedClockConfiguration;
import xyz.dgz48.tasks.webapi.MockJwtDecoderConfiguration;
import xyz.dgz48.tasks.webapi.TestcontainersConfiguration;
import xyz.dgz48.tasks.webapi.security.domain.TasksPrincipal;
import xyz.dgz48.tasks.webapi.tenant.adapter.persistence.TenantJpaEntity;
import xyz.dgz48.tasks.webapi.user.adapter.persistence.UserJpaEntity;

@SpringBootTest
@AutoConfigureMockMvc
@Import({
  TestcontainersConfiguration.class,
  MockJwtDecoderConfiguration.class,
  FixedClockConfiguration.class
})
class MeIT {

  @Autowired MockMvc mockMvc;
  @Autowired EntityManager em;
  @Autowired TransactionTemplate txTemplate;

  private Long userId;
  private Long tenantId1;
  private Long tenantId2;
  private TasksAuthenticationToken authToken;

  @BeforeEach
  void setUp() {
    txTemplate.execute(
        ignored -> {
          var user =
              new UserJpaEntity("sub-me-it", "me-it@example.com", "MeテストIT", "ミーテストIT", "開発部");
          em.persist(user);
          em.flush();
          userId = user.getId();

          var tenant1 = new TenantJpaEntity("me-t1", "MeテナントA");
          var tenant2 = new TenantJpaEntity("me-t2", "MeテナントB");
          em.persist(tenant1);
          em.persist(tenant2);
          em.flush();
          tenantId1 = tenant1.getId();
          tenantId2 = tenant2.getId();

          return null;
        });

    var principal =
        new TasksPrincipal(userId, "sub-me-it", "me-it@example.com", "MeテストIT", "ミーテストIT", "開発部");
    authToken = new TasksAuthenticationToken(principal, List.of());
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
    if (userId == null) {
      return;
    }
    txTemplate.execute(
        ignored -> {
          em.createNativeQuery("DELETE FROM user_tenants WHERE user_id = ?")
              .setParameter(1, userId)
              .executeUpdate();
          em.createNativeQuery("DELETE FROM tenants WHERE id IN (?, ?)")
              .setParameter(1, tenantId1)
              .setParameter(2, tenantId2)
              .executeUpdate();
          em.createNativeQuery("DELETE FROM users WHERE id = ?")
              .setParameter(1, userId)
              .executeUpdate();
          return null;
        });
  }

  @Test
  void unauthenticated_returns401() throws Exception {
    mockMvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
  }

  @Test
  void noTenants_returnsEmptyArray() throws Exception {
    mockMvc
        .perform(get("/api/auth/me").with(authentication(authToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.user.id").value(userId))
        .andExpect(jsonPath("$.user.email").value("me-it@example.com"))
        .andExpect(jsonPath("$.user.fullName").value("MeテストIT"))
        .andExpect(jsonPath("$.user.departmentName").value("開発部"))
        .andExpect(jsonPath("$.tenants").isArray())
        .andExpect(jsonPath("$.tenants").isEmpty())
        .andExpect(jsonPath("$.activeTenantId").isEmpty());
  }

  @Test
  void oneTenant_returnsSingleEntry() throws Exception {
    insertUserTenant(userId, tenantId1, "MEMBER", LocalDateTime.of(2026, 1, 1, 0, 0));

    mockMvc
        .perform(get("/api/auth/me").with(authentication(authToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tenants.length()").value(1))
        .andExpect(jsonPath("$.tenants[0].id").value(tenantId1))
        .andExpect(jsonPath("$.tenants[0].code").value("me-t1"))
        .andExpect(jsonPath("$.tenants[0].name").value("MeテナントA"))
        .andExpect(jsonPath("$.tenants[0].role").value("MEMBER"))
        .andExpect(jsonPath("$.activeTenantId").isEmpty());
  }

  @Test
  void multipleTenants_returnsAllInJoinedAtOrder() throws Exception {
    insertUserTenant(userId, tenantId2, "TENANT_ADMIN", LocalDateTime.of(2026, 2, 1, 0, 0));
    insertUserTenant(userId, tenantId1, "MEMBER", LocalDateTime.of(2026, 1, 1, 0, 0));

    mockMvc
        .perform(get("/api/auth/me").with(authentication(authToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tenants.length()").value(2))
        .andExpect(jsonPath("$.tenants[0].id").value(tenantId1))
        .andExpect(jsonPath("$.tenants[0].role").value("MEMBER"))
        .andExpect(jsonPath("$.tenants[1].id").value(tenantId2))
        .andExpect(jsonPath("$.tenants[1].role").value("TENANT_ADMIN"));
  }

  @Test
  void withXTenantIdHeader_returnsActiveTenantId() throws Exception {
    insertUserTenant(userId, tenantId1, "MEMBER", LocalDateTime.of(2026, 1, 1, 0, 0));

    mockMvc
        .perform(
            get("/api/auth/me").with(authentication(authToken)).header("X-Tenant-Id", tenantId1))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.activeTenantId").value(tenantId1));
  }

  private void insertUserTenant(Long uid, Long tid, String role, LocalDateTime joinedAt) {
    txTemplate.execute(
        ignored -> {
          em.createNativeQuery(
                  "INSERT INTO user_tenants (user_id, tenant_id, role, status, joined_at)"
                      + " VALUES (?,?,?,?,?)")
              .setParameter(1, uid)
              .setParameter(2, tid)
              .setParameter(3, role)
              .setParameter(4, "ACTIVE")
              .setParameter(5, joinedAt)
              .executeUpdate();
          return null;
        });
  }
}
