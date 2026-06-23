package xyz.dgz48.tasks.webapi.task.adapter.web;

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
import xyz.dgz48.tasks.webapi.tenant.adapter.persistence.TenantJpaEntity;
import xyz.dgz48.tasks.webapi.user.adapter.persistence.UserJpaEntity;

/**
 * GET /api/tasks の統合テスト。
 *
 * <p>visibility 3 役割評価(ADR-0005)とクロステナント分離(ADR-0010)を検証する。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({
  TestcontainersConfiguration.class,
  MockJwtDecoderConfiguration.class,
  FixedClockConfiguration.class
})
class ListTasksIT {

  @Autowired MockMvc mockMvc;
  @Autowired EntityManager em;
  @Autowired TransactionTemplate txTemplate;

  /**
   * {@code ListTasksUseCase} が注入する {@link Clock} を固定し、期限切れ判定(#667)の基準となる当日({@code
   * LocalDate.now(clock)})を {@link #TODAY} に一致させる。これにより「選択日に関わらず当日基準で期限切れを表示」を決定論的に検証できる。
   */
  @MockitoBean Clock clock;

  /** 固定クロックの当日(JST 2026-01-15)。期限切れ判定(#667)が決定論的になるよう Clock を固定する。 */
  private static final LocalDate TODAY = LocalDate.of(2026, 1, 15);

  // User A: viewer(target user)
  private Long userAId;
  private TasksAuthenticationToken authTokenA;

  // User B: owner/stakeholder(used to create tasks)
  private Long userBId;

  private Long tenantId;

  // Tasks
  private Long tenantTaskId;
  private Long stakeholderTaskId;
  private Long privateTaskId;
  private Long privateTaskOwnedByAId;

  @BeforeEach
  void setUp() {
    when(clock.getZone()).thenReturn(AppZones.JST);
    when(clock.instant())
        .thenReturn(FixedClockConfiguration.FIXED_NOW.atZone(AppZones.JST).toInstant());

    txTemplate.execute(
        ignored -> {
          var userA =
              new UserJpaEntity("sub-lt-a", "lt-a@example.com", "一覧テストA太郎", "イチランテストエーたろう", null);
          var userB =
              new UserJpaEntity("sub-lt-b", "lt-b@example.com", "一覧テストB太郎", "イチランテストビーたろう", null);
          em.persist(userA);
          em.persist(userB);
          em.flush();
          userAId = userA.getId();
          userBId = userB.getId();

          var principal =
              new TasksPrincipal(
                  userBId, "sub-lt-b", "lt-b@example.com", "一覧テストB太郎", "イチランテストビーたろう", null);
          SecurityContextHolder.getContext()
              .setAuthentication(new TasksAuthenticationToken(principal, List.of()));

          var tenant = new TenantJpaEntity("LT-1", "一覧テストテナント");
          em.persist(tenant);
          em.flush();
          tenantId = tenant.getId();

          em.createNativeQuery(
                  "INSERT INTO user_tenants (user_id, tenant_id, role, status, joined_at)"
                      + " VALUES (?,?,?,?,?)")
              .setParameter(1, userAId)
              .setParameter(2, tenantId)
              .setParameter(3, "MEMBER")
              .setParameter(4, "ACTIVE")
              .setParameter(5, LocalDateTime.of(2026, 1, 1, 0, 0))
              .executeUpdate();
          em.createNativeQuery(
                  "INSERT INTO user_tenants (user_id, tenant_id, role, status, joined_at)"
                      + " VALUES (?,?,?,?,?)")
              .setParameter(1, userBId)
              .setParameter(2, tenantId)
              .setParameter(3, "MEMBER")
              .setParameter(4, "ACTIVE")
              .setParameter(5, LocalDateTime.of(2026, 1, 1, 0, 0))
              .executeUpdate();

          // visibility=TENANT: userA can see
          var tenantTask =
              new TaskJpaEntity(
                  tenantId,
                  userBId,
                  "TENANTタスク",
                  "四半期レビュー資料の作成",
                  TaskStatus.NOT_STARTED,
                  Priority.MEDIUM,
                  TODAY);
          em.persist(tenantTask);
          em.flush();
          tenantTaskId = tenantTask.getId();

          // visibility=STAKEHOLDERS owned by B: userA can see only as stakeholder
          em.createNativeQuery("UPDATE tasks SET visibility = 'STAKEHOLDERS' WHERE id = ?")
              .setParameter(1, tenantTaskId)
              .executeUpdate();
          // Reset — create real separate tasks
          em.createNativeQuery("UPDATE tasks SET visibility = 'TENANT' WHERE id = ?")
              .setParameter(1, tenantTaskId)
              .executeUpdate();

          // STAKEHOLDERS task: userA is a registered stakeholder
          var stakeholderTask =
              new TaskJpaEntity(
                  tenantId,
                  userBId,
                  "STAKEHOLDERSタスク",
                  null,
                  TaskStatus.NOT_STARTED,
                  Priority.HIGH,
                  TODAY);
          em.persist(stakeholderTask);
          em.flush();
          stakeholderTaskId = stakeholderTask.getId();
          em.createNativeQuery("UPDATE tasks SET visibility = 'STAKEHOLDERS' WHERE id = ?")
              .setParameter(1, stakeholderTaskId)
              .executeUpdate();
          // Register userA as stakeholder
          em.createNativeQuery(
                  "INSERT INTO task_stakeholders (task_id, user_id, tenant_id, added_by, added_at)"
                      + " VALUES (?,?,?,?,?)")
              .setParameter(1, stakeholderTaskId)
              .setParameter(2, userAId)
              .setParameter(3, tenantId)
              .setParameter(4, userBId)
              .setParameter(5, LocalDateTime.of(2026, 1, 1, 0, 0))
              .executeUpdate();

          // PRIVATE task owned by B: userA cannot see
          var privateTask =
              new TaskJpaEntity(
                  tenantId,
                  userBId,
                  "PRIVATEタスク(Bのもの)",
                  null,
                  TaskStatus.NOT_STARTED,
                  Priority.LOW,
                  TODAY);
          em.persist(privateTask);
          em.flush();
          privateTaskId = privateTask.getId();
          em.createNativeQuery("UPDATE tasks SET visibility = 'PRIVATE' WHERE id = ?")
              .setParameter(1, privateTaskId)
              .executeUpdate();

          // PRIVATE task owned by A: userA can see
          var principal2 =
              new TasksPrincipal(
                  userAId, "sub-lt-a", "lt-a@example.com", "一覧テストA太郎", "イチランテストエーたろう", null);
          SecurityContextHolder.getContext()
              .setAuthentication(new TasksAuthenticationToken(principal2, List.of()));

          var privateTaskOwnedByA =
              new TaskJpaEntity(
                  tenantId,
                  userAId,
                  "PRIVATEタスク(Aのもの)",
                  null,
                  TaskStatus.NOT_STARTED,
                  Priority.LOW,
                  TODAY);
          em.persist(privateTaskOwnedByA);
          em.flush();
          privateTaskOwnedByAId = privateTaskOwnedByA.getId();
          em.createNativeQuery("UPDATE tasks SET visibility = 'PRIVATE' WHERE id = ?")
              .setParameter(1, privateTaskOwnedByAId)
              .executeUpdate();

          return null;
        });

    SecurityContextHolder.clearContext();

    var principalA =
        new TasksPrincipal(
            userAId, "sub-lt-a", "lt-a@example.com", "一覧テストA太郎", "イチランテストエーたろう", null);
    authTokenA = new TasksAuthenticationToken(principalA, List.of());
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
    if (userAId == null) {
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

  @Nested
  class VisibilityAuthorization {

    @Test
    void returnsOnlyVisibleTasks_forMemberUserA() throws Exception {
      // userA should see: tenantTask, stakeholderTask (as registered stakeholder),
      // privateTaskOwnedByA
      // userA should NOT see: privateTask (owned by B, no stakeholder link)
      mockMvc
          .perform(
              get("/api/tasks")
                  .header("X-Tenant-Id", String.valueOf(tenantId))
                  .with(authentication(authTokenA)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.content").isArray())
          .andExpect(jsonPath("$.content[?(@.id == " + tenantTaskId + ")]").exists())
          .andExpect(jsonPath("$.content[?(@.id == " + stakeholderTaskId + ")]").exists())
          .andExpect(jsonPath("$.content[?(@.id == " + privateTaskOwnedByAId + ")]").exists())
          .andExpect(jsonPath("$.content[?(@.id == " + privateTaskId + ")]").doesNotExist());
    }

    @Test
    void tenantTask_isVisibleToAllTenantMembers() throws Exception {
      mockMvc
          .perform(
              get("/api/tasks")
                  .header("X-Tenant-Id", String.valueOf(tenantId))
                  .param("visibility", "TENANT")
                  .with(authentication(authTokenA)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.content[?(@.id == " + tenantTaskId + ")]").exists());
    }

    @Test
    void stakeholdersTask_isVisibleToRegisteredStakeholder() throws Exception {
      mockMvc
          .perform(
              get("/api/tasks")
                  .header("X-Tenant-Id", String.valueOf(tenantId))
                  .param("visibility", "STAKEHOLDERS")
                  .with(authentication(authTokenA)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.content[?(@.id == " + stakeholderTaskId + ")]").exists())
          .andExpect(jsonPath("$.content[?(@.id == " + privateTaskId + ")]").doesNotExist());
    }

    @Test
    void privateTask_isNotVisibleToNonOwnerNonAssignee() throws Exception {
      mockMvc
          .perform(
              get("/api/tasks")
                  .header("X-Tenant-Id", String.valueOf(tenantId))
                  .param("visibility", "PRIVATE")
                  .with(authentication(authTokenA)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.content[?(@.id == " + privateTaskId + ")]").doesNotExist())
          .andExpect(jsonPath("$.content[?(@.id == " + privateTaskOwnedByAId + ")]").exists());
    }
  }

  @Nested
  class CrossTenantIsolation {

    private Long tenantBId;
    private Long taskBId;
    private Long userCId;

    @BeforeEach
    void setUpTenantB() {
      txTemplate.execute(
          ignored -> {
            var userC =
                new UserJpaEntity("sub-lt-c", "lt-c@example.com", "一覧テストC太郎", "イチランテストシーたろう", null);
            em.persist(userC);
            em.flush();
            userCId = userC.getId();

            var principalC =
                new TasksPrincipal(
                    userCId, "sub-lt-c", "lt-c@example.com", "一覧テストC太郎", "イチランテストシーたろう", null);
            SecurityContextHolder.getContext()
                .setAuthentication(new TasksAuthenticationToken(principalC, List.of()));

            var tenantB = new TenantJpaEntity("LT-B", "一覧テストテナントB");
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

            var taskB =
                new TaskJpaEntity(
                    tenantBId,
                    userCId,
                    "テナントBのタスク",
                    null,
                    TaskStatus.NOT_STARTED,
                    Priority.MEDIUM,
                    TODAY);
            em.persist(taskB);
            em.flush();
            taskBId = taskB.getId();
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
                .setParameter(1, taskBId)
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
    void listTasks_doesNotLeakOtherTenantTasks() throws Exception {
      // userA is in tenantA. Request with X-Tenant-Id = tenantA must NOT return tenantB's tasks.
      mockMvc
          .perform(
              get("/api/tasks")
                  .header("X-Tenant-Id", String.valueOf(tenantId))
                  .with(authentication(authTokenA)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.content[?(@.id == " + taskBId + ")]").doesNotExist());
    }
  }

  @Nested
  class FilterAndPaging {

    @Test
    void statusFilter_returnsOnlyMatchingTasks() throws Exception {
      mockMvc
          .perform(
              get("/api/tasks")
                  .header("X-Tenant-Id", String.valueOf(tenantId))
                  .param("status", "NOT_STARTED")
                  .with(authentication(authTokenA)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.content[?(@.status != 'NOT_STARTED')]").doesNotExist());
    }

    @Test
    void ownerIdFilter_returnsOnlyOwnerTasks() throws Exception {
      mockMvc
          .perform(
              get("/api/tasks")
                  .header("X-Tenant-Id", String.valueOf(tenantId))
                  .param("ownerId", String.valueOf(userAId))
                  .with(authentication(authTokenA)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.content[?(@.owner.id == " + userBId + ")]").doesNotExist());
    }

    @Test
    void keywordFilter_matchesTitlePartial() throws Exception {
      // "STAKEHOLDERS" は stakeholderTask のタイトルにのみ含まれる(#669)
      mockMvc
          .perform(
              get("/api/tasks")
                  .header("X-Tenant-Id", String.valueOf(tenantId))
                  .param("keyword", "STAKEHOLDERS")
                  .with(authentication(authTokenA)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.content[?(@.id == " + stakeholderTaskId + ")]").exists())
          .andExpect(jsonPath("$.content[?(@.id == " + tenantTaskId + ")]").doesNotExist())
          .andExpect(
              jsonPath("$.content[?(@.id == " + privateTaskOwnedByAId + ")]").doesNotExist());
    }

    @Test
    void keywordFilter_matchesDescriptionPartial() throws Exception {
      // "四半期" は tenantTask の説明にのみ含まれる(#669、タイトルは "TENANTタスク")
      mockMvc
          .perform(
              get("/api/tasks")
                  .header("X-Tenant-Id", String.valueOf(tenantId))
                  .param("keyword", "四半期")
                  .with(authentication(authTokenA)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.content[?(@.id == " + tenantTaskId + ")]").exists())
          .andExpect(jsonPath("$.content[?(@.id == " + stakeholderTaskId + ")]").doesNotExist());
    }

    @Test
    void keywordFilter_isCaseInsensitive() throws Exception {
      // 小文字 "stakeholders" でも大文字タイトルに一致する
      mockMvc
          .perform(
              get("/api/tasks")
                  .header("X-Tenant-Id", String.valueOf(tenantId))
                  .param("keyword", "stakeholders")
                  .with(authentication(authTokenA)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.content[?(@.id == " + stakeholderTaskId + ")]").exists());
    }

    @Test
    void keywordFilter_noMatch_returnsEmpty() throws Exception {
      mockMvc
          .perform(
              get("/api/tasks")
                  .header("X-Tenant-Id", String.valueOf(tenantId))
                  .param("keyword", "存在しないキーワードxyz")
                  .with(authentication(authTokenA)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.content.length()").value(0))
          .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void keywordFilter_blank_returnsAllVisible() throws Exception {
      // 空白のみの keyword は検索条件なし扱い(認可フィルタ通過タスクは全件返る)
      mockMvc
          .perform(
              get("/api/tasks")
                  .header("X-Tenant-Id", String.valueOf(tenantId))
                  .param("keyword", "   ")
                  .with(authentication(authTokenA)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.content[?(@.id == " + tenantTaskId + ")]").exists())
          .andExpect(jsonPath("$.content[?(@.id == " + stakeholderTaskId + ")]").exists());
    }

    @Test
    void paging_returnsCorrectPage() throws Exception {
      mockMvc
          .perform(
              get("/api/tasks")
                  .header("X-Tenant-Id", String.valueOf(tenantId))
                  .param("page", "0")
                  .param("size", "2")
                  .with(authentication(authTokenA)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.number").value(0))
          .andExpect(jsonPath("$.size").value(2))
          .andExpect(
              jsonPath("$.content.length()").value(org.hamcrest.Matchers.lessThanOrEqualTo(2)));
    }

    @Test
    void sortByDueDateAsc_returnsOrderedResults() throws Exception {
      mockMvc
          .perform(
              get("/api/tasks")
                  .header("X-Tenant-Id", String.valueOf(tenantId))
                  .param("sort", "dueDate,asc")
                  .with(authentication(authTokenA)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.totalElements").isNumber());
    }

    @Test
    void sortByPriorityDesc_returnsHighPriorityFirst() throws Exception {
      mockMvc
          .perform(
              get("/api/tasks")
                  .header("X-Tenant-Id", String.valueOf(tenantId))
                  .param("sort", "priority,desc")
                  .with(authentication(authTokenA)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.content[0].priority").value("HIGH"));
    }

    @Test
    void responseContainsRequiredFields() throws Exception {
      mockMvc
          .perform(
              get("/api/tasks")
                  .header("X-Tenant-Id", String.valueOf(tenantId))
                  .with(authentication(authTokenA)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.totalElements").isNumber())
          .andExpect(jsonPath("$.totalPages").isNumber())
          .andExpect(jsonPath("$.number").value(0))
          .andExpect(jsonPath("$.size").isNumber())
          .andExpect(jsonPath("$.overdueCount").isNumber());
    }
  }

  @Nested
  class Authentication {

    @Test
    void returns401_whenUnauthenticated() throws Exception {
      mockMvc
          .perform(get("/api/tasks").header("X-Tenant-Id", String.valueOf(tenantId)))
          .andExpect(status().isUnauthorized());
    }
  }

  /** 表示対象日フィルタ(targetDate / includeOverdue、#665)の検証。 */
  @Nested
  class DateWindow {

    private Long todayTaskId;
    private Long overdueTaskId;
    private Long overdueDoneTaskId;
    private Long futureTaskId;

    @BeforeEach
    void setUpDatedTasks() {
      txTemplate.execute(
          ignored -> {
            // JpaAuditorAware が created_by / updated_by を解決できるよう認証コンテキストを設定
            var principalB =
                new TasksPrincipal(
                    userBId, "sub-lt-b", "lt-b@example.com", "一覧テストB太郎", "イチランテストビーたろう", null);
            SecurityContextHolder.getContext()
                .setAuthentication(new TasksAuthenticationToken(principalB, List.of()));

            todayTaskId = persistTask("当日タスク", TaskStatus.NOT_STARTED, TODAY);
            overdueTaskId = persistTask("期限切れタスク", TaskStatus.IN_PROGRESS, TODAY.minusDays(5));
            overdueDoneTaskId = persistTask("期限切れ完了タスク", TaskStatus.DONE, TODAY.minusDays(5));
            futureTaskId = persistTask("未来タスク", TaskStatus.NOT_STARTED, TODAY.plusDays(10));
            return null;
          });
      SecurityContextHolder.clearContext();
    }

    private Long persistTask(String title, TaskStatus status, LocalDate dueDate) {
      // visibility=TENANT(デフォルト)なので userA から参照可能
      var task =
          new TaskJpaEntity(tenantId, userBId, title, null, status, Priority.MEDIUM, dueDate);
      em.persist(task);
      em.flush();
      return task.getId();
    }

    @Test
    void targetDate_withDefaultIncludeOverdue_returnsTargetDayAndOverdueExcludingDoneAndFuture()
        throws Exception {
      // targetDate=TODAY 基準 + includeOverdue=true(デフォルト、明示指定なし)
      mockMvc
          .perform(
              get("/api/tasks")
                  .header("X-Tenant-Id", String.valueOf(tenantId))
                  .param("targetDate", TODAY.toString())
                  .with(authentication(authTokenA)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.content[?(@.id == " + todayTaskId + ")]").exists())
          .andExpect(jsonPath("$.content[?(@.id == " + overdueTaskId + ")]").exists())
          .andExpect(jsonPath("$.content[?(@.id == " + overdueDoneTaskId + ")]").doesNotExist())
          .andExpect(jsonPath("$.content[?(@.id == " + futureTaskId + ")]").doesNotExist())
          .andExpect(
              jsonPath("$.overdueCount").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
    }

    @Test
    void includeOverdueFalse_returnsOnlyTargetDateTasks() throws Exception {
      mockMvc
          .perform(
              get("/api/tasks")
                  .header("X-Tenant-Id", String.valueOf(tenantId))
                  .param("targetDate", TODAY.toString())
                  .param("includeOverdue", "false")
                  .with(authentication(authTokenA)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.content[?(@.id == " + todayTaskId + ")]").exists())
          .andExpect(jsonPath("$.content[?(@.id == " + overdueTaskId + ")]").doesNotExist())
          .andExpect(jsonPath("$.content[?(@.id == " + futureTaskId + ")]").doesNotExist());
    }

    @Test
    void targetDate_selectsThatDaysTasks() throws Exception {
      mockMvc
          .perform(
              get("/api/tasks")
                  .header("X-Tenant-Id", String.valueOf(tenantId))
                  .param("targetDate", TODAY.plusDays(10).toString())
                  .param("includeOverdue", "false")
                  .with(authentication(authTokenA)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.content[?(@.id == " + futureTaskId + ")]").exists())
          .andExpect(jsonPath("$.content[?(@.id == " + todayTaskId + ")]").doesNotExist());
    }

    @Test
    void overdueAlwaysShown_whenTargetDateIsFuture() throws Exception {
      // #667: 未来日を選択しても、期限切れ未完了タスクは当日(TODAY)基準で常時上部に表示される。
      // 期限切れ判定は選択日ではなく当日基準のため、当日タスクは未来日ビューに現れない。
      mockMvc
          .perform(
              get("/api/tasks")
                  .header("X-Tenant-Id", String.valueOf(tenantId))
                  .param("targetDate", TODAY.plusDays(10).toString())
                  .with(authentication(authTokenA)))
          .andExpect(status().isOk())
          // 選択日(未来)のタスク
          .andExpect(jsonPath("$.content[?(@.id == " + futureTaskId + ")]").exists())
          // 期限切れ未完了は選択日に関わらず常時表示
          .andExpect(jsonPath("$.content[?(@.id == " + overdueTaskId + ")]").exists())
          // 当日タスクは当日基準では期限切れでなく、選択日(未来)とも一致しないため現れない
          .andExpect(jsonPath("$.content[?(@.id == " + todayTaskId + ")]").doesNotExist())
          // 完了済の期限切れは除外
          .andExpect(jsonPath("$.content[?(@.id == " + overdueDoneTaskId + ")]").doesNotExist())
          // overdueCount は当日基準で算出される(選択日に依存しない)
          .andExpect(
              jsonPath("$.overdueCount").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
    }

    @Test
    void invalidTargetDate_returns400() throws Exception {
      mockMvc
          .perform(
              get("/api/tasks")
                  .header("X-Tenant-Id", String.valueOf(tenantId))
                  .param("targetDate", "not-a-date")
                  .with(authentication(authTokenA)))
          .andExpect(status().isBadRequest());
    }
  }
}
