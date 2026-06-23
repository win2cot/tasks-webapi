package xyz.dgz48.tasks.webapi.task.adapter.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import org.springframework.http.MediaType;
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
 * クロステナント越境「更新」遮断の HTTP 層 IT(R-05 / ADR-0010)。
 *
 * <p>越境更新は 2 つの経路で fail-closed になることを Testcontainers MySQL 8.4 で検証する:
 *
 * <ul>
 *   <li><strong>非メンバーテナント指定 → 403</strong>: 所属しないテナントを {@code X-Tenant-Id} に指定した更新要求は、 {@code
 *       TenantContextFilter} がコンテキスト確立段階で拒否する(設計規約 HTTP ステータス方針「テナント越境 = 403」)。
 *   <li><strong>自テナントコンテキストで別テナント資源を更新 → 404</strong>: 自テナントのコンテキストでは別テナントのタスクが Hibernate Filter
 *       により不可視であり、更新ユースケースの read-before-write(Filter 付き {@code findById})が先に {@code
 *       TaskNotFoundException} を送出する。これにより tenant_id 非絞り込みの bulk DML ({@code
 *       updateStatusById})が越境行へ到達しないことが担保される(NIST AC-4 — 存在を漏らさない)。
 * </ul>
 *
 * <p>越境「参照」= 404 は {@code TaskCrossTenantIT} で検証済。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({
  TestcontainersConfiguration.class,
  MockJwtDecoderConfiguration.class,
  FixedClockConfiguration.class
})
class CrossTenantWriteForbiddenIT {

  @Autowired MockMvc mockMvc;
  @Autowired EntityManager em;
  @Autowired TransactionTemplate txTemplate;

  private Long userId;
  private Long tenantAId;
  private Long tenantBId;
  private Long taskBId;
  private TasksAuthenticationToken authToken;

  @BeforeEach
  void setUp() {
    txTemplate.execute(
        ignored -> {
          var user =
              new UserJpaEntity("sub-xtw", "xtw@example.com", "越境更新太郎", "エッキョウコウシンタロウ", null);
          em.persist(user);
          em.flush();
          userId = user.getId();

          var principal =
              new TasksPrincipal(
                  userId, "sub-xtw", "xtw@example.com", "越境更新太郎", "エッキョウコウシンタロウ", null);
          SecurityContextHolder.getContext()
              .setAuthentication(new TasksAuthenticationToken(principal, List.of()));

          var tenantA = new TenantJpaEntity("XTW-A", "越境更新A");
          var tenantB = new TenantJpaEntity("XTW-B", "越境更新B");
          em.persist(tenantA);
          em.persist(tenantB);
          em.flush();
          tenantAId = tenantA.getId();
          tenantBId = tenantB.getId();

          // user は tenantA のメンバーのみ(tenantB には所属しない)
          em.createNativeQuery(
                  "INSERT INTO user_tenants"
                      + " (user_id, tenant_id, role, status, joined_at) VALUES (?,?,?,?,?)")
              .setParameter(1, userId)
              .setParameter(2, tenantAId)
              .setParameter(3, "MEMBER")
              .setParameter(4, "ACTIVE")
              .setParameter(5, LocalDateTime.of(2026, 1, 1, 0, 0))
              .executeUpdate();

          // 更新対象のタスクは tenantB に存在する(user は越境してこれを更新しようとする)
          var taskB =
              new TaskJpaEntity(
                  tenantBId,
                  userId,
                  "テナントBのタスク",
                  null,
                  TaskStatus.NOT_STARTED,
                  Priority.MEDIUM,
                  LocalDate.of(2026, 12, 31));
          em.persist(taskB);
          em.flush();
          taskBId = taskB.getId();

          return null;
        });

    SecurityContextHolder.clearContext();

    var principal =
        new TasksPrincipal(userId, "sub-xtw", "xtw@example.com", "越境更新太郎", "エッキョウコウシンタロウ", null);
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
              .setParameter(1, taskBId)
              .executeUpdate();
          em.createNativeQuery("DELETE FROM user_tenants WHERE user_id = ?")
              .setParameter(1, userId)
              .executeUpdate();
          em.createNativeQuery("DELETE FROM tenants WHERE id IN (?,?)")
              .setParameter(1, tenantAId)
              .setParameter(2, tenantBId)
              .executeUpdate();
          em.createNativeQuery("DELETE FROM users WHERE id = ?")
              .setParameter(1, userId)
              .executeUpdate();
          return null;
        });
  }

  @Test
  void statusChange_nonMemberTenantHeader_returns403() throws Exception {
    // tenantB は user の非所属テナント → TenantContextFilter がコンテキスト確立段階で 403
    mockMvc
        .perform(
            patch("/api/tasks/" + taskBId + "/status")
                .header("X-Tenant-Id", String.valueOf(tenantBId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"IN_PROGRESS\"}")
                .with(authentication(authToken)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("E_FORBIDDEN"));
  }

  @Test
  void delete_nonMemberTenantHeader_returns403() throws Exception {
    mockMvc
        .perform(
            delete("/api/tasks/" + taskBId)
                .header("X-Tenant-Id", String.valueOf(tenantBId))
                .with(authentication(authToken)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("E_FORBIDDEN"));
  }

  @Test
  void statusChange_ownTenantContext_foreignTask_returns404() throws Exception {
    // 自テナント(A)コンテキストでは tenantB のタスクは Filter により不可視 →
    // read-before-write が先に 404(tenant_id 非絞り込みの bulk DML は越境行へ到達しない)
    mockMvc
        .perform(
            patch("/api/tasks/" + taskBId + "/status")
                .header("X-Tenant-Id", String.valueOf(tenantAId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"IN_PROGRESS\"}")
                .with(authentication(authToken)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("E_NOT_FOUND"));
  }
}
