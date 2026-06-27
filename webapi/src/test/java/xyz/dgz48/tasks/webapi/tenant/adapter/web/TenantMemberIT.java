package xyz.dgz48.tasks.webapi.tenant.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
 * テナントメンバー管理 API の統合テスト(#792 是正後)。
 *
 * <p>DELETE {@code /api/tenant/users/{userId}} / PUT {@code /api/tenant/users/{userId}/role} を
 * Tenant Admin / Member / SaaS Admin(非メンバー)のシナリオで検証する。テナントは X-Tenant-Id 駆動(テナント暗黙)で、
 * 別テナントのメンバーには干渉できない(404)= 越境漏洩なしを確認。SaaS Admin はメンバー管理 API で 403(§6.2.1)。
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
  private Long memberUserId;
  private Long targetUserId;
  private Long saasAdminUserId;
  private Long tenantId;
  private Long otherTenantId;

  private TasksAuthenticationToken tenantAdminToken;
  private TasksAuthenticationToken memberToken;
  private TasksAuthenticationToken saasAdminToken;

  @BeforeEach
  void setUp() {
    txTemplate.execute(
        ignored -> {
          var adminUser =
              new UserJpaEntity("sub-tm-admin", "tm-admin@example.com", "管理太郎", "カンリタロウ", null);
          var memberUser =
              new UserJpaEntity("sub-tm-member", "tm-member@example.com", "一般次郎", "イッパンジロウ", null);
          var targetUser =
              new UserJpaEntity("sub-tm-target", "tm-target@example.com", "対象花子", "タイショウハナコ", null);
          var saasAdminUser =
              new UserJpaEntity("sub-tm-saas", "tm-saas@example.com", "運営三郎", "ウンエイサブロウ", null);
          em.persist(adminUser);
          em.persist(memberUser);
          em.persist(targetUser);
          em.persist(saasAdminUser);
          em.flush();
          adminUserId = adminUser.getId();
          memberUserId = memberUser.getId();
          targetUserId = targetUser.getId();
          saasAdminUserId = saasAdminUser.getId();

          var tenant = new TenantJpaEntity("TM-IT-1", "メンバー管理ITテナント");
          var other = new TenantJpaEntity("TM-IT-2", "他テナント");
          em.persist(tenant);
          em.persist(other);
          em.flush();
          tenantId = tenant.getId();
          otherTenantId = other.getId();

          // 呼び出し側(Tenant Admin)と一般メンバーを現テナントに登録。
          insertMembershipInTx(adminUserId, tenantId, "TENANT_ADMIN", "ACTIVE");
          insertMembershipInTx(memberUserId, tenantId, "MEMBER", "ACTIVE");
          // saasAdminUser はどのテナントにも所属させない(非メンバー)。

          return null;
        });

    SecurityContextHolder.clearContext();

    tenantAdminToken =
        new TasksAuthenticationToken(
            new TasksPrincipal(
                adminUserId, "sub-tm-admin", "tm-admin@example.com", "管理太郎", "カンリタロウ", null),
            List.of());

    memberToken =
        new TasksAuthenticationToken(
            new TasksPrincipal(
                memberUserId, "sub-tm-member", "tm-member@example.com", "一般次郎", "イッパンジロウ", null),
            List.of());

    saasAdminToken =
        new TasksAuthenticationToken(
            new TasksPrincipal(
                saasAdminUserId, "sub-tm-saas", "tm-saas@example.com", "運営三郎", "ウンエイサブロウ", null),
            List.of(new SimpleGrantedAuthority("ROLE_APP_ADMIN")));
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
          em.createNativeQuery("DELETE FROM users WHERE id IN (?, ?, ?, ?)")
              .setParameter(1, adminUserId)
              .setParameter(2, memberUserId)
              .setParameter(3, targetUserId)
              .setParameter(4, saasAdminUserId)
              .executeUpdate();
          return null;
        });
  }

  // =========================================================
  // DELETE /api/tenant/users/{userId}
  // =========================================================

  @Test
  void removeMember_returns204_whenTenantAdmin() throws Exception {
    insertMember(targetUserId, tenantId, "MEMBER", "ACTIVE");

    mockMvc
        .perform(
            delete("/api/tenant/users/{userId}", targetUserId)
                .header("X-Tenant-Id", tenantId)
                .with(authentication(tenantAdminToken)))
        .andExpect(status().isNoContent());

    assertMemberNotExists(targetUserId, tenantId);
  }

  @Test
  void removeMember_returns403_whenMember() throws Exception {
    insertMember(targetUserId, tenantId, "MEMBER", "ACTIVE");

    mockMvc
        .perform(
            delete("/api/tenant/users/{userId}", targetUserId)
                .header("X-Tenant-Id", tenantId)
                .with(authentication(memberToken)))
        .andExpect(status().isForbidden());
  }

  @Test
  void removeMember_returns403_whenSaasAdmin() throws Exception {
    insertMember(targetUserId, tenantId, "MEMBER", "ACTIVE");

    // SaaS Admin は当該テナントの非メンバーのため、TenantContextFilter が 403 を返す(§6.2.1)。
    mockMvc
        .perform(
            delete("/api/tenant/users/{userId}", targetUserId)
                .header("X-Tenant-Id", tenantId)
                .with(authentication(saasAdminToken)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("E_FORBIDDEN"));
  }

  @Test
  void removeMember_returns401_whenUnauthenticated() throws Exception {
    mockMvc
        .perform(delete("/api/tenant/users/{userId}", targetUserId).header("X-Tenant-Id", tenantId))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void removeMember_returns403_whenSelfRemoval() throws Exception {
    mockMvc
        .perform(
            delete("/api/tenant/users/{userId}", adminUserId)
                .header("X-Tenant-Id", tenantId)
                .with(authentication(tenantAdminToken)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("E_FORBIDDEN"));
  }

  @Test
  void removeMember_returns404_whenNotActiveMember() throws Exception {
    mockMvc
        .perform(
            delete("/api/tenant/users/{userId}", targetUserId)
                .header("X-Tenant-Id", tenantId)
                .with(authentication(tenantAdminToken)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("E_NOT_FOUND"));
  }

  @Test
  void removeMember_returns404_whenTargetBelongsToAnotherTenant() throws Exception {
    // 対象は別テナントのメンバー。現テナント(X-Tenant-Id)には居ないため 404 = 越境干渉不可。
    insertMember(targetUserId, otherTenantId, "MEMBER", "ACTIVE");

    mockMvc
        .perform(
            delete("/api/tenant/users/{userId}", targetUserId)
                .header("X-Tenant-Id", tenantId)
                .with(authentication(tenantAdminToken)))
        .andExpect(status().isNotFound());

    assertMemberExists(targetUserId, otherTenantId, "MEMBER");
  }

  // =========================================================
  // PUT /api/tenant/users/{userId}/role
  // =========================================================

  @Test
  void updateRole_returns200WithUpdatedMember_whenTenantAdmin() throws Exception {
    insertMember(targetUserId, tenantId, "MEMBER", "ACTIVE");

    mockMvc
        .perform(
            put("/api/tenant/users/{userId}/role", targetUserId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"TENANT_ADMIN\"}")
                .header("X-Tenant-Id", tenantId)
                .with(authentication(tenantAdminToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.userId").value(targetUserId.intValue()))
        .andExpect(jsonPath("$.email").value("tm-target@example.com"))
        .andExpect(jsonPath("$.role").value("TENANT_ADMIN"))
        .andExpect(jsonPath("$.status").value("ACTIVE"));

    assertMemberExists(targetUserId, tenantId, "TENANT_ADMIN");
  }

  @Test
  void updateRole_returns403_whenMember() throws Exception {
    insertMember(targetUserId, tenantId, "MEMBER", "ACTIVE");

    mockMvc
        .perform(
            put("/api/tenant/users/{userId}/role", targetUserId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"TENANT_ADMIN\"}")
                .header("X-Tenant-Id", tenantId)
                .with(authentication(memberToken)))
        .andExpect(status().isForbidden());
  }

  @Test
  void updateRole_returns403_whenSaasAdmin() throws Exception {
    insertMember(targetUserId, tenantId, "MEMBER", "ACTIVE");

    mockMvc
        .perform(
            put("/api/tenant/users/{userId}/role", targetUserId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"TENANT_ADMIN\"}")
                .header("X-Tenant-Id", tenantId)
                .with(authentication(saasAdminToken)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("E_FORBIDDEN"));
  }

  @Test
  void updateRole_returns403_whenSelfChange() throws Exception {
    mockMvc
        .perform(
            put("/api/tenant/users/{userId}/role", adminUserId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"MEMBER\"}")
                .header("X-Tenant-Id", tenantId)
                .with(authentication(tenantAdminToken)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("E_FORBIDDEN"));
  }

  @Test
  void updateRole_returns404_whenNotActiveMember() throws Exception {
    mockMvc
        .perform(
            put("/api/tenant/users/{userId}/role", targetUserId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"TENANT_ADMIN\"}")
                .header("X-Tenant-Id", tenantId)
                .with(authentication(tenantAdminToken)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("E_NOT_FOUND"));
  }

  // =========================================================
  // helpers
  // =========================================================

  private void insertMember(Long userId, Long tId, String role, String status) {
    txTemplate.execute(
        ignored -> {
          insertMembershipInTx(userId, tId, role, status);
          return null;
        });
  }

  private void insertMembershipInTx(Long userId, Long tId, String role, String status) {
    em.createNativeQuery(
            "INSERT INTO user_tenants (user_id, tenant_id, role, status, joined_at)"
                + " VALUES (?,?,?,?,?)")
        .setParameter(1, userId)
        .setParameter(2, tId)
        .setParameter(3, role)
        .setParameter(4, status)
        .setParameter(5, LocalDateTime.of(2026, 1, 1, 0, 0))
        .executeUpdate();
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
