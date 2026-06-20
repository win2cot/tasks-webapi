package xyz.dgz48.tasks.webapi.task.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
import xyz.dgz48.tasks.webapi.tenant.adapter.persistence.TenantJpaEntity;
import xyz.dgz48.tasks.webapi.user.adapter.persistence.UserJpaEntity;

/**
 * PATCH /api/tasks/{id} の統合 IT。ADR-0012(ETag/If-Match)/ ADR-0013(監査ログ diff)/ ADR-0014(JsonNullable)
 * の受入基準を検証する。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({
  TestcontainersConfiguration.class,
  MockJwtDecoderConfiguration.class,
  FixedClockConfiguration.class
})
class PatchTaskIT {

  @Autowired MockMvc mockMvc;
  @Autowired EntityManager em;
  @Autowired TransactionTemplate txTemplate;

  private Long userId;
  private Long otherUserId;
  private Long tenantId;
  private Long taskId;
  private TasksAuthenticationToken ownerToken;
  private TasksAuthenticationToken otherToken;

  @BeforeEach
  void setUp() {
    txTemplate.execute(
        ignored -> {
          var user =
              new UserJpaEntity(
                  "sub-patch-it", "patch-it@example.com", "PATCH 太郎", "パッチ タロウ", null);
          em.persist(user);
          var other =
              new UserJpaEntity(
                  "sub-patch-other", "patch-other@example.com", "他 ユーザー", "タ ユーザー", null);
          em.persist(other);
          em.flush();
          userId = user.getId();
          otherUserId = other.getId();

          var principal =
              new TasksPrincipal(
                  userId, "sub-patch-it", "patch-it@example.com", "PATCH 太郎", "パッチ タロウ", null);
          SecurityContextHolder.getContext()
              .setAuthentication(new TasksAuthenticationToken(principal, List.of()));

          var tenant = new TenantJpaEntity("PATCH-IT-1", "PatchIT");
          em.persist(tenant);
          em.flush();
          tenantId = tenant.getId();

          em.createNativeQuery(
                  "INSERT INTO user_tenants"
                      + " (user_id, tenant_id, role, status, joined_at) VALUES (?,?,?,?,?)")
              .setParameter(1, userId)
              .setParameter(2, tenantId)
              .setParameter(3, "MEMBER")
              .setParameter(4, "ACTIVE")
              .setParameter(5, LocalDateTime.of(2026, 1, 1, 0, 0))
              .executeUpdate();
          em.createNativeQuery(
                  "INSERT INTO user_tenants"
                      + " (user_id, tenant_id, role, status, joined_at) VALUES (?,?,?,?,?)")
              .setParameter(1, otherUserId)
              .setParameter(2, tenantId)
              .setParameter(3, "MEMBER")
              .setParameter(4, "ACTIVE")
              .setParameter(5, LocalDateTime.of(2026, 1, 1, 0, 0))
              .executeUpdate();

          var task =
              new TaskJpaEntity(
                  tenantId,
                  userId,
                  "Original title",
                  "Original description",
                  TaskStatus.NOT_STARTED,
                  Priority.MEDIUM,
                  LocalDate.of(2026, 12, 31));
          em.persist(task);
          em.flush();
          taskId = task.getId();

          return null;
        });

    SecurityContextHolder.clearContext();

    var principal =
        new TasksPrincipal(
            userId, "sub-patch-it", "patch-it@example.com", "PATCH 太郎", "パッチ タロウ", null);
    ownerToken = new TasksAuthenticationToken(principal, List.of());

    var otherPrincipal =
        new TasksPrincipal(
            otherUserId, "sub-patch-other", "patch-other@example.com", "他 ユーザー", "タ ユーザー", null);
    otherToken = new TasksAuthenticationToken(otherPrincipal, List.of());
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
    if (taskId == null) return;
    TenantContext.set(tenantId);
    try {
      txTemplate.execute(
          ignored -> {
            em.createNativeQuery("DELETE FROM audit_logs WHERE tenant_id = ?")
                .setParameter(1, tenantId)
                .executeUpdate();
            em.createNativeQuery("DELETE FROM tasks WHERE id = ?")
                .setParameter(1, taskId)
                .executeUpdate();
            em.createNativeQuery("DELETE FROM user_tenants WHERE tenant_id = ?")
                .setParameter(1, tenantId)
                .executeUpdate();
            em.createNativeQuery("DELETE FROM tenants WHERE id = ?")
                .setParameter(1, tenantId)
                .executeUpdate();
            em.createNativeQuery("DELETE FROM users WHERE id IN (?,?)")
                .setParameter(1, userId)
                .setParameter(2, otherUserId)
                .executeUpdate();
            return null;
          });
    } finally {
      TenantContext.clear();
    }
  }

  @Test
  void patchTask_returns400_whenIfMatchHeaderMissing() throws Exception {
    mockMvc
        .perform(
            patch("/api/tasks/" + taskId)
                .header("X-Tenant-Id", String.valueOf(tenantId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"New title\"}")
                .with(authentication(ownerToken)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("E_VALIDATION"));
  }

  @Test
  void patchTask_returns412_whenIfMatchVersionStale() throws Exception {
    mockMvc
        .perform(
            patch("/api/tasks/" + taskId)
                .header("X-Tenant-Id", String.valueOf(tenantId))
                .header(HttpHeaders.IF_MATCH, "W/\"99\"")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"New title\"}")
                .with(authentication(ownerToken)))
        .andExpect(status().isPreconditionFailed())
        .andExpect(jsonPath("$.code").value("E_PRECONDITION_FAILED"));
  }

  @Test
  void patchTask_returns403_whenNotOwner() throws Exception {
    mockMvc
        .perform(
            patch("/api/tasks/" + taskId)
                .header("X-Tenant-Id", String.valueOf(tenantId))
                .header(HttpHeaders.IF_MATCH, "W/\"0\"")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"New title\"}")
                .with(authentication(otherToken)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("E_FORBIDDEN"));
  }

  @Test
  void patchTask_updatesTitle_andReturnsNewEtag() throws Exception {
    mockMvc
        .perform(
            patch("/api/tasks/" + taskId)
                .header("X-Tenant-Id", String.valueOf(tenantId))
                .header(HttpHeaders.IF_MATCH, "W/\"0\"")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Updated title\"}")
                .with(authentication(ownerToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("Updated title"))
        .andExpect(jsonPath("$.description").value("Original description"))
        .andExpect(jsonPath("$.version").value(1))
        .andExpect(header().string(HttpHeaders.ETAG, "W/\"1\""))
        .andExpect(jsonPath("$.owner.id").value(userId))
        .andExpect(jsonPath("$.owner.fullName").value("PATCH 太郎"))
        .andExpect(jsonPath("$.editable").value(true))
        .andExpect(jsonPath("$.deletable").value(true));
  }

  @Test
  void patchTask_clearsDescription_whenExplicitNull() throws Exception {
    mockMvc
        .perform(
            patch("/api/tasks/" + taskId)
                .header("X-Tenant-Id", String.valueOf(tenantId))
                .header(HttpHeaders.IF_MATCH, "W/\"0\"")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\":null}")
                .with(authentication(ownerToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("Original title"))
        .andExpect(jsonPath("$.description").doesNotExist())
        .andExpect(jsonPath("$.version").value(1));
  }

  @Test
  void patchTask_isNoop_whenAllFieldsUndefined() throws Exception {
    mockMvc
        .perform(
            patch("/api/tasks/" + taskId)
                .header("X-Tenant-Id", String.valueOf(tenantId))
                .header(HttpHeaders.IF_MATCH, "W/\"0\"")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .with(authentication(ownerToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("Original title"))
        .andExpect(jsonPath("$.version").value(0));
  }

  @Test
  void patchTask_recordsAuditLog_whenFieldChanges() throws Exception {
    mockMvc
        .perform(
            patch("/api/tasks/" + taskId)
                .header("X-Tenant-Id", String.valueOf(tenantId))
                .header(HttpHeaders.IF_MATCH, "W/\"0\"")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Audited title\",\"priority\":\"HIGH\"}")
                .with(authentication(ownerToken)))
        .andExpect(status().isOk());

    txTemplate.execute(
        ignored -> {
          List<?> logs =
              em.createNativeQuery(
                      "SELECT detail FROM audit_logs WHERE tenant_id = ? AND action = 'TASK_UPDATED'")
                  .setParameter(1, tenantId)
                  .getResultList();
          assertThat(logs).hasSize(1);
          String detail = (String) logs.get(0);
          assertThat(detail).contains("title");
          assertThat(detail).contains("priority");
          return null;
        });
  }

  @Test
  void patchTask_returns400_whenTitleExceedsMaxLength() throws Exception {
    String longTitle = "a".repeat(101);
    mockMvc
        .perform(
            patch("/api/tasks/" + taskId)
                .header("X-Tenant-Id", String.valueOf(tenantId))
                .header(HttpHeaders.IF_MATCH, "W/\"0\"")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"" + longTitle + "\"}")
                .with(authentication(ownerToken)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("E_VALIDATION"));
  }

  @Test
  void patchTask_returns400_whenDescriptionExceedsMaxLength() throws Exception {
    String longDescription = "a".repeat(2001);
    mockMvc
        .perform(
            patch("/api/tasks/" + taskId)
                .header("X-Tenant-Id", String.valueOf(tenantId))
                .header(HttpHeaders.IF_MATCH, "W/\"0\"")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\":\"" + longDescription + "\"}")
                .with(authentication(ownerToken)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("E_VALIDATION"));
  }

  @Test
  void patchTask_concurrentRequests_oneGets412() throws Exception {
    var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
    java.util.concurrent.Callable<org.springframework.test.web.servlet.MvcResult> req =
        () ->
            mockMvc
                .perform(
                    patch("/api/tasks/" + taskId)
                        .header("X-Tenant-Id", String.valueOf(tenantId))
                        .header(HttpHeaders.IF_MATCH, "W/\"0\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Concurrent\"}")
                        .with(authentication(ownerToken)))
                .andReturn();

    var f1 = executor.submit(req);
    var f2 = executor.submit(req);
    int s1 = f1.get().getResponse().getStatus();
    int s2 = f2.get().getResponse().getStatus();
    executor.shutdown();

    assertThat(List.of(s1, s2)).as("一方が 200、もう一方が 412 であること").containsExactlyInAnyOrder(200, 412);
  }
}
