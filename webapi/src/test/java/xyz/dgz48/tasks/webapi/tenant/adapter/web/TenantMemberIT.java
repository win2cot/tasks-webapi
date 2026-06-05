package xyz.dgz48.tasks.webapi.tenant.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import org.springframework.http.MediaType;
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
 * テナントメンバー管理 API の統合テスト。
 *
 * <p>POST/DELETE/PATCH {@code /api/tenants/{tenantId}/users} を SaaS Admin / Tenant Admin /
 * 非メンバーのシナリオで検証する。クロステナント漏洩なし・自己操作禁止を重点確認。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({
  TestcontainersConfiguration.class,
  MockJwtDecoderConfiguration.class,
  FixedClockConfiguration.class
})
class TenantMemberIT {

  @Autowired MockMvc mockMvc;
  @Autowired EntityManager em;
  @Autowired TransactionTemplate txTemplate;

  private Long adminUserId;
  private Long targetUserId;
  private Long tenantId;
  private Long otherTenantId;

  private TasksAuthenticationToken saasAdminToken;
  private TasksAuthenticationToken tenantAdminToken;
  private TasksAuthenticationToken memberToken;

  @BeforeEach
  void setUp() {
    txTemplate.execute(
        ignored -> {
          var adminUser =
              new UserJpaEntity("sub-tm-admin", "tm-admin@example.com", "管理太郎", "カンリタロウ", null);
          em.persist(adminUser);

          var targetUser =
              new UserJpaEntity("sub-tm-target", "tm-target@example.com", "対象花子", "タイショウハナコ", null);
          em.persist(targetUser);

          em.flush();
          adminUserId = adminUser.getId();
          targetUserId = targetUser.getId();

          var tenant = new TenantJpaEntity("TM-IT-1", "メンバー管理ITテナント");
          var other = new TenantJpaEntity("TM-IT-2", "他テナント");
          em.persist(tenant);
          em.persist(other);
          em.flush();
          tenantId = tenant.getId();
          otherTenantId = other.getId();

          em.createNativeQuery(
                  "INSERT INTO user_tenants (user_id, tenant_id, role, status, joined_at)"
                      + " VALUES (?,?,?,?,?)")
              .setParameter(1, adminUserId)
              .setParameter(2, tenantId)
              .setParameter(3, "TENANT_ADMIN")
              .setParameter(4, "ACTIVE")
              .setParameter(5, LocalDateTime.of(2026, 1, 1, 0, 0))
              .executeUpdate();

          return null;
        });

    SecurityContextHolder.clearContext();

    var adminPrincipal =
        new TasksPrincipal(
            adminUserId, "sub-tm-admin", "tm-admin@example.com", "管理太郎", "カンリタロウ", null);

    saasAdminToken =
        new TasksAuthenticationToken(
            adminPrincipal, List.of(new SimpleGrantedAuthority("ROLE_APP_ADMIN")));

    tenantAdminToken =
        new TasksAuthenticationToken(
            adminPrincipal, List.of(new SimpleGrantedAuthority("ROLE_TENANT_ADMIN")));

    memberToken =
        new TasksAuthenticationToken(
            adminPrincipal, List.of(new SimpleGrantedAuthority("ROLE_MEMBER")));
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
    if (adminUserId == null) {
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
              .setParameter(1, adminUserId)
              .setParameter(2, targetUserId)
              .executeUpdate();
          return null;
        });
  }

  // =========================================================
  // POST /api/tenants/{tenantId}/users
  // =========================================================

  @Test
  void addMember_returns201_whenSaasAdmin() throws Exception {
    mockMvc
        .perform(
            post("/api/tenants/{tenantId}/users", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":" + targetUserId + ",\"role\":\"MEMBER\"}")
                .with(authentication(saasAdminToken)))
        .andExpect(status().isCreated());

    assertMemberExists(targetUserId, tenantId, "MEMBER");
  }

  @Test
  void addMember_returns201_whenTenantAdmin_withMatchingTenantContext() throws Exception {
    mockMvc
        .perform(
            post("/api/tenants/{tenantId}/users", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":" + targetUserId + ",\"role\":\"MEMBER\"}")
                .header("X-Tenant-Id", tenantId)
                .with(authentication(tenantAdminToken)))
        .andExpect(status().isCreated());

    assertMemberExists(targetUserId, tenantId, "MEMBER");
  }

  @Test
  void addMember_returns403_whenTenantAdmin_withMismatchedTenantContext() throws Exception {
    mockMvc
        .perform(
            post("/api/tenants/{tenantId}/users", otherTenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":" + targetUserId + ",\"role\":\"MEMBER\"}")
                .header("X-Tenant-Id", tenantId)
                .with(authentication(tenantAdminToken)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("E_FORBIDDEN"));
  }

  @Test
  void addMember_returns403_whenMember() throws Exception {
    mockMvc
        .perform(
            post("/api/tenants/{tenantId}/users", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":" + targetUserId + ",\"role\":\"MEMBER\"}")
                .with(authentication(memberToken)))
        .andExpect(status().isForbidden());
  }

  @Test
  void addMember_returns409_whenAlreadyExists() throws Exception {
    insertMember(targetUserId, tenantId, "MEMBER", "ACTIVE");

    mockMvc
        .perform(
            post("/api/tenants/{tenantId}/users", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":" + targetUserId + ",\"role\":\"MEMBER\"}")
                .with(authentication(saasAdminToken)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("E_CONFLICT"));
  }

  // =========================================================
  // DELETE /api/tenants/{tenantId}/users/{userId}
  // =========================================================

  @Test
  void removeMember_returns204_whenSaasAdmin() throws Exception {
    insertMember(targetUserId, tenantId, "MEMBER", "ACTIVE");

    mockMvc
        .perform(
            delete("/api/tenants/{tenantId}/users/{userId}", tenantId, targetUserId)
                .with(authentication(saasAdminToken)))
        .andExpect(status().isNoContent());

    assertMemberNotExists(targetUserId, tenantId);
  }

  @Test
  void removeMember_returns403_whenSelfRemoval() throws Exception {
    mockMvc
        .perform(
            delete("/api/tenants/{tenantId}/users/{userId}", tenantId, adminUserId)
                .with(authentication(saasAdminToken)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("E_FORBIDDEN"));
  }

  @Test
  void removeMember_returns404_whenNotActiveMember() throws Exception {
    mockMvc
        .perform(
            delete("/api/tenants/{tenantId}/users/{userId}", tenantId, targetUserId)
                .with(authentication(saasAdminToken)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("E_NOT_FOUND"));
  }

  @Test
  void removeMember_returns403_whenCrossTenantByTenantAdmin() throws Exception {
    insertMember(targetUserId, otherTenantId, "MEMBER", "ACTIVE");

    mockMvc
        .perform(
            delete("/api/tenants/{tenantId}/users/{userId}", otherTenantId, targetUserId)
                .header("X-Tenant-Id", tenantId)
                .with(authentication(tenantAdminToken)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("E_FORBIDDEN"));
  }

  // =========================================================
  // PATCH /api/tenants/{tenantId}/users/{userId}
  // =========================================================

  @Test
  void changeMemberRole_returns204_whenSaasAdmin() throws Exception {
    insertMember(targetUserId, tenantId, "MEMBER", "ACTIVE");

    mockMvc
        .perform(
            patch("/api/tenants/{tenantId}/users/{userId}", tenantId, targetUserId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"TENANT_ADMIN\"}")
                .with(authentication(saasAdminToken)))
        .andExpect(status().isNoContent());

    assertMemberExists(targetUserId, tenantId, "TENANT_ADMIN");
  }

  @Test
  void changeMemberRole_returns403_whenSelfChange() throws Exception {
    mockMvc
        .perform(
            patch("/api/tenants/{tenantId}/users/{userId}", tenantId, adminUserId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"MEMBER\"}")
                .with(authentication(saasAdminToken)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("E_FORBIDDEN"));
  }

  @Test
  void changeMemberRole_returns404_whenNotActiveMember() throws Exception {
    mockMvc
        .perform(
            patch("/api/tenants/{tenantId}/users/{userId}", tenantId, targetUserId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"TENANT_ADMIN\"}")
                .with(authentication(saasAdminToken)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("E_NOT_FOUND"));
  }

  @Test
  void changeMemberRole_returns403_whenCrossTenantByTenantAdmin() throws Exception {
    insertMember(targetUserId, otherTenantId, "MEMBER", "ACTIVE");

    mockMvc
        .perform(
            patch("/api/tenants/{tenantId}/users/{userId}", otherTenantId, targetUserId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"TENANT_ADMIN\"}")
                .header("X-Tenant-Id", tenantId)
                .with(authentication(tenantAdminToken)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("E_FORBIDDEN"));
  }

  // =========================================================
  // helpers
  // =========================================================

  private void insertMember(Long userId, Long tId, String role, String status) {
    txTemplate.execute(
        ignored -> {
          em.createNativeQuery(
                  "INSERT INTO user_tenants (user_id, tenant_id, role, status, joined_at)"
                      + " VALUES (?,?,?,?,?)")
              .setParameter(1, userId)
              .setParameter(2, tId)
              .setParameter(3, role)
              .setParameter(4, status)
              .setParameter(5, LocalDateTime.of(2026, 1, 1, 0, 0))
              .executeUpdate();
          return null;
        });
  }

  private void assertMemberExists(Long userId, Long tId, String expectedRole) {
    txTemplate.execute(
        ignored -> {
          Object[] row =
              (Object[])
                  em.createNativeQuery(
                          "SELECT role, status FROM user_tenants"
                              + " WHERE user_id = ? AND tenant_id = ?")
                      .setParameter(1, userId)
                      .setParameter(2, tId)
                      .getSingleResult();
          assertThat(row[0]).isEqualTo(expectedRole);
          assertThat(row[1]).isEqualTo("ACTIVE");
          return null;
        });
  }

  private void assertMemberNotExists(Long userId, Long tId) {
    txTemplate.execute(
        ignored -> {
          long count =
              ((Number)
                      em.createNativeQuery(
                              "SELECT COUNT(*) FROM user_tenants"
                                  + " WHERE user_id = ? AND tenant_id = ?")
                          .setParameter(1, userId)
                          .setParameter(2, tId)
                          .getSingleResult())
                  .longValue();
          assertThat(count).isZero();
          return null;
        });
  }
}
