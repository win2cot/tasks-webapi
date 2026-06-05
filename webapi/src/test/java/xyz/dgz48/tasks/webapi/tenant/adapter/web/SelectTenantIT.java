package xyz.dgz48.tasks.webapi.tenant.adapter.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import xyz.dgz48.tasks.webapi.security.adapter.web.TasksAuthenticationToken;
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
class SelectTenantIT {

  @Autowired MockMvc mockMvc;
  @Autowired EntityManager em;
  @Autowired TransactionTemplate txTemplate;

  private Long userId;
  private Long memberTenantId;
  private Long nonMemberTenantId;
  private TasksAuthenticationToken authToken;

  @BeforeEach
  void setUp() {
    txTemplate.execute(
        ignored -> {
          var user =
              new UserJpaEntity(
                  "sub-select-tenant", "select@example.com", "切替テスト太郎", "キリカエテストタロウ", null);
          em.persist(user);
          em.flush();
          userId = user.getId();

          var memberTenant = new TenantJpaEntity("ST-MEMBER", "所属テナント");
          var nonMemberTenant = new TenantJpaEntity("ST-OTHER", "非所属テナント");
          em.persist(memberTenant);
          em.persist(nonMemberTenant);
          em.flush();
          memberTenantId = memberTenant.getId();
          nonMemberTenantId = nonMemberTenant.getId();

          em.createNativeQuery(
                  "INSERT INTO user_tenants"
                      + " (user_id, tenant_id, role, status, joined_at) VALUES (?,?,?,?,?)")
              .setParameter(1, userId)
              .setParameter(2, memberTenantId)
              .setParameter(3, "MEMBER")
              .setParameter(4, "ACTIVE")
              .setParameter(5, LocalDateTime.of(2026, 1, 1, 0, 0))
              .executeUpdate();

          return null;
        });

    SecurityContextHolder.clearContext();

    var principal =
        new TasksPrincipal(
            userId, "sub-select-tenant", "select@example.com", "切替テスト太郎", "キリカエテストタロウ", null);
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
          em.createNativeQuery("DELETE FROM tenants WHERE id = ? OR id = ?")
              .setParameter(1, memberTenantId)
              .setParameter(2, nonMemberTenantId)
              .executeUpdate();
          em.createNativeQuery("DELETE FROM users WHERE id = ?")
              .setParameter(1, userId)
              .executeUpdate();
          return null;
        });
  }

  @Test
  void selectTenant_returns204_whenActiveMember() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/tenants/{tenantId}/select", memberTenantId)
                .with(authentication(authToken)))
        .andExpect(status().isNoContent());
  }

  @Test
  void selectTenant_returns403_whenNotMember() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/tenants/{tenantId}/select", nonMemberTenantId)
                .with(authentication(authToken)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("E_FORBIDDEN"));
  }

  @Test
  void selectTenant_returns400_whenTenantIdIsNotNumeric() throws Exception {
    mockMvc
        .perform(post("/api/auth/tenants/abc/select").with(authentication(authToken)))
        .andExpect(status().isBadRequest());
  }
}
