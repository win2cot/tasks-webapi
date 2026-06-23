package xyz.dgz48.tasks.webapi.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import xyz.dgz48.tasks.webapi.FixedClockConfiguration;
import xyz.dgz48.tasks.webapi.MockJwtDecoderConfiguration;
import xyz.dgz48.tasks.webapi.TestcontainersConfiguration;
import xyz.dgz48.tasks.webapi.security.adapter.web.TasksAuthenticationToken;
import xyz.dgz48.tasks.webapi.security.domain.TasksPrincipal;
import xyz.dgz48.tasks.webapi.shared.domain.TenantContext;
import xyz.dgz48.tasks.webapi.task.adapter.persistence.TaskJpaEntity;
import xyz.dgz48.tasks.webapi.task.domain.Priority;
import xyz.dgz48.tasks.webapi.task.domain.TaskStatus;
import xyz.dgz48.tasks.webapi.task.domain.Visibility;
import xyz.dgz48.tasks.webapi.tenant.adapter.persistence.TenantJpaEntity;
import xyz.dgz48.tasks.webapi.user.adapter.persistence.UserJpaEntity;

/**
 * 認可違反({@code *_DENIED})の {@code audit_logs} 記録配線を検証する統合 IT(基本設計書 §6.2.3 / #736)。
 *
 * <p>§6.2.3 の 8 シナリオのうち {@code TENANT_CROSSED} は {@link CrossTenantViolationDetectionIT} で検証済み。 本
 * IT は残り 7 アクション(VIEW / EDIT / DELETE / STATUS_CHANGE / VISIBILITY_CHANGE / STAKEHOLDER_EDIT /
 * ROLE_BASED)の記録漏れを防ぐ。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({
  TestcontainersConfiguration.class,
  MockJwtDecoderConfiguration.class,
  FixedClockConfiguration.class
})
class AuthorizationDeniedAuditIT {

  @Autowired MockMvc mockMvc;
  @Autowired EntityManager em;
  @Autowired TransactionTemplate txTemplate;

  private Long ownerId;
  private Long memberUserId;
  private Long tenantId;
  private Long tenantTaskId;
  private Long privateTaskId;

  /** 所有者でも担当者でも関係者でもない、テナント内の一般 Member(認可違反の主体)。 */
  private TasksAuthenticationToken memberToken;

  @BeforeEach
  void setUp() {
    txTemplate.execute(
        ignored -> {
          var owner =
              new UserJpaEntity(
                  "sub-ad-owner", "ad-owner@example.com", "所有者 太郎", "ショユウシャ タロウ", null);
          var member =
              new UserJpaEntity(
                  "sub-ad-member", "ad-member@example.com", "メンバー 太郎", "メンバー タロウ", null);
          em.persist(owner);
          em.persist(member);
          em.flush();
          ownerId = owner.getId();
          memberUserId = member.getId();

          // JPA Auditing(created_by / updated_by)に必要な SecurityContext
          var ownerPrincipal =
              new TasksPrincipal(
                  ownerId, "sub-ad-owner", "ad-owner@example.com", "所有者 太郎", "ショユウシャ タロウ", null);
          SecurityContextHolder.getContext()
              .setAuthentication(new TasksAuthenticationToken(ownerPrincipal, List.of()));

          var tenant = new TenantJpaEntity("AD-TENANT", "認可違反テナント");
          em.persist(tenant);
          em.flush();
          tenantId = tenant.getId();

          insertUserTenant(ownerId, tenantId, "MEMBER");
          insertUserTenant(memberUserId, tenantId, "MEMBER");

          var tenantTask =
              new TaskJpaEntity(
                  tenantId,
                  ownerId,
                  "TENANT task",
                  null,
                  TaskStatus.NOT_STARTED,
                  Priority.MEDIUM,
                  LocalDate.of(2026, 12, 31));
          var privateTask =
              new TaskJpaEntity(
                  tenantId,
                  ownerId,
                  "PRIVATE task",
                  null,
                  TaskStatus.NOT_STARTED,
                  Priority.MEDIUM,
                  Visibility.PRIVATE,
                  null,
                  LocalDate.of(2026, 12, 31));
          em.persist(tenantTask);
          em.persist(privateTask);
          em.flush();
          tenantTaskId = tenantTask.getId();
          privateTaskId = privateTask.getId();

          return null;
        });

    SecurityContextHolder.clearContext();
    deleteAllAuditLogs();

    var memberPrincipal =
        new TasksPrincipal(
            memberUserId, "sub-ad-member", "ad-member@example.com", "メンバー 太郎", "メンバー タロウ", null);
    memberToken = new TasksAuthenticationToken(memberPrincipal, List.of());
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
    if (tenantId == null) return;
    TenantContext.set(tenantId);
    try {
      txTemplate.execute(
          ignored -> {
            em.createNativeQuery("DELETE FROM audit_logs").executeUpdate();
            em.createNativeQuery("DELETE FROM tasks WHERE tenant_id = ?")
                .setParameter(1, tenantId)
                .executeUpdate();
            em.createNativeQuery("DELETE FROM user_tenants WHERE tenant_id = ?")
                .setParameter(1, tenantId)
                .executeUpdate();
            em.createNativeQuery("DELETE FROM tenants WHERE id = ?")
                .setParameter(1, tenantId)
                .executeUpdate();
            em.createNativeQuery("DELETE FROM users WHERE id IN (?,?)")
                .setParameter(1, ownerId)
                .setParameter(2, memberUserId)
                .executeUpdate();
            return null;
          });
    } finally {
      TenantContext.clear();
    }
  }

  @Test
  void getTask_byNonViewer_recordsViewDenied() throws Exception {
    mockMvc
        .perform(
            get("/api/tasks/" + privateTaskId)
                .header("X-Tenant-Id", String.valueOf(tenantId))
                .with(authentication(memberToken)))
        .andExpect(status().isNotFound());

    assertDeniedRecorded("VIEW_DENIED", privateTaskId);
  }

  @Test
  void patchTask_byNonOwner_recordsEditDenied() throws Exception {
    mockMvc
        .perform(
            patch("/api/tasks/" + tenantTaskId)
                .header("X-Tenant-Id", String.valueOf(tenantId))
                .header(HttpHeaders.IF_MATCH, "W/\"0\"")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"New title\"}")
                .with(authentication(memberToken)))
        .andExpect(status().isForbidden());

    assertDeniedRecorded("EDIT_DENIED", tenantTaskId);
  }

  @Test
  void deleteTask_byNonOwner_recordsDeleteDenied() throws Exception {
    mockMvc
        .perform(
            delete("/api/tasks/" + tenantTaskId)
                .header("X-Tenant-Id", String.valueOf(tenantId))
                .header(HttpHeaders.IF_MATCH, "W/\"0\"")
                .with(authentication(memberToken)))
        .andExpect(status().isForbidden());

    assertDeniedRecorded("DELETE_DENIED", tenantTaskId);
  }

  @Test
  void changeStatus_byNonOwnerNonAssignee_recordsStatusChangeDenied() throws Exception {
    mockMvc
        .perform(
            patch("/api/tasks/" + tenantTaskId + "/status")
                .header("X-Tenant-Id", String.valueOf(tenantId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"IN_PROGRESS\"}")
                .with(authentication(memberToken)))
        .andExpect(status().isForbidden());

    assertDeniedRecorded("STATUS_CHANGE_DENIED", tenantTaskId);
  }

  @Test
  void changeVisibility_byNonOwner_recordsVisibilityChangeDenied() throws Exception {
    mockMvc
        .perform(
            patch("/api/tasks/" + tenantTaskId + "/visibility")
                .header("X-Tenant-Id", String.valueOf(tenantId))
                .header(HttpHeaders.IF_MATCH, "W/\"0\"")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"visibility\":\"PRIVATE\"}")
                .with(authentication(memberToken)))
        .andExpect(status().isForbidden());

    assertDeniedRecorded("VISIBILITY_CHANGE_DENIED", tenantTaskId);
  }

  @Test
  void addStakeholder_byNonOwnerNonAssignee_recordsStakeholderEditDenied() throws Exception {
    mockMvc
        .perform(
            post("/api/tasks/" + tenantTaskId + "/stakeholders")
                .header("X-Tenant-Id", String.valueOf(tenantId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":" + memberUserId + "}")
                .with(authentication(memberToken)))
        .andExpect(status().isForbidden());

    assertDeniedRecorded("STAKEHOLDER_EDIT_DENIED", tenantTaskId);
  }

  @Test
  void adminApi_byNonAdmin_recordsRoleBasedDenied() throws Exception {
    // /api/tenants は SaaS Admin 専用(@PreAuthorize hasRole('APP_ADMIN'))。テナント免除パスのため
    // X-Tenant-Id は付与しない。一般 Member には APP_ADMIN ロールが無く AccessDeniedException となる。
    mockMvc
        .perform(get("/api/tenants").with(authentication(memberToken)))
        .andExpect(status().isForbidden());

    txTemplate.execute(
        ignored -> {
          List<?> rows =
              em.createNativeQuery(
                      "SELECT detail FROM audit_logs WHERE action = 'ROLE_BASED_DENIED'")
                  .getResultList();
          assertThat(rows).as("ROLE_BASED_DENIED が記録される").hasSize(1);
          assertThat((String) rows.get(0)).contains("/api/tenants");
          return null;
        });
  }

  @Test
  void deniedRecords_maintainHashChain() throws Exception {
    mockMvc
        .perform(
            patch("/api/tasks/" + tenantTaskId)
                .header("X-Tenant-Id", String.valueOf(tenantId))
                .header(HttpHeaders.IF_MATCH, "W/\"0\"")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"New title\"}")
                .with(authentication(memberToken)))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            delete("/api/tasks/" + tenantTaskId)
                .header("X-Tenant-Id", String.valueOf(tenantId))
                .header(HttpHeaders.IF_MATCH, "W/\"0\"")
                .with(authentication(memberToken)))
        .andExpect(status().isForbidden());

    txTemplate.execute(
        ignored -> {
          @SuppressWarnings("unchecked")
          List<String> hashes =
              em.createNativeQuery("SELECT hash_chain FROM audit_logs ORDER BY id").getResultList();
          assertThat(hashes).hasSize(2);
          assertThat(hashes.get(0)).isEqualTo("0".repeat(64));
          assertThat(hashes.get(1)).isNotEqualTo("0".repeat(64)).matches("[0-9a-f]{64}");
          return null;
        });
  }

  private void assertDeniedRecorded(String action, Long taskId) {
    txTemplate.execute(
        ignored -> {
          List<?> rows =
              em.createNativeQuery(
                      "SELECT detail FROM audit_logs WHERE tenant_id = ? AND action = ?")
                  .setParameter(1, tenantId)
                  .setParameter(2, action)
                  .getResultList();
          assertThat(rows).as("%s が記録される", action).hasSize(1);
          assertThat(((String) rows.get(0)).replace(" ", "")).contains("\"taskId\":" + taskId);
          return null;
        });
  }

  private void deleteAllAuditLogs() {
    txTemplate.execute(
        ignored -> {
          em.createNativeQuery("DELETE FROM audit_logs").executeUpdate();
          return null;
        });
  }

  private void insertUserTenant(Long userId, Long tid, String role) {
    em.createNativeQuery(
            "INSERT INTO user_tenants (user_id, tenant_id, role, status, joined_at)"
                + " VALUES (?,?,?,?,?)")
        .setParameter(1, userId)
        .setParameter(2, tid)
        .setParameter(3, role)
        .setParameter(4, "ACTIVE")
        .setParameter(5, LocalDateTime.of(2026, 1, 1, 0, 0))
        .executeUpdate();
  }
}
