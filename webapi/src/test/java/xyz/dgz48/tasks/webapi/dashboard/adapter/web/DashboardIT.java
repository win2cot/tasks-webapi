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
import org.junit.jupiter.api.Nested;
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
 * S-03 個人視点ダッシュボード API の統合テスト。
 *
 * <p>4 セクション(GET /api/dashboard/tasks)と数値カード集計(GET /api/dashboard/summary)の契約を検証する。とりわけ visibility
 * 3 役割評価(ADR-0005)と「他者の PRIVATE / 非関係者 STAKEHOLDERS が集計に漏れない」こと(設計規約 §6 / NIST
 * AC-4)、テナント分離(ADR-0010)を確認する。
 *
 * <p>固定 Clock(JST 2026-01-15)で当日判定を決定論的にする。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({
  TestcontainersConfiguration.class,
  MockJwtDecoderConfiguration.class,
  FixedClockConfiguration.class
})
class DashboardIT {

  @Autowired MockMvc mockMvc;
  @Autowired EntityManager em;
  @Autowired TransactionTemplate txTemplate;

  /** GetDashboard*UseCase が注入する Clock を固定し、当日(JST 2026-01-15)を決定論的にする。 */
  @MockitoBean Clock clock;

  private static final LocalDate TODAY = LocalDate.of(2026, 1, 15);

  private Long userAId;
  private Long userBId;
  private Long tenantId;
  private TasksAuthenticationToken authTokenA;

  // 4 セクション検証用タスク
  private Long t1TodayOwnedByA;
  private Long t2OverdueOwnedByA;
  private Long t3TodayOwnedByB;
  private Long t5StakeholderUpcoming;
  private Long t7CompletedTodayOwnedByA;
  private Long t8CompletedYesterday;
  private Long t9UpcomingAssignedToA;
  private Long t10FutureBeyondWindowOwnedByA;
  // 集計漏れ検証用(A から参照不可)
  private Long t4PrivateOwnedByB;
  private Long t6StakeholderNotRegistered;

  @BeforeEach
  void setUp() {
    when(clock.getZone()).thenReturn(AppZones.JST);
    when(clock.instant())
        .thenReturn(FixedClockConfiguration.FIXED_NOW.atZone(AppZones.JST).toInstant());

    txTemplate.execute(
        ignored -> {
          var userA = new UserJpaEntity("sub-db-a", "db-a@example.com", "ダッシュA", "ダッシュエー", null);
          var userB = new UserJpaEntity("sub-db-b", "db-b@example.com", "ダッシュB", "ダッシュビー", null);
          em.persist(userA);
          em.persist(userB);
          em.flush();
          userAId = userA.getId();
          userBId = userB.getId();

          // 監査列(created_by/updated_by)解決のため認証コンテキストを設定
          SecurityContextHolder.getContext()
              .setAuthentication(
                  new TasksAuthenticationToken(
                      new TasksPrincipal(
                          userBId, "sub-db-b", "db-b@example.com", "ダッシュB", "ダッシュビー", null),
                      List.of()));

          var tenant = new TenantJpaEntity("DB-1", "ダッシュボードテストテナント");
          em.persist(tenant);
          em.flush();
          tenantId = tenant.getId();

          insertMembership(userAId);
          insertMembership(userBId);

          t1TodayOwnedByA =
              persistTask(
                  userAId,
                  null,
                  "T1",
                  TaskStatus.NOT_STARTED,
                  Priority.MEDIUM,
                  Visibility.TENANT,
                  TODAY);
          t2OverdueOwnedByA =
              persistTask(
                  userAId,
                  null,
                  "T2",
                  TaskStatus.IN_PROGRESS,
                  Priority.MEDIUM,
                  Visibility.PRIVATE,
                  TODAY.minusDays(3));
          t3TodayOwnedByB =
              persistTask(
                  userBId,
                  null,
                  "T3",
                  TaskStatus.NOT_STARTED,
                  Priority.MEDIUM,
                  Visibility.TENANT,
                  TODAY);
          t4PrivateOwnedByB =
              persistTask(
                  userBId,
                  null,
                  "T4",
                  TaskStatus.NOT_STARTED,
                  Priority.MEDIUM,
                  Visibility.PRIVATE,
                  TODAY);
          t5StakeholderUpcoming =
              persistTask(
                  userBId,
                  null,
                  "T5",
                  TaskStatus.NOT_STARTED,
                  Priority.HIGH,
                  Visibility.STAKEHOLDERS,
                  TODAY.plusDays(1));
          t6StakeholderNotRegistered =
              persistTask(
                  userBId,
                  null,
                  "T6",
                  TaskStatus.NOT_STARTED,
                  Priority.MEDIUM,
                  Visibility.STAKEHOLDERS,
                  TODAY);
          t7CompletedTodayOwnedByA =
              persistTask(
                  userAId,
                  null,
                  "T7",
                  TaskStatus.DONE,
                  Priority.LOW,
                  Visibility.TENANT,
                  TODAY.minusDays(1));
          t8CompletedYesterday =
              persistTask(
                  userBId,
                  null,
                  "T8",
                  TaskStatus.DONE,
                  Priority.LOW,
                  Visibility.TENANT,
                  TODAY.minusDays(2));
          t9UpcomingAssignedToA =
              persistTask(
                  userBId,
                  userAId,
                  "T9",
                  TaskStatus.NOT_STARTED,
                  Priority.MEDIUM,
                  Visibility.TENANT,
                  TODAY.plusDays(2));
          t10FutureBeyondWindowOwnedByA =
              persistTask(
                  userAId,
                  null,
                  "T10",
                  TaskStatus.NOT_STARTED,
                  Priority.MEDIUM,
                  Visibility.TENANT,
                  TODAY.plusDays(10));

          // A を T5 の関係者として登録(T6 には登録しない)
          insertStakeholder(t5StakeholderUpcoming, userAId);

          // DONE タスクの completed_at を設定
          setCompletedAt(t7CompletedTodayOwnedByA, TODAY.atTime(10, 0));
          setCompletedAt(t8CompletedYesterday, TODAY.minusDays(1).atTime(10, 0));

          return null;
        });

    SecurityContextHolder.clearContext();
    authTokenA =
        new TasksAuthenticationToken(
            new TasksPrincipal(userAId, "sub-db-a", "db-a@example.com", "ダッシュA", "ダッシュエー", null),
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
          em.createNativeQuery("DELETE FROM users WHERE id IN (?,?)")
              .setParameter(1, userAId)
              .setParameter(2, userBId)
              .executeUpdate();
          return null;
        });
  }

  // --- GET /api/dashboard/tasks(4 セクション) ---

  @Nested
  class TaskSections {

    @Test
    void returnsFourSections_restrictedToOwnerOrAssignee() throws Exception {
      mockMvc
          .perform(
              get("/api/dashboard/tasks")
                  .header("X-Tenant-Id", String.valueOf(tenantId))
                  .with(authentication(authTokenA)))
          .andExpect(status().isOk())
          // 期限超過: T2(A 所有、TODAY-3、未完了)
          .andExpect(jsonPath("$.overdue[?(@.id == " + t2OverdueOwnedByA + ")]").exists())
          // 今日やること: T1(A 所有、当日)
          .andExpect(jsonPath("$.today[?(@.id == " + t1TodayOwnedByA + ")]").exists())
          // これから: T9(A 担当、TODAY+2)。T10(TODAY+10)は窓外。
          .andExpect(jsonPath("$.upcoming[?(@.id == " + t9UpcomingAssignedToA + ")]").exists())
          .andExpect(
              jsonPath("$.upcoming[?(@.id == " + t10FutureBeyondWindowOwnedByA + ")]")
                  .doesNotExist())
          // 今日やったこと: T7(A 所有、本日完了)。T8(昨日完了)は除外。
          .andExpect(
              jsonPath("$.completedToday[?(@.id == " + t7CompletedTodayOwnedByA + ")]").exists())
          .andExpect(
              jsonPath("$.completedToday[?(@.id == " + t8CompletedYesterday + ")]").doesNotExist());
    }

    @Test
    void excludesTasksWhereUserIsNeitherOwnerNorAssignee() throws Exception {
      // T3 は TENANT で参照可能だが A は所有者でも担当者でもないため 4 セクションには現れない。
      // T5 は A が関係者だが所有者・担当者ではないため同様に現れない。
      mockMvc
          .perform(
              get("/api/dashboard/tasks")
                  .header("X-Tenant-Id", String.valueOf(tenantId))
                  .with(authentication(authTokenA)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.today[?(@.id == " + t3TodayOwnedByB + ")]").doesNotExist())
          .andExpect(
              jsonPath("$.upcoming[?(@.id == " + t5StakeholderUpcoming + ")]").doesNotExist());
    }

    @Test
    void dueWithinDays_widensUpcomingWindow() throws Exception {
      // dueWithinDays=10 にすると T10(TODAY+10)が upcoming に含まれる。
      mockMvc
          .perform(
              get("/api/dashboard/tasks")
                  .header("X-Tenant-Id", String.valueOf(tenantId))
                  .param("dueWithinDays", "10")
                  .with(authentication(authTokenA)))
          .andExpect(status().isOk())
          .andExpect(
              jsonPath("$.upcoming[?(@.id == " + t10FutureBeyondWindowOwnedByA + ")]").exists());
    }

    @Test
    void returns401_whenUnauthenticated() throws Exception {
      mockMvc
          .perform(get("/api/dashboard/tasks").header("X-Tenant-Id", String.valueOf(tenantId)))
          .andExpect(status().isUnauthorized());
    }
  }

  // --- GET /api/dashboard/summary(数値カード) ---

  @Nested
  class Summary {

    @Test
    void aggregatesOverAuthorizedVisibleSet() throws Exception {
      mockMvc
          .perform(
              get("/api/dashboard/summary")
                  .header("X-Tenant-Id", String.valueOf(tenantId))
                  .with(authentication(authTokenA)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.todayDueCount").value(2))
          .andExpect(jsonPath("$.overdueCount").value(1))
          .andExpect(jsonPath("$.completedTodayCount").value(1))
          .andExpect(jsonPath("$.myOpenCount").value(3))
          .andExpect(jsonPath("$.statusBreakdown.NOT_STARTED").value(5))
          .andExpect(jsonPath("$.statusBreakdown.IN_PROGRESS").value(1))
          .andExpect(jsonPath("$.statusBreakdown.DONE").value(2))
          .andExpect(jsonPath("$.statusBreakdown.ON_HOLD").value(0))
          .andExpect(jsonPath("$.priorityBreakdown.HIGH").value(1))
          .andExpect(jsonPath("$.priorityBreakdown.MEDIUM").value(5))
          .andExpect(jsonPath("$.priorityBreakdown.LOW").value(2));
    }

    @Test
    void doesNotLeakOtherUsersPrivateOrNonStakeholderTasks() throws Exception {
      // 設計規約 §6 / NIST AC-4: 他者の PRIVATE(T4)・非関係者 STAKEHOLDERS(T6)は当日期限だが A の集計に加算されない。
      // どちらも当日期限のため、漏れていれば todayDueCount は 3 以上になる。todayDueCount=2 で漏れがないことを保証する。
      mockMvc
          .perform(
              get("/api/dashboard/summary")
                  .header("X-Tenant-Id", String.valueOf(tenantId))
                  .with(authentication(authTokenA)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.todayDueCount").value(2));
    }

    @Test
    void returns401_whenUnauthenticated() throws Exception {
      mockMvc
          .perform(get("/api/dashboard/summary").header("X-Tenant-Id", String.valueOf(tenantId)))
          .andExpect(status().isUnauthorized());
    }
  }

  // --- クロステナント分離 ---

  @Nested
  class CrossTenantIsolation {

    private Long tenantBId;
    private Long userCId;
    private Long taskCId;

    @BeforeEach
    void setUpTenantB() {
      txTemplate.execute(
          ignored -> {
            var userC = new UserJpaEntity("sub-db-c", "db-c@example.com", "ダッシュC", "ダッシュシー", null);
            em.persist(userC);
            em.flush();
            userCId = userC.getId();

            SecurityContextHolder.getContext()
                .setAuthentication(
                    new TasksAuthenticationToken(
                        new TasksPrincipal(
                            userCId, "sub-db-c", "db-c@example.com", "ダッシュC", "ダッシュシー", null),
                        List.of()));

            var tenantB = new TenantJpaEntity("DB-B", "別テナント");
            em.persist(tenantB);
            em.flush();
            tenantBId = tenantB.getId();

            em.createNativeQuery(
                    "INSERT INTO user_tenants (user_id, tenant_id, role, status, joined_at)"
                        + " VALUES (?,?,?,?,?)")
                .setParameter(1, userCId)
                .setParameter(2, tenantBId)
                .setParameter(3, "MEMBER")
                .setParameter(4, "ACTIVE")
                .setParameter(5, LocalDateTime.of(2026, 1, 1, 0, 0))
                .executeUpdate();

            var taskC =
                new TaskJpaEntity(
                    tenantBId,
                    userCId,
                    "別テナントの当日タスク",
                    null,
                    TaskStatus.NOT_STARTED,
                    Priority.MEDIUM,
                    TODAY);
            em.persist(taskC);
            em.flush();
            taskCId = taskC.getId();
            return null;
          });
      SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDownTenantB() {
      SecurityContextHolder.clearContext();
      if (tenantBId == null) {
        return;
      }
      txTemplate.execute(
          ignored -> {
            em.createNativeQuery("DELETE FROM tasks WHERE id = ?")
                .setParameter(1, taskCId)
                .executeUpdate();
            em.createNativeQuery("DELETE FROM user_tenants WHERE tenant_id = ?")
                .setParameter(1, tenantBId)
                .executeUpdate();
            em.createNativeQuery("DELETE FROM tenants WHERE id = ?")
                .setParameter(1, tenantBId)
                .executeUpdate();
            em.createNativeQuery("DELETE FROM users WHERE id = ?")
                .setParameter(1, userCId)
                .executeUpdate();
            return null;
          });
    }

    @Test
    void summary_inTenantA_doesNotCountTenantBTasks() throws Exception {
      // A としてテナント A の集計を取得しても、テナント B の当日タスクは加算されない(todayDueCount は依然 2)。
      mockMvc
          .perform(
              get("/api/dashboard/summary")
                  .header("X-Tenant-Id", String.valueOf(tenantId))
                  .with(authentication(authTokenA)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.todayDueCount").value(2));
    }
  }

  // --- helpers ---

  private void insertMembership(Long userId) {
    em.createNativeQuery(
            "INSERT INTO user_tenants (user_id, tenant_id, role, status, joined_at)"
                + " VALUES (?,?,?,?,?)")
        .setParameter(1, userId)
        .setParameter(2, tenantId)
        .setParameter(3, "MEMBER")
        .setParameter(4, "ACTIVE")
        .setParameter(5, LocalDateTime.of(2026, 1, 1, 0, 0))
        .executeUpdate();
  }

  private void insertStakeholder(Long taskId, Long userId) {
    em.createNativeQuery(
            "INSERT INTO task_stakeholders (task_id, user_id, tenant_id, added_by, added_at)"
                + " VALUES (?,?,?,?,?)")
        .setParameter(1, taskId)
        .setParameter(2, userId)
        .setParameter(3, tenantId)
        .setParameter(4, userBId)
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
      Long ownerId,
      @org.jspecify.annotations.Nullable Long assigneeId,
      String title,
      TaskStatus status,
      Priority priority,
      Visibility visibility,
      LocalDate dueDate) {
    var task =
        new TaskJpaEntity(
            tenantId, ownerId, title, null, status, priority, visibility, assigneeId, dueDate);
    em.persist(task);
    em.flush();
    return task.getId();
  }
}
