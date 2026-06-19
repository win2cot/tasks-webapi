package xyz.dgz48.tasks.webapi.task.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
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
 * PATCH /api/tasks/{id}/status の統合 IT。
 *
 * <p>楽観ロック(If-Match)対象外(ADR-0012 amendment)。If-Match 不要で 200 を返し、同時変更は last-write-wins で両方 200
 * を返すことを検証する。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({
  TestcontainersConfiguration.class,
  MockJwtDecoderConfiguration.class,
  FixedClockConfiguration.class
})
class ChangeTaskStatusIT {

  @Autowired MockMvc mockMvc;
  @Autowired EntityManager em;
  @Autowired TransactionTemplate txTemplate;

  private Long userId;
  private Long tenantId;
  private Long taskId;
  private TasksAuthenticationToken authToken;

  @BeforeEach
  void setUp() {
    txTemplate.execute(
        ignored -> {
          var user = new UserJpaEntity("sub-cs-it", "cs-it@example.com", "楽観 太郎", "ラッカン タロウ", null);
          em.persist(user);
          em.flush();
          userId = user.getId();

          var principal =
              new TasksPrincipal(
                  userId, "sub-cs-it", "cs-it@example.com", "楽観 太郎", "ラッカン タロウ", null);
          SecurityContextHolder.getContext()
              .setAuthentication(new TasksAuthenticationToken(principal, List.of()));

          var tenant = new TenantJpaEntity("CS-IT-1", "楽観ロックIT");
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

          var task =
              new TaskJpaEntity(
                  tenantId,
                  userId,
                  "楽観ロックテスト",
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
        new TasksPrincipal(userId, "sub-cs-it", "cs-it@example.com", "楽観 太郎", "ラッカン タロウ", null);
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
              .setParameter(1, taskId)
              .executeUpdate();
          em.createNativeQuery("DELETE FROM user_tenants WHERE user_id = ? AND tenant_id = ?")
              .setParameter(1, userId)
              .setParameter(2, tenantId)
              .executeUpdate();
          em.createNativeQuery("DELETE FROM tenants WHERE id = ?")
              .setParameter(1, tenantId)
              .executeUpdate();
          em.createNativeQuery("DELETE FROM users WHERE id = ?")
              .setParameter(1, userId)
              .executeUpdate();
          return null;
        });
  }

  @Test
  void changeStatus_returns200_withoutIfMatch() throws Exception {
    mockMvc
        .perform(
            patch("/api/tasks/" + taskId + "/status")
                .header("X-Tenant-Id", String.valueOf(tenantId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"IN_PROGRESS\"}")
                .with(authentication(authToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
  }

  @Test
  void changeStatus_concurrentRequests_bothReturn200_lastWriteWins() throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(2);

    Callable<MvcResult> patchRequest =
        () ->
            mockMvc
                .perform(
                    patch("/api/tasks/" + taskId + "/status")
                        .header("X-Tenant-Id", String.valueOf(tenantId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PROGRESS\"}")
                        .with(authentication(authToken)))
                .andReturn();

    Future<MvcResult> future1 = executor.submit(patchRequest);
    Future<MvcResult> future2 = executor.submit(patchRequest);

    MockHttpServletResponse response1 = future1.get().getResponse();
    MockHttpServletResponse response2 = future2.get().getResponse();
    executor.shutdown();

    assertThat(response1.getStatus())
        .as("同時ステータス変更: リクエスト1 が 200 を返すこと(last-write-wins)")
        .isEqualTo(200);
    assertThat(response2.getStatus())
        .as("同時ステータス変更: リクエスト2 が 200 を返すこと(last-write-wins)")
        .isEqualTo(200);
  }
}
