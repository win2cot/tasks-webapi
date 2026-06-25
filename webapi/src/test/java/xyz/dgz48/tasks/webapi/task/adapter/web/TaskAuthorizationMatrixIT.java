package xyz.dgz48.tasks.webapi.task.adapter.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
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
import xyz.dgz48.tasks.webapi.shared.infra.AppZones;
import xyz.dgz48.tasks.webapi.task.adapter.persistence.TaskJpaEntity;
import xyz.dgz48.tasks.webapi.task.domain.Priority;
import xyz.dgz48.tasks.webapi.task.domain.TaskStatus;
import xyz.dgz48.tasks.webapi.tenant.adapter.persistence.TenantJpaEntity;
import xyz.dgz48.tasks.webapi.user.adapter.persistence.UserJpaEntity;

/**
 * ADR-0005 タスク認可の Web 層 受け入れマトリクス IT(Issue #768)。
 *
 * <p>タスク認可は <strong>所有者・担当者・関係者の 3 役割のみ</strong>で評価し、{@code TaskAuthorizationDomainService}(SSOT)に
 * 集約する。本 IT は実エンドポイント + 実 DB(Testcontainers MySQL 8.4)で、{@code @PreAuthorize} のロールチェック
 * (TENANT_ADMIN / MEMBER 通過)を抜けた後にドメイン SSOT が業務認可を決定することを固定する。中心的な検証は 2 点:
 *
 * <ol>
 *   <li><strong>Tenant Admin に業務タスク特権が無い(ADR-0005 撤廃確認)</strong>: タスクの所有者・担当者・関係者の いずれでもない
 *       TENANT_ADMIN ロールのユーザーは、編集 / 削除 / ステータス変更 / 公開範囲変更 / 関係者管理を行えない(403)。 STAKEHOLDERS / PRIVATE
 *       タスクは参照すらできない(404)。TENANT タスクの参照は「運営者特権」ではなく通常のメンバー権として 200。
 *   <li><strong>担当者は所有者専用操作を行えない</strong>: 担当者は編集 / 削除 / 公開範囲変更を行えない(403)が、 ステータス変更 /
 *       関係者管理は行える(所有者・担当者の共有操作)。
 * </ol>
 *
 * <p>HTTP ステータス方針: 参照不可・不在 = 404(NIST AC-4 存在秘匿)、更新権限不足 = 403。更新系の認可順序は 「参照可否(404)→ 操作権限(403)→
 * バージョン(412)」であり、TENANT タスクは運営者・担当者とも参照可のため、操作権限不足は 403 になる。
 *
 * <p>役割 × visibility × 操作の他セル(無関係 Member の拒否・各操作の正常系・監査記録)は {@code StakeholderIT} / {@code
 * PatchTaskIT} / {@code DeleteTaskIT} / {@code ChangeVisibilityIT} / {@code ChangeTaskStatusIT} /
 * {@code ListTasksIT} / {@code AuthorizationDeniedAuditIT} で検証済。SSOT 単体マトリクスは {@code
 * TaskAuthorizationDomainServiceTest}。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({
  TestcontainersConfiguration.class,
  MockJwtDecoderConfiguration.class,
  FixedClockConfiguration.class
})
class TaskAuthorizationMatrixIT {

  private static final String IF_MATCH_V0 = "W/\"0\"";

  @Autowired MockMvc mockMvc;
  @Autowired EntityManager em;
  @Autowired TransactionTemplate txTemplate;

  private Long ownerId;
  private Long assigneeId;
  private Long tenantAdminId;
  private Long tenantId;

  private Long tenantTaskId; // owner 所有・assignee なし・TENANT
  private Long stakeholdersTaskId; // owner 所有・assignee 担当・STAKEHOLDERS
  private Long privateTaskId; // owner 所有・assignee 担当・PRIVATE

  private TasksAuthenticationToken tenantAdminToken;
  private TasksAuthenticationToken assigneeToken;

  @BeforeEach
  void setUp() {
    txTemplate.execute(
        ignored -> {
          var owner =
              new UserJpaEntity("sub-am-owner", "am-owner@example.com", "所有者", "ショユウシャ", null);
          var assignee =
              new UserJpaEntity(
                  "sub-am-assignee", "am-assignee@example.com", "担当者", "タントウシャ", null);
          var admin =
              new UserJpaEntity("sub-am-admin", "am-admin@example.com", "運営者", "ウンエイシャ", null);
          em.persist(owner);
          em.persist(assignee);
          em.persist(admin);
          em.flush();
          ownerId = owner.getId();
          assigneeId = assignee.getId();
          tenantAdminId = admin.getId();

          // Auditing 用 SecurityContext(タスク作成の created_by 解決)
          var ownerPrincipal =
              new TasksPrincipal(
                  ownerId, "sub-am-owner", "am-owner@example.com", "所有者", "ショユウシャ", null);
          SecurityContextHolder.getContext()
              .setAuthentication(new TasksAuthenticationToken(ownerPrincipal, List.of()));

          var tenant = new TenantJpaEntity("AM-TENANT", "認可マトリクステナント");
          em.persist(tenant);
          em.flush();
          tenantId = tenant.getId();

          // owner / assignee は MEMBER、admin だけが TENANT_ADMIN(タスクには無関係)
          insertUserTenant(ownerId, "MEMBER");
          insertUserTenant(assigneeId, "MEMBER");
          insertUserTenant(tenantAdminId, "TENANT_ADMIN");

          var tenantTask =
              new TaskJpaEntity(
                  tenantId,
                  ownerId,
                  "TENANT可視タスク",
                  null,
                  TaskStatus.NOT_STARTED,
                  Priority.MEDIUM,
                  LocalDate.of(2026, 12, 31));
          em.persist(tenantTask);
          em.flush();
          tenantTaskId = tenantTask.getId();

          stakeholdersTaskId = insertTask("STAKEHOLDERS可視タスク", "STAKEHOLDERS", assigneeId);
          privateTaskId = insertTask("PRIVATE可視タスク", "PRIVATE", assigneeId);

          return null;
        });

    SecurityContextHolder.clearContext();

    tenantAdminToken =
        buildToken(tenantAdminId, "sub-am-admin", "am-admin@example.com", "運営者", "ウンエイシャ");
    assigneeToken =
        buildToken(assigneeId, "sub-am-assignee", "am-assignee@example.com", "担当者", "タントウシャ");
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
    if (tenantId == null) {
      return;
    }
    txTemplate.execute(
        ignored -> {
          em.createNativeQuery("DELETE FROM task_stakeholders WHERE tenant_id = ?")
              .setParameter(1, tenantId)
              .executeUpdate();
          em.createNativeQuery("DELETE FROM tasks WHERE tenant_id = ?")
              .setParameter(1, tenantId)
              .executeUpdate();
          em.createNativeQuery("DELETE FROM user_tenants WHERE tenant_id = ?")
              .setParameter(1, tenantId)
              .executeUpdate();
          em.createNativeQuery("DELETE FROM tenants WHERE id = ?")
              .setParameter(1, tenantId)
              .executeUpdate();
          em.createNativeQuery("DELETE FROM users WHERE id IN (?,?,?)")
              .setParameter(1, ownerId)
              .setParameter(2, assigneeId)
              .setParameter(3, tenantAdminId)
              .executeUpdate();
          return null;
        });
  }

  @Nested
  class TenantAdminHasNoBusinessTaskPrivilege {

    @Test
    void getPrivateTask_returns404() throws Exception {
      mockMvc
          .perform(
              get("/api/tasks/" + privateTaskId)
                  .header("X-Tenant-Id", String.valueOf(tenantId))
                  .with(authentication(tenantAdminToken)))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.code").value("E_NOT_FOUND"));
    }

    @Test
    void getStakeholdersTask_whenNotRegistered_returns404() throws Exception {
      mockMvc
          .perform(
              get("/api/tasks/" + stakeholdersTaskId)
                  .header("X-Tenant-Id", String.valueOf(tenantId))
                  .with(authentication(tenantAdminToken)))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.code").value("E_NOT_FOUND"));
    }

    @Test
    void getTenantTask_returns200_asOrdinaryMemberNotPrivilege() throws Exception {
      // TENANT 公開はメンバー全員に開かれている。運営者も「メンバーとして」参照できるだけで業務特権ではない。
      mockMvc
          .perform(
              get("/api/tasks/" + tenantTaskId)
                  .header("X-Tenant-Id", String.valueOf(tenantId))
                  .with(authentication(tenantAdminToken)))
          .andExpect(status().isOk());
    }

    @Test
    void editTenantTask_returns403() throws Exception {
      mockMvc
          .perform(
              patch("/api/tasks/" + tenantTaskId)
                  .header("X-Tenant-Id", String.valueOf(tenantId))
                  .header(HttpHeaders.IF_MATCH, IF_MATCH_V0)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"title\":\"運営者による編集\"}")
                  .with(authentication(tenantAdminToken)))
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.code").value("E_FORBIDDEN"));
    }

    @Test
    void deleteTenantTask_returns403() throws Exception {
      mockMvc
          .perform(
              delete("/api/tasks/" + tenantTaskId)
                  .header("X-Tenant-Id", String.valueOf(tenantId))
                  .header(HttpHeaders.IF_MATCH, IF_MATCH_V0)
                  .with(authentication(tenantAdminToken)))
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.code").value("E_FORBIDDEN"));
    }

    @Test
    void changeStatusTenantTask_returns403() throws Exception {
      mockMvc
          .perform(
              patch("/api/tasks/" + tenantTaskId + "/status")
                  .header("X-Tenant-Id", String.valueOf(tenantId))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"status\":\"IN_PROGRESS\"}")
                  .with(authentication(tenantAdminToken)))
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.code").value("E_FORBIDDEN"));
    }

    @Test
    void changeVisibilityTenantTask_returns403() throws Exception {
      mockMvc
          .perform(
              patch("/api/tasks/" + tenantTaskId + "/visibility")
                  .header("X-Tenant-Id", String.valueOf(tenantId))
                  .header(HttpHeaders.IF_MATCH, IF_MATCH_V0)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"visibility\":\"PRIVATE\"}")
                  .with(authentication(tenantAdminToken)))
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.code").value("E_FORBIDDEN"));
    }

    @Test
    void addStakeholderTenantTask_returns403() throws Exception {
      mockMvc
          .perform(
              post("/api/tasks/" + tenantTaskId + "/stakeholders")
                  .header("X-Tenant-Id", String.valueOf(tenantId))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"userId\":" + assigneeId + "}")
                  .with(authentication(tenantAdminToken)))
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.code").value("E_FORBIDDEN"));
    }

    @Test
    void listStakeholdersPrivateTask_returns404() throws Exception {
      mockMvc
          .perform(
              get("/api/tasks/" + privateTaskId + "/stakeholders")
                  .header("X-Tenant-Id", String.valueOf(tenantId))
                  .with(authentication(tenantAdminToken)))
          .andExpect(status().isNotFound());
    }
  }

  @Nested
  class AssigneeLimitedToSharedOperations {

    @Test
    void editStakeholdersTask_returns403() throws Exception {
      // 担当者は STAKEHOLDERS タスクを参照できる(404 にならない)が、編集は所有者専用 → 403
      mockMvc
          .perform(
              patch("/api/tasks/" + stakeholdersTaskId)
                  .header("X-Tenant-Id", String.valueOf(tenantId))
                  .header(HttpHeaders.IF_MATCH, IF_MATCH_V0)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"title\":\"担当者による編集\"}")
                  .with(authentication(assigneeToken)))
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.code").value("E_FORBIDDEN"));
    }

    @Test
    void deleteStakeholdersTask_returns403() throws Exception {
      mockMvc
          .perform(
              delete("/api/tasks/" + stakeholdersTaskId)
                  .header("X-Tenant-Id", String.valueOf(tenantId))
                  .header(HttpHeaders.IF_MATCH, IF_MATCH_V0)
                  .with(authentication(assigneeToken)))
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.code").value("E_FORBIDDEN"));
    }

    @Test
    void changeVisibilityStakeholdersTask_returns403() throws Exception {
      mockMvc
          .perform(
              patch("/api/tasks/" + stakeholdersTaskId + "/visibility")
                  .header("X-Tenant-Id", String.valueOf(tenantId))
                  .header(HttpHeaders.IF_MATCH, IF_MATCH_V0)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"visibility\":\"TENANT\"}")
                  .with(authentication(assigneeToken)))
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.code").value("E_FORBIDDEN"));
    }

    @Test
    void changeStatusStakeholdersTask_returns200() throws Exception {
      // ステータス変更は所有者・担当者の共有操作 → 担当者は許可される
      mockMvc
          .perform(
              patch("/api/tasks/" + stakeholdersTaskId + "/status")
                  .header("X-Tenant-Id", String.valueOf(tenantId))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"status\":\"IN_PROGRESS\"}")
                  .with(authentication(assigneeToken)))
          .andExpect(status().isOk());
    }
  }

  // ---- helpers ----

  private void insertUserTenant(Long userId, String role) {
    em.createNativeQuery(
            "INSERT INTO user_tenants (user_id, tenant_id, role, status, joined_at)"
                + " VALUES (?,?,?,?,?)")
        .setParameter(1, userId)
        .setParameter(2, tenantId)
        .setParameter(3, role)
        .setParameter(4, "ACTIVE")
        .setParameter(5, LocalDateTime.of(2026, 1, 1, 0, 0))
        .executeUpdate();
  }

  private Long insertTask(String title, String visibility, Long taskAssigneeId) {
    em.createNativeQuery(
            "INSERT INTO tasks"
                + " (tenant_id, owner_id, assignee_id, title, status, priority, visibility,"
                + " due_date, version, created_at, updated_at, created_by, updated_by)"
                + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)")
        .setParameter(1, tenantId)
        .setParameter(2, ownerId)
        .setParameter(3, taskAssigneeId)
        .setParameter(4, title)
        .setParameter(5, "NOT_STARTED")
        .setParameter(6, "MEDIUM")
        .setParameter(7, visibility)
        .setParameter(8, LocalDate.of(2026, 12, 31))
        .setParameter(9, 0L)
        .setParameter(10, LocalDateTime.now(AppZones.JST))
        .setParameter(11, LocalDateTime.now(AppZones.JST))
        .setParameter(12, ownerId)
        .setParameter(13, ownerId)
        .executeUpdate();
    return ((Number) em.createNativeQuery("SELECT LAST_INSERT_ID()").getSingleResult()).longValue();
  }

  private TasksAuthenticationToken buildToken(
      Long userId, String sub, String email, String fullName, String fullNameKana) {
    var principal = new TasksPrincipal(userId, sub, email, fullName, fullNameKana, null);
    return new TasksAuthenticationToken(principal, List.of());
  }
}
