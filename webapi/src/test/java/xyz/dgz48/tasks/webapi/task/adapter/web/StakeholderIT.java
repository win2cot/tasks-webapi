package xyz.dgz48.tasks.webapi.task.adapter.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
 * 関係者 API(listStakeholders / addStakeholder / removeStakeholder)の統合 IT。
 *
 * <p>visibility × 認可マトリクス(所有者 / 担当者 / 関係者 / 無関係 Member / 他テナント)を網羅する。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({
  TestcontainersConfiguration.class,
  MockJwtDecoderConfiguration.class,
  FixedClockConfiguration.class
})
class StakeholderIT {

  @Autowired MockMvc mockMvc;
  @Autowired EntityManager em;
  @Autowired TransactionTemplate txTemplate;

  // ユーザー
  private Long ownerId;
  private Long assigneeId;
  private Long memberUserId;
  private Long otherTenantUserId;

  // テナント
  private Long tenantId;
  private Long otherTenantId;

  // タスク(visibility 別)
  private Long tenantTaskId;
  private Long stakeholdersTaskId;
  private Long privateTaskId;

  private TasksAuthenticationToken ownerToken;
  private TasksAuthenticationToken assigneeToken;
  private TasksAuthenticationToken memberToken;

  @BeforeEach
  void setUp() {
    txTemplate.execute(
        ignored -> {
          var ownerEntity =
              new UserJpaEntity(
                  "sub-sh-owner", "sh-owner@example.com", "所有者 太郎", "ショユウシャ タロウ", null);
          var assigneeEntity =
              new UserJpaEntity(
                  "sub-sh-assignee", "sh-assignee@example.com", "担当者 太郎", "タントウシャ タロウ", null);
          var memberEntity =
              new UserJpaEntity(
                  "sub-sh-member", "sh-member@example.com", "メンバー 太郎", "メンバー タロウ", null);
          var otherTenantUserEntity =
              new UserJpaEntity(
                  "sub-sh-other", "sh-other@example.com", "他テナント太郎", "タテナント タロウ", null);

          em.persist(ownerEntity);
          em.persist(assigneeEntity);
          em.persist(memberEntity);
          em.persist(otherTenantUserEntity);
          em.flush();

          ownerId = ownerEntity.getId();
          assigneeId = assigneeEntity.getId();
          memberUserId = memberEntity.getId();
          otherTenantUserId = otherTenantUserEntity.getId();

          // Auditing に必要な SecurityContext
          var ownerPrincipal =
              new TasksPrincipal(
                  ownerId, "sub-sh-owner", "sh-owner@example.com", "所有者 太郎", "ショユウシャ タロウ", null);
          SecurityContextHolder.getContext()
              .setAuthentication(new TasksAuthenticationToken(ownerPrincipal, List.of()));

          var tenantEntity = new TenantJpaEntity("SH-TENANT", "関係者テナント");
          var otherTenantEntity = new TenantJpaEntity("SH-OTHER", "他テナント");
          em.persist(tenantEntity);
          em.persist(otherTenantEntity);
          em.flush();
          tenantId = tenantEntity.getId();
          otherTenantId = otherTenantEntity.getId();

          insertUserTenant(ownerId, tenantId, "TENANT_ADMIN");
          insertUserTenant(assigneeId, tenantId, "MEMBER");
          insertUserTenant(memberUserId, tenantId, "MEMBER");
          insertUserTenant(otherTenantUserId, otherTenantId, "MEMBER");

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

          // STAKEHOLDERS/PRIVATE は直接 SQL で visibility をセット
          em.createNativeQuery(
                  "INSERT INTO tasks"
                      + " (tenant_id, owner_id, assignee_id, title, status, priority, visibility, due_date, version, created_at, updated_at, created_by, updated_by)"
                      + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)")
              .setParameter(1, tenantId)
              .setParameter(2, ownerId)
              .setParameter(3, assigneeId)
              .setParameter(4, "STAKEHOLDERS可視タスク")
              .setParameter(5, "NOT_STARTED")
              .setParameter(6, "MEDIUM")
              .setParameter(7, "STAKEHOLDERS")
              .setParameter(8, LocalDate.of(2026, 12, 31))
              .setParameter(9, 0L)
              .setParameter(10, LocalDateTime.now(AppZones.JST))
              .setParameter(11, LocalDateTime.now(AppZones.JST))
              .setParameter(12, ownerId)
              .setParameter(13, ownerId)
              .executeUpdate();

          stakeholdersTaskId =
              ((Number) em.createNativeQuery("SELECT LAST_INSERT_ID()").getSingleResult())
                  .longValue();

          em.createNativeQuery(
                  "INSERT INTO tasks"
                      + " (tenant_id, owner_id, assignee_id, title, status, priority, visibility, due_date, version, created_at, updated_at, created_by, updated_by)"
                      + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)")
              .setParameter(1, tenantId)
              .setParameter(2, ownerId)
              .setParameter(3, assigneeId)
              .setParameter(4, "PRIVATE可視タスク")
              .setParameter(5, "NOT_STARTED")
              .setParameter(6, "MEDIUM")
              .setParameter(7, "PRIVATE")
              .setParameter(8, LocalDate.of(2026, 12, 31))
              .setParameter(9, 0L)
              .setParameter(10, LocalDateTime.now(AppZones.JST))
              .setParameter(11, LocalDateTime.now(AppZones.JST))
              .setParameter(12, ownerId)
              .setParameter(13, ownerId)
              .executeUpdate();

          privateTaskId =
              ((Number) em.createNativeQuery("SELECT LAST_INSERT_ID()").getSingleResult())
                  .longValue();

          return null;
        });

    SecurityContextHolder.clearContext();

    ownerToken = buildToken(ownerId, "sub-sh-owner", "sh-owner@example.com", "所有者 太郎");
    assigneeToken = buildToken(assigneeId, "sub-sh-assignee", "sh-assignee@example.com", "担当者 太郎");
    memberToken = buildToken(memberUserId, "sub-sh-member", "sh-member@example.com", "メンバー 太郎");
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
    txTemplate.execute(
        ignored -> {
          em.createNativeQuery("DELETE FROM task_stakeholders WHERE task_id IN (?,?,?)")
              .setParameter(1, tenantTaskId)
              .setParameter(2, stakeholdersTaskId)
              .setParameter(3, privateTaskId)
              .executeUpdate();
          em.createNativeQuery("DELETE FROM tasks WHERE id IN (?,?,?)")
              .setParameter(1, tenantTaskId)
              .setParameter(2, stakeholdersTaskId)
              .setParameter(3, privateTaskId)
              .executeUpdate();
          em.createNativeQuery("DELETE FROM user_tenants WHERE tenant_id IN (?,?)")
              .setParameter(1, tenantId)
              .setParameter(2, otherTenantId)
              .executeUpdate();
          em.createNativeQuery("DELETE FROM tenants WHERE id IN (?,?)")
              .setParameter(1, tenantId)
              .setParameter(2, otherTenantId)
              .executeUpdate();
          em.createNativeQuery("DELETE FROM users WHERE id IN (?,?,?,?)")
              .setParameter(1, ownerId)
              .setParameter(2, assigneeId)
              .setParameter(3, memberUserId)
              .setParameter(4, otherTenantUserId)
              .executeUpdate();
          return null;
        });
  }

  @Nested
  class ListStakeholders {

    @Test
    void tenantVisibility_allMembersCanList() throws Exception {
      mockMvc
          .perform(
              get("/api/tasks/" + tenantTaskId + "/stakeholders")
                  .header("X-Tenant-Id", String.valueOf(tenantId))
                  .with(authentication(memberToken)))
          .andExpect(status().isOk());
    }

    @Test
    void stakeholdersVisibility_ownerCanList() throws Exception {
      mockMvc
          .perform(
              get("/api/tasks/" + stakeholdersTaskId + "/stakeholders")
                  .header("X-Tenant-Id", String.valueOf(tenantId))
                  .with(authentication(ownerToken)))
          .andExpect(status().isOk());
    }

    @Test
    void stakeholdersVisibility_assigneeCanList() throws Exception {
      mockMvc
          .perform(
              get("/api/tasks/" + stakeholdersTaskId + "/stakeholders")
                  .header("X-Tenant-Id", String.valueOf(tenantId))
                  .with(authentication(assigneeToken)))
          .andExpect(status().isOk());
    }

    @Test
    void stakeholdersVisibility_nonStakeholderMemberGets404() throws Exception {
      mockMvc
          .perform(
              get("/api/tasks/" + stakeholdersTaskId + "/stakeholders")
                  .header("X-Tenant-Id", String.valueOf(tenantId))
                  .with(authentication(memberToken)))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.code").value("E_NOT_FOUND"));
    }

    @Test
    void stakeholdersVisibility_registeredStakeholderCanList() throws Exception {
      insertStakeholder(stakeholdersTaskId, memberUserId, tenantId, ownerId);

      try {
        mockMvc
            .perform(
                get("/api/tasks/" + stakeholdersTaskId + "/stakeholders")
                    .header("X-Tenant-Id", String.valueOf(tenantId))
                    .with(authentication(memberToken)))
            .andExpect(status().isOk());
      } finally {
        removeStakeholder(stakeholdersTaskId, memberUserId);
      }
    }

    @Test
    void privateVisibility_memberGets404() throws Exception {
      mockMvc
          .perform(
              get("/api/tasks/" + privateTaskId + "/stakeholders")
                  .header("X-Tenant-Id", String.valueOf(tenantId))
                  .with(authentication(memberToken)))
          .andExpect(status().isNotFound());
    }

    @Test
    void privateVisibility_stakeholderRegistrationDoesNotGrantAccess() throws Exception {
      insertStakeholder(privateTaskId, memberUserId, tenantId, ownerId);

      try {
        mockMvc
            .perform(
                get("/api/tasks/" + privateTaskId + "/stakeholders")
                    .header("X-Tenant-Id", String.valueOf(tenantId))
                    .with(authentication(memberToken)))
            .andExpect(status().isNotFound());
      } finally {
        removeStakeholder(privateTaskId, memberUserId);
      }
    }
  }

  @Nested
  class AddStakeholder {

    @Test
    void owner_canAddStakeholder() throws Exception {
      mockMvc
          .perform(
              post("/api/tasks/" + tenantTaskId + "/stakeholders")
                  .header("X-Tenant-Id", String.valueOf(tenantId))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"userId\":" + memberUserId + "}")
                  .with(authentication(ownerToken)))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.userId").value(memberUserId));
    }

    @Test
    void assignee_canAddStakeholder() throws Exception {
      // stakeholdersTaskId は assignee_id = assigneeId で作成されている
      mockMvc
          .perform(
              post("/api/tasks/" + stakeholdersTaskId + "/stakeholders")
                  .header("X-Tenant-Id", String.valueOf(tenantId))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"userId\":" + memberUserId + "}")
                  .with(authentication(assigneeToken)))
          .andExpect(status().isCreated());
    }

    @Test
    void nonOwnerMember_gets403() throws Exception {
      mockMvc
          .perform(
              post("/api/tasks/" + tenantTaskId + "/stakeholders")
                  .header("X-Tenant-Id", String.valueOf(tenantId))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"userId\":" + memberUserId + "}")
                  .with(authentication(memberToken)))
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.code").value("E_FORBIDDEN"));
    }

    @Test
    void duplicateAdd_gets409() throws Exception {
      insertStakeholder(tenantTaskId, memberUserId, tenantId, ownerId);

      try {
        mockMvc
            .perform(
                post("/api/tasks/" + tenantTaskId + "/stakeholders")
                    .header("X-Tenant-Id", String.valueOf(tenantId))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"userId\":" + memberUserId + "}")
                    .with(authentication(ownerToken)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("E_CONFLICT"));
      } finally {
        removeStakeholder(tenantTaskId, memberUserId);
      }
    }

    @Test
    void crossTenantUser_gets403() throws Exception {
      mockMvc
          .perform(
              post("/api/tasks/" + tenantTaskId + "/stakeholders")
                  .header("X-Tenant-Id", String.valueOf(tenantId))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"userId\":" + otherTenantUserId + "}")
                  .with(authentication(ownerToken)))
          .andExpect(status().isForbidden());
    }
  }

  @Nested
  class RemoveStakeholder {

    @Test
    void owner_canRemoveStakeholder() throws Exception {
      insertStakeholder(tenantTaskId, memberUserId, tenantId, ownerId);

      mockMvc
          .perform(
              delete("/api/tasks/" + tenantTaskId + "/stakeholders/" + memberUserId)
                  .header("X-Tenant-Id", String.valueOf(tenantId))
                  .with(authentication(ownerToken)))
          .andExpect(status().isNoContent());
    }

    @Test
    void assignee_canRemoveStakeholder() throws Exception {
      // stakeholdersTaskId は assignee_id = assigneeId で作成されている
      insertStakeholder(stakeholdersTaskId, memberUserId, tenantId, ownerId);

      mockMvc
          .perform(
              delete("/api/tasks/" + stakeholdersTaskId + "/stakeholders/" + memberUserId)
                  .header("X-Tenant-Id", String.valueOf(tenantId))
                  .with(authentication(assigneeToken)))
          .andExpect(status().isNoContent());
    }

    @Test
    void nonOwnerMember_gets403() throws Exception {
      insertStakeholder(tenantTaskId, ownerId, tenantId, ownerId);

      try {
        mockMvc
            .perform(
                delete("/api/tasks/" + tenantTaskId + "/stakeholders/" + ownerId)
                    .header("X-Tenant-Id", String.valueOf(tenantId))
                    .with(authentication(memberToken)))
            .andExpect(status().isForbidden());
      } finally {
        removeStakeholder(tenantTaskId, ownerId);
      }
    }

    @Test
    void notRegistered_gets404() throws Exception {
      mockMvc
          .perform(
              delete("/api/tasks/" + tenantTaskId + "/stakeholders/" + memberUserId)
                  .header("X-Tenant-Id", String.valueOf(tenantId))
                  .with(authentication(ownerToken)))
          .andExpect(status().isNotFound());
    }
  }

  // ---- helpers ----

  private void insertUserTenant(Long userId, Long tenantId, String role) {
    em.createNativeQuery(
            "INSERT INTO user_tenants (user_id, tenant_id, role, status, joined_at) VALUES (?,?,?,?,?)")
        .setParameter(1, userId)
        .setParameter(2, tenantId)
        .setParameter(3, role)
        .setParameter(4, "ACTIVE")
        .setParameter(5, LocalDateTime.of(2026, 1, 1, 0, 0))
        .executeUpdate();
  }

  private void insertStakeholder(Long taskId, Long userId, Long tenantId, Long addedBy) {
    txTemplate.execute(
        ignored -> {
          em.createNativeQuery(
                  "INSERT INTO task_stakeholders (task_id, user_id, tenant_id, added_by, added_at)"
                      + " VALUES (?,?,?,?,?)")
              .setParameter(1, taskId)
              .setParameter(2, userId)
              .setParameter(3, tenantId)
              .setParameter(4, addedBy)
              .setParameter(5, LocalDateTime.now(AppZones.JST))
              .executeUpdate();
          return null;
        });
  }

  private void removeStakeholder(Long taskId, Long userId) {
    txTemplate.execute(
        ignored -> {
          em.createNativeQuery("DELETE FROM task_stakeholders WHERE task_id = ? AND user_id = ?")
              .setParameter(1, taskId)
              .setParameter(2, userId)
              .executeUpdate();
          return null;
        });
  }

  private TasksAuthenticationToken buildToken(
      Long userId, String sub, String email, String fullName) {
    var principal = new TasksPrincipal(userId, sub, email, fullName, fullName + "カナ", null);
    return new TasksAuthenticationToken(principal, List.of());
  }
}
