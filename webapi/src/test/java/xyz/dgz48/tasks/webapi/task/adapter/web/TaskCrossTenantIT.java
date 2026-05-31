package xyz.dgz48.tasks.webapi.task.adapter.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import xyz.dgz48.tasks.webapi.FixedClockConfiguration;
import xyz.dgz48.tasks.webapi.MockJwtDecoderConfiguration;
import xyz.dgz48.tasks.webapi.TestcontainersConfiguration;
import xyz.dgz48.tasks.webapi.security.adapter.web.TasksAuthenticationToken;
import xyz.dgz48.tasks.webapi.security.domain.TasksPrincipal;
import xyz.dgz48.tasks.webapi.task.adapter.persistence.TaskJpaEntity;
import xyz.dgz48.tasks.webapi.task.domain.Priority;
import xyz.dgz48.tasks.webapi.task.domain.TaskStatus;
import xyz.dgz48.tasks.webapi.tenant.adapter.persistence.TenantJpaEntity;
import xyz.dgz48.tasks.webapi.user.adapter.persistence.UserJpaEntity;

/**
 * クロステナント分離の統合 IT。
 *
 * <p>GET /api/tasks/{id} で別テナントのタスクへのアクセスが 404 を返すことを検証する(設計規約 §9 マルチテナント分離要件)。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({
  TestcontainersConfiguration.class,
  MockJwtDecoderConfiguration.class,
  FixedClockConfiguration.class
})
class TaskCrossTenantIT {

  @Autowired MockMvc mockMvc;
  @Autowired EntityManager em;
  @Autowired TransactionTemplate txTemplate;

  private Long userId;
  private Long tenantAId;
  private Long tenantBId;
  private Long taskAId;
  private Long taskBId;
  private TasksAuthenticationToken authToken;

  @BeforeEach
  void setUp() {
    txTemplate.execute(
        ignored -> {
          var user = new UserJpaEntity("sub-ct", "ct@example.com", "クロステナント太郎", "クロステナントタロウ", null);
          em.persist(user);
          em.flush();
          userId = user.getId();

          var principal =
              new TasksPrincipal(
                  userId, "sub-ct", "ct@example.com", "クロステナント太郎", "クロステナントタロウ", null);
          SecurityContextHolder.getContext()
              .setAuthentication(new TasksAuthenticationToken(principal, List.of()));

          var tenantA = new TenantJpaEntity("CT-A", "クロステナントA");
          var tenantB = new TenantJpaEntity("CT-B", "クロステナントB");
          em.persist(tenantA);
          em.persist(tenantB);
          em.flush();
          tenantAId = tenantA.getId();
          tenantBId = tenantB.getId();

          em.createNativeQuery(
                  "INSERT INTO user_tenants"
                      + " (user_id, tenant_id, role, status, joined_at) VALUES (?,?,?,?,?)")
              .setParameter(1, userId)
              .setParameter(2, tenantAId)
              .setParameter(3, "MEMBER")
              .setParameter(4, "ACTIVE")
              .setParameter(5, LocalDateTime.of(2026, 1, 1, 0, 0))
              .executeUpdate();

          var taskA =
              new TaskJpaEntity(
                  tenantAId,
                  userId,
                  "テナントAのタスク",
                  null,
                  TaskStatus.NOT_STARTED,
                  Priority.MEDIUM,
                  LocalDate.of(2026, 12, 31));
          var taskB =
              new TaskJpaEntity(
                  tenantBId,
                  userId,
                  "テナントBのタスク",
                  null,
                  TaskStatus.NOT_STARTED,
                  Priority.MEDIUM,
                  LocalDate.of(2026, 12, 31));
          em.persist(taskA);
          em.persist(taskB);
          em.flush();
          taskAId = taskA.getId();
          taskBId = taskB.getId();

          return null;
        });

    SecurityContextHolder.clearContext();

    var principal =
        new TasksPrincipal(userId, "sub-ct", "ct@example.com", "クロステナント太郎", "クロステナントタロウ", null);
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
          em.createNativeQuery("DELETE FROM tasks WHERE id = ?")
              .setParameter(1, taskAId)
              .executeUpdate();
          em.createNativeQuery("DELETE FROM tasks WHERE id = ?")
              .setParameter(1, taskBId)
              .executeUpdate();
          em.createNativeQuery("DELETE FROM user_tenants WHERE user_id = ? AND tenant_id = ?")
              .setParameter(1, userId)
              .setParameter(2, tenantAId)
              .executeUpdate();
          em.createNativeQuery("DELETE FROM tenants WHERE id = ?")
              .setParameter(1, tenantAId)
              .executeUpdate();
          em.createNativeQuery("DELETE FROM tenants WHERE id = ?")
              .setParameter(1, tenantBId)
              .executeUpdate();
          em.createNativeQuery("DELETE FROM users WHERE id = ?")
              .setParameter(1, userId)
              .executeUpdate();
          return null;
        });
  }

  @Test
  void crossTenantAccess_returns404() throws Exception {
    mockMvc
        .perform(
            get("/api/tasks/" + taskBId)
                .header("X-Tenant-Id", String.valueOf(tenantAId))
                .with(authentication(authToken)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("E_NOT_FOUND"));
  }

  @Test
  void sameTenantAccess_returns200() throws Exception {
    mockMvc
        .perform(
            get("/api/tasks/" + taskAId)
                .header("X-Tenant-Id", String.valueOf(tenantAId))
                .with(authentication(authToken)))
        .andExpect(status().isOk());
  }
}
