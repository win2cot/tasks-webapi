package xyz.dgz48.tasks.webapi.task.adapter.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

@SpringBootTest
@AutoConfigureMockMvc
@Import({
  TestcontainersConfiguration.class,
  MockJwtDecoderConfiguration.class,
  FixedClockConfiguration.class
})
class DeleteTaskIT {

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
              new UserJpaEntity("sub-del-it", "del-it@example.com", "削除 太郎", "サクジョ タロウ", null);
          em.persist(user);
          var other =
              new UserJpaEntity("sub-del-other", "del-other@example.com", "他人 次郎", "タニン ジロウ", null);
          em.persist(other);
          em.flush();
          userId = user.getId();
          otherUserId = other.getId();

          var principal =
              new TasksPrincipal(
                  userId, "sub-del-it", "del-it@example.com", "削除 太郎", "サクジョ タロウ", null);
          SecurityContextHolder.getContext()
              .setAuthentication(new TasksAuthenticationToken(principal, List.of()));

          var tenant = new TenantJpaEntity("DEL-IT-1", "削除IT");
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
                  "削除テスト",
                  null,
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
        new TasksPrincipal(userId, "sub-del-it", "del-it@example.com", "削除 太郎", "サクジョ タロウ", null);
    ownerToken = new TasksAuthenticationToken(principal, List.of());

    var otherPrincipal =
        new TasksPrincipal(
            otherUserId, "sub-del-other", "del-other@example.com", "他人 次郎", "タニン ジロウ", null);
    otherToken = new TasksAuthenticationToken(otherPrincipal, List.of());
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
    if (userId == null) {
      return;
    }
    TenantContext.set(tenantId);
    try {
      txTemplate.execute(
          ignored -> {
            em.createNativeQuery("DELETE FROM audit_logs WHERE tenant_id = ?")
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
            em.createNativeQuery("DELETE FROM users WHERE id = ? OR id = ?")
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
  void deleteTask_returns204_whenOwnerWithMatchingVersion() throws Exception {
    mockMvc
        .perform(
            delete("/api/tasks/" + taskId)
                .header("X-Tenant-Id", String.valueOf(tenantId))
                .header(HttpHeaders.IF_MATCH, "W/\"0\"")
                .with(authentication(ownerToken)))
        .andExpect(status().isNoContent());

    // 削除後は listTasks に現れない
    mockMvc
        .perform(
            get("/api/tasks")
                .header("X-Tenant-Id", String.valueOf(tenantId))
                .with(authentication(ownerToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[?(@.id == " + taskId + ")]").doesNotExist());
  }

  @Test
  void deleteTask_returns404_whenTaskNotFound() throws Exception {
    mockMvc
        .perform(
            delete("/api/tasks/99999999")
                .header("X-Tenant-Id", String.valueOf(tenantId))
                .header(HttpHeaders.IF_MATCH, "W/\"0\"")
                .with(authentication(ownerToken)))
        .andExpect(status().isNotFound());
  }

  @Test
  void deleteTask_returns403_whenNotOwner() throws Exception {
    mockMvc
        .perform(
            delete("/api/tasks/" + taskId)
                .header("X-Tenant-Id", String.valueOf(tenantId))
                .header(HttpHeaders.IF_MATCH, "W/\"0\"")
                .with(authentication(otherToken)))
        .andExpect(status().isForbidden());
  }

  @Test
  void deleteTask_returns412_whenVersionMismatch() throws Exception {
    mockMvc
        .perform(
            delete("/api/tasks/" + taskId)
                .header("X-Tenant-Id", String.valueOf(tenantId))
                .header(HttpHeaders.IF_MATCH, "W/\"99\"")
                .with(authentication(ownerToken)))
        .andExpect(status().isPreconditionFailed())
        .andExpect(jsonPath("$.code").value("E_PRECONDITION_FAILED"));
  }

  @Test
  void deleteTask_returns400_whenIfMatchMissing() throws Exception {
    mockMvc
        .perform(
            delete("/api/tasks/" + taskId)
                .header("X-Tenant-Id", String.valueOf(tenantId))
                .with(authentication(ownerToken)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("E_VALIDATION"));
  }
}
