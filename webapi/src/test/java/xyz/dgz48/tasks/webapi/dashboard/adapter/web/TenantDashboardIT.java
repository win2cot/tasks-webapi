package xyz.dgz48.tasks.webapi.dashboard.adapter.web;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManager;
import java.time.Clock;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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
import xyz.dgz48.tasks.webapi.task.domain.Visibility;
import xyz.dgz48.tasks.webapi.tenant.adapter.persistence.TenantJpaEntity;
import xyz.dgz48.tasks.webapi.user.adapter.persistence.UserJpaEntity;

/**
 * S-15 テナント運営者向けダッシュボード API(A-28、GET /api/tenant/dashboard/summary)の統合テスト。
 *
 * <p>集計対象が {@code visibility ∈ {TENANT, STAKEHOLDERS}} に限定され <b>{@code PRIVATE}
 * が件数も含めて漏れない</b>こと(ADR-0005 §3.5 / NIST AC-4)、{@code memberCount} が ACTIVE 所属のみ数えること、認可(Tenant
 * Admin のみ・Member は 403)、テナント分離(ADR-0010)を検証する。固定 Clock(JST 2026-01-15)で当日判定を決定論的にする。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({
  TestcontainersConfiguration.class,
  MockJwtDecoderConfiguration.class,
  FixedClockConfiguration.class
})
class TenantDashboardIT {

  @Autowired MockMvc mockMvc;
  @Autowired EntityManager em;
  @Autowired TransactionTemplate txTemplate;

  /** GetTenantDashboardSummaryUseCase が注入する Clock を固定し、当日(JST 2026-01-15)を決定論的にする。 */
  @MockitoBean Clock clock;

  private static final LocalDate TODAY = LocalDate.of(2026, 1, 15);

  private Long tenantId;
  private Long adminUserId;
  private Long memberUserId;
  private Long invitedUserId;

  private TasksAuthenticationToken adminToken;
  private TasksAuthenticationToken memberToken;

  @BeforeEach
  void setUp() {
    when(clock.getZone()).thenReturn(AppZones.JST);
    when(clock.instant())
        .thenReturn(FixedClockConfiguration.FIXED_NOW.atZone(AppZones.JST).toInstant());

    txTemplate.execute(
        ignored -> {
          var admin = new UserJpaEntity("sub-tda", "tda@example.com", "運営者", "ウンエイシャ", null);
          var member = new UserJpaEntity("sub-tdm", "tdm@example.com", "一般", "イッパン", null);
          var invited = new UserJpaEntity("sub-tdi", "tdi@example.com", "招待中", "ショウタイチュウ", null);
          em.persist(admin);
          em.persist(member);
          em.persist(invited);
          em.flush();
          adminUserId = admin.getId();
          memberUserId = member.getId();
          invitedUserId = invited.getId();

          // 監査列(created_by/updated_by)解決のため認証コンテキストを設定
          SecurityContextHolder.getContext()
              .setAuthentication(
                  new TasksAuthenticationToken(
                      new TasksPrincipal(
                          adminUserId, "sub-tda", "tda@example.com", "運営者", "ウンエイシャ", null),
                      List.of()));

          var tenant = new TenantJpaEntity("TD-1", "運営ダッシュボードテナント");
          em.persist(tenant);
          em.flush();
          tenantId = tenant.getId();

          insertMembership(adminUserId, "TENANT_ADMIN", "ACTIVE");
          insertMembership(memberUserId, "MEMBER", "ACTIVE");
          // INVITED は memberCount に数えない
          insertMembership(invitedUserId, "MEMBER", "INVITED");

          // TENANT / STAKEHOLDERS は集計対象、PRIVATE は除外
          persistTask(
              "TA", TaskStatus.NOT_STARTED, Priority.MEDIUM, Visibility.TENANT, TODAY, null);
          persistTask(
              "TB",
              TaskStatus.IN_PROGRESS,
              Priority.HIGH,
              Visibility.TENANT,
              TODAY.minusDays(2),
              null);
          persistTask(
              "TC",
              TaskStatus.NOT_STARTED,
              Priority.LOW,
              Visibility.STAKEHOLDERS,
              TODAY.plusDays(5),
              null);
          Long td =
              persistTask(
                  "TD",
                  TaskStatus.DONE,
                  Priority.MEDIUM,
                  Visibility.TENANT,
                  TODAY.minusDays(1),
                  null);
          setCompletedAt(td, TODAY.atTime(10, 0));
          // TE: PRIVATE・当日期限・HIGH。漏れていれば todayDueCount / totalTaskCount / HIGH / NOT_STARTED が増える。
          persistTask("TE", TaskStatus.NOT_STARTED, Priority.HIGH, Visibility.PRIVATE, TODAY, null);

          return null;
        });

    SecurityContextHolder.clearContext();
    adminToken =
        new TasksAuthenticationToken(
            new TasksPrincipal(adminUserId, "sub-tda", "tda@example.com", "運営者", "ウンエイシャ", null),
            List.of());
    memberToken =
        new TasksAuthenticationToken(
            new TasksPrincipal(memberUserId, "sub-tdm", "tdm@example.com", "一般", "イッパン", null),
            List.of());
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
    if (tenantId == null) {
      return;
    }
    txTemplate.execute(
        ignored -> {
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
              .setParameter(1, adminUserId)
              .setParameter(2, memberUserId)
              .setParameter(3, invitedUserId)
              .executeUpdate();
          return null;
        });
  }

  @Test
  void aggregatesTenantAndStakeholdersTasks_excludingPrivate() throws Exception {
    mockMvc
        .perform(
            get("/api/tenant/dashboard/summary")
                .header("X-Tenant-Id", String.valueOf(tenantId))
                .with(authentication(adminToken)))
        .andExpect(status().isOk())
        // 集計対象は TA/TB/TC/TD の 4 件(TE=PRIVATE は除外)
        .andExpect(jsonPath("$.totalTaskCount").value(4))
        // 当日期限: TA のみ(TE は PRIVATE で除外)
        .andExpect(jsonPath("$.todayDueCount").value(1))
        // 期限切れ未完了: TB のみ
        .andExpect(jsonPath("$.overdueCount").value(1))
        // 本日完了: TD のみ
        .andExpect(jsonPath("$.completedTodayCount").value(1))
        .andExpect(jsonPath("$.statusBreakdown.NOT_STARTED").value(2))
        .andExpect(jsonPath("$.statusBreakdown.IN_PROGRESS").value(1))
        .andExpect(jsonPath("$.statusBreakdown.DONE").value(1))
        .andExpect(jsonPath("$.statusBreakdown.ON_HOLD").value(0))
        .andExpect(jsonPath("$.priorityBreakdown.HIGH").value(1))
        .andExpect(jsonPath("$.priorityBreakdown.MEDIUM").value(2))
        .andExpect(jsonPath("$.priorityBreakdown.LOW").value(1))
        // ACTIVE 所属のみ(admin + member の 2、INVITED は除外)
        .andExpect(jsonPath("$.memberCount").value(2));
  }

  @Test
  void member_isForbidden() throws Exception {
    mockMvc
        .perform(
            get("/api/tenant/dashboard/summary")
                .header("X-Tenant-Id", String.valueOf(tenantId))
                .with(authentication(memberToken)))
        .andExpect(status().isForbidden());
  }

  @Test
  void unauthenticated_returns401() throws Exception {
    mockMvc
        .perform(
            get("/api/tenant/dashboard/summary").header("X-Tenant-Id", String.valueOf(tenantId)))
        .andExpect(status().isUnauthorized());
  }

  // --- helpers ---

  private void insertMembership(Long userId, String role, String memberStatus) {
    em.createNativeQuery(
            "INSERT INTO user_tenants (user_id, tenant_id, role, status, joined_at)"
                + " VALUES (?,?,?,?,?)")
        .setParameter(1, userId)
        .setParameter(2, tenantId)
        .setParameter(3, role)
        .setParameter(4, memberStatus)
        .setParameter(5, LocalDateTime.of(2026, 1, 1, 0, 0))
        .executeUpdate();
  }

  private void setCompletedAt(Long taskId, LocalDateTime completedAt) {
    em.createNativeQuery("UPDATE tasks SET completed_at = ? WHERE id = ?")
        .setParameter(1, completedAt)
        .setParameter(2, taskId)
        .executeUpdate();
  }

  private Long persistTask(
      String title,
      TaskStatus status,
      Priority priority,
      Visibility visibility,
      LocalDate dueDate,
      @org.jspecify.annotations.Nullable Long assigneeId) {
    var task =
        new TaskJpaEntity(
            tenantId, adminUserId, title, null, status, priority, visibility, assigneeId, dueDate);
    em.persist(task);
    em.flush();
    return task.getId();
  }
}
