package xyz.dgz48.tasks.webapi.tenant.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import xyz.dgz48.tasks.webapi.FixedClockConfiguration;
import xyz.dgz48.tasks.webapi.MockJwtDecoderConfiguration;
import xyz.dgz48.tasks.webapi.TestcontainersConfiguration;
import xyz.dgz48.tasks.webapi.security.adapter.web.TasksAuthenticationToken;
import xyz.dgz48.tasks.webapi.security.domain.TasksPrincipal;
import xyz.dgz48.tasks.webapi.user.adapter.persistence.UserJpaEntity;

/**
 * セルフサインアップ(A-05)POST /api/tenants の統合 IT(Testcontainers MySQL 8.4)。
 *
 * <p>認証済み・テナント未所属ユーザーが {@code X-Tenant-Id} なしでテナントを作成し、初代 TENANT_ADMIN として登録されることを検証する。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({
  TestcontainersConfiguration.class,
  MockJwtDecoderConfiguration.class,
  FixedClockConfiguration.class
})
class TenantSignupIT {

  @Autowired MockMvc mockMvc;
  @Autowired EntityManager em;
  @Autowired TransactionTemplate txTemplate;

  private Long userId;
  private TasksAuthenticationToken authToken;
  private final List<Long> createdTenantIds = new ArrayList<>();

  @BeforeEach
  void setUp() {
    createdTenantIds.clear();
    txTemplate.execute(
        ignored -> {
          var user =
              new UserJpaEntity("sub-signup", "signup@example.com", "サインアップ太郎", "サインアップタロウ", "開発部");
          em.persist(user);
          em.flush();
          userId = user.getId();
          return null;
        });
    var principal =
        new TasksPrincipal(
            userId, "sub-signup", "signup@example.com", "サインアップ太郎", "サインアップタロウ", "開発部");
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
          for (Long tenantId : createdTenantIds) {
            em.createNativeQuery("DELETE FROM audit_logs WHERE tenant_id = ?")
                .setParameter(1, tenantId)
                .executeUpdate();
            em.createNativeQuery("DELETE FROM chain_heads WHERE chain_key = ?")
                .setParameter(1, tenantId)
                .executeUpdate();
          }
          em.createNativeQuery("DELETE FROM user_tenants WHERE user_id = ?")
              .setParameter(1, userId)
              .executeUpdate();
          for (Long tenantId : createdTenantIds) {
            em.createNativeQuery("DELETE FROM tenants WHERE id = ?")
                .setParameter(1, tenantId)
                .executeUpdate();
          }
          em.createNativeQuery("DELETE FROM users WHERE id = ?")
              .setParameter(1, userId)
              .executeUpdate();
          return null;
        });
  }

  @Test
  void createTenant_returns201_andRegistersCallerAsTenantAdmin() throws Exception {
    String response =
        mockMvc
            .perform(
                post("/api/tenants")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"My Team\"}")
                    .with(authentication(authToken)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.tenant.name").value("My Team"))
            .andExpect(jsonPath("$.tenant.code").value("my-team"))
            .andExpect(jsonPath("$.tenant.plan").value("FREE"))
            .andExpect(jsonPath("$.tenant.status").value("ACTIVE"))
            .andExpect(jsonPath("$.tenant.userCount").value(1))
            .andExpect(jsonPath("$.tenant.taskCount").value(0))
            .andExpect(jsonPath("$.initialAdmin.userId").value(userId))
            .andExpect(jsonPath("$.initialAdmin.email").value("signup@example.com"))
            .andExpect(jsonPath("$.initialAdmin.role").value("TENANT_ADMIN"))
            .andExpect(jsonPath("$.initialAdmin.status").value("ACTIVE"))
            .andReturn()
            .getResponse()
            .getContentAsString();

    Long tenantId = ((Number) JsonPath.read(response, "$.tenant.id")).longValue();
    createdTenantIds.add(tenantId);

    Object[] row =
        (Object[])
            em.createNativeQuery(
                    "SELECT role, status FROM user_tenants WHERE user_id = ? AND tenant_id = ?")
                .setParameter(1, userId)
                .setParameter(2, tenantId)
                .getSingleResult();
    assertThat(row[0]).isEqualTo("TENANT_ADMIN");
    assertThat(row[1]).isEqualTo("ACTIVE");
  }

  @Test
  void createTenant_sameName_generatesSuffixedCode() throws Exception {
    String first =
        mockMvc
            .perform(
                post("/api/tenants")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"Dup Team\"}")
                    .with(authentication(authToken)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.tenant.code").value("dup-team"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    createdTenantIds.add(((Number) JsonPath.read(first, "$.tenant.id")).longValue());

    String second =
        mockMvc
            .perform(
                post("/api/tenants")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"Dup Team\"}")
                    .with(authentication(authToken)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.tenant.code").value("dup-team-2"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    createdTenantIds.add(((Number) JsonPath.read(second, "$.tenant.id")).longValue());
  }

  @Test
  void createTenant_blankName_returns400() throws Exception {
    mockMvc
        .perform(
            post("/api/tenants")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"   \"}")
                .with(authentication(authToken)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("E_VALIDATION"));
  }

  @Test
  void createTenant_unauthenticated_returns401() throws Exception {
    mockMvc
        .perform(
            post("/api/tenants")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"No Auth\"}"))
        .andExpect(status().isUnauthorized());
  }
}
