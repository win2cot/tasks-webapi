package xyz.dgz48.tasks.webapi.tenant.adapter.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
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
 * テナント切替後の TenantContext 反映確認 IT (Phase 1 Sprint 1 C-3)。
 *
 * <p>ユーザ U がテナント A・B 両方に所属し、各テナントにタスク T_A・T_B を持つシナリオで、 切替前後のクロステナント分離と Hibernate Filter の
 * tenant_id 自動付与を通しで検証する。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({
  TestcontainersConfiguration.class,
  MockJwtDecoderConfiguration.class,
  FixedClockConfiguration.class
})
class TenantSwitchContextIT {

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
          var user =
              new UserJpaEntity(
                  "sub-sw-ctx", "sw-ctx@example.com", "切替コンテキスト太郎", "キリカエコンテキストタロウ", null);
          em.persist(user);
          em.flush();
          userId = user.getId();

          var principal =
              new TasksPrincipal(
                  userId, "sub-sw-ctx", "sw-ctx@example.com", "切替コンテキスト太郎", "キリカエコンテキストタロウ", null);
          SecurityContextHolder.getContext()
              .setAuthentication(new TasksAuthenticationToken(principal, List.of()));

          var tenantA = new TenantJpaEntity("SW-A", "切替テストテナントA");
          var tenantB = new TenantJpaEntity("SW-B", "切替テストテナントB");
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
          em.createNativeQuery(
                  "INSERT INTO user_tenants"
                      + " (user_id, tenant_id, role, status, joined_at) VALUES (?,?,?,?,?)")
              .setParameter(1, userId)
              .setParameter(2, tenantBId)
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

    authToken =
        new TasksAuthenticationToken(
            new TasksPrincipal(
                userId, "sub-sw-ctx", "sw-ctx@example.com", "切替コンテキスト太郎", "キリカエコンテキストタロウ", null),
            List.of());
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
          em.createNativeQuery("DELETE FROM user_tenants WHERE user_id = ?")
              .setParameter(1, userId)
              .executeUpdate();
          em.createNativeQuery("DELETE FROM tenants WHERE id = ? OR id = ?")
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
  void tenantSwitch_reflectsTenantContext_andPreventsLeakage() throws Exception {
    // 切替前(X-Tenant-Id: A): T_A 取得可、T_B は 404(Hibernate Filter が tenant_id=A を自動付与)
    mockMvc
        .perform(
            get("/api/tasks/{id}", taskAId)
                .header("X-Tenant-Id", String.valueOf(tenantAId))
                .with(authentication(authToken)))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            get("/api/tasks/{id}", taskBId)
                .header("X-Tenant-Id", String.valueOf(tenantAId))
                .with(authentication(authToken)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("E_NOT_FOUND"));

    // テナント切替: ユーザがテナントBのアクティブメンバーであることを検証して 204 を返す
    mockMvc
        .perform(
            post("/api/auth/tenants/{tenantId}/select", tenantBId).with(authentication(authToken)))
        .andExpect(status().isNoContent());

    // 切替後(X-Tenant-Id: B): T_B 取得可、T_A は 404(Hibernate Filter が tenant_id=B を自動付与)
    mockMvc
        .perform(
            get("/api/tasks/{id}", taskBId)
                .header("X-Tenant-Id", String.valueOf(tenantBId))
                .with(authentication(authToken)))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            get("/api/tasks/{id}", taskAId)
                .header("X-Tenant-Id", String.valueOf(tenantBId))
                .with(authentication(authToken)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("E_NOT_FOUND"));
  }
}
