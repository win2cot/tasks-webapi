package xyz.dgz48.tasks.webapi.dashboard.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import xyz.dgz48.tasks.webapi.QueryCountProbe;
import xyz.dgz48.tasks.webapi.QueryCountTestConfiguration;
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
 * N+1 回帰固定テスト(ADR-0039 / #769)— ダッシュボード系エンドポイント。
 *
 * <p>異なる所有者の TENANT タスクを各セクションに多めに seed し、クエリ本数がタスク件数 N に依存せず定数であることを固定する。
 *
 * <ul>
 *   <li>{@code GET /api/dashboard/tasks} — 4 セクション query + {@code loadUserMap} の 1 回一括 {@code
 *       findAllById}。
 *   <li>{@code GET /api/dashboard/summary} — STAKEHOLDERS 認可を {@code EXISTS} 相関サブクエリで畳んだ単一 query。
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({
  TestcontainersConfiguration.class,
  MockJwtDecoderConfiguration.class,
  FixedClockConfiguration.class,
  QueryCountTestConfiguration.class
})
class DashboardQueryCountIT {

  @Autowired MockMvc mockMvc;
  @Autowired EntityManager em;
  @Autowired TransactionTemplate txTemplate;

  @MockitoBean Clock clock;

  private static final LocalDate TODAY = LocalDate.of(2026, 1, 15);

  private Long tenantId;
  private Long viewerId;
  private TasksAuthenticationToken viewerToken;

  @BeforeEach
  void setUp() {
    when(clock.getZone()).thenReturn(AppZones.JST);
    when(clock.instant())
        .thenReturn(FixedClockConfiguration.FIXED_NOW.atZone(AppZones.JST).toInstant());

    txTemplate.execute(
        ignored -> {
          var viewer =
              new UserJpaEntity(
                  "sub-dqc-viewer", "dqc-viewer@example.com", "計数閲覧者", "けいすうえつらんしゃ", null);
          em.persist(viewer);
          em.flush();
          viewerId = viewer.getId();

          SecurityContextHolder.getContext()
              .setAuthentication(
                  new TasksAuthenticationToken(
                      new TasksPrincipal(
                          viewerId,
                          "sub-dqc-viewer",
                          "dqc-viewer@example.com",
                          "計数閲覧者",
                          "けいすうえつらんしゃ",
                          null),
                      List.of()));

          var tenant = new TenantJpaEntity("DQC-1", "計数テストテナント");
          em.persist(tenant);
          em.flush();
          tenantId = tenant.getId();

          insertMembership(viewerId, tenantId);

          // 各セクション(overdue / today / upcoming)に別ユーザー所有の TENANT タスクを配置。
          // loadUserMap が複数の別所有者を 1 回で解決することを検証できるよう所有者は全て別ユーザーにする。
          persistOwnedTask("超過1", TaskStatus.IN_PROGRESS, TODAY.minusDays(5));
          persistOwnedTask("超過2", TaskStatus.NOT_STARTED, TODAY.minusDays(3));
          persistOwnedTask("当日1", TaskStatus.NOT_STARTED, TODAY);
          persistOwnedTask("当日2", TaskStatus.IN_PROGRESS, TODAY);
          persistOwnedTask("未来1", TaskStatus.NOT_STARTED, TODAY.plusDays(7));
          persistOwnedTask("未来2", TaskStatus.NOT_STARTED, TODAY.plusDays(14));

          return null;
        });

    SecurityContextHolder.clearContext();

    viewerToken =
        new TasksAuthenticationToken(
            new TasksPrincipal(
                viewerId, "sub-dqc-viewer", "dqc-viewer@example.com", "計数閲覧者", "けいすうえつらんしゃ", null),
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
          em.createNativeQuery("DELETE FROM users WHERE email LIKE 'dqc-%@example.com'")
              .executeUpdate();
          return null;
        });
  }

  private void persistOwnedTask(String title, TaskStatus status, LocalDate dueDate) {
    var owner =
        new UserJpaEntity(
            "sub-dqc-" + title,
            "dqc-" + title + "@example.com",
            "所有者" + title,
            "しょゆうしゃ" + title,
            null);
    em.persist(owner);
    em.flush();
    var task =
        new TaskJpaEntity(tenantId, owner.getId(), title, null, status, Priority.MEDIUM, dueDate);
    em.persist(task);
    em.flush();
  }

  private void insertMembership(Long userId, Long tenant) {
    em.createNativeQuery(
            "INSERT INTO user_tenants (user_id, tenant_id, role, status, joined_at) VALUES (?,?,?,?,?)")
        .setParameter(1, userId)
        .setParameter(2, tenant)
        .setParameter(3, "MEMBER")
        .setParameter(4, "ACTIVE")
        .setParameter(5, LocalDateTime.of(2026, 1, 1, 0, 0))
        .executeUpdate();
  }

  /**
   * {@code GET /api/dashboard/tasks} のクエリ本数がタスク件数 N に依存せず定数であることを固定する。内訳: membership 解決 + 4 セクション
   * query + {@code loadUserMap} の一括 {@code findAllById}。N+1 退行時は所有者数だけ本数が増える。
   */
  @Test
  void dashboardTasks_queryCountIsConstantRegardlessOfTaskCount() throws Exception {
    QueryCountProbe.reset();
    mockMvc
        .perform(
            get("/api/dashboard/tasks")
                .header("X-Tenant-Id", String.valueOf(tenantId))
                .with(authentication(viewerToken)))
        .andExpect(status().isOk());

    assertThat(QueryCountProbe.totalQueries()).isEqualTo(EXPECTED_DASHBOARD_TASKS_QUERIES);
  }

  /**
   * {@code GET /api/dashboard/summary} のクエリ本数がタスク件数 N に依存せず定数であることを固定する。STAKEHOLDERS 認可は {@code
   * EXISTS} 相関サブクエリでループ外の単一 query に畳まれている。N+1 退行時は本数が N に比例して増える。
   */
  @Test
  void dashboardSummary_queryCountIsConstantRegardlessOfTaskCount() throws Exception {
    QueryCountProbe.reset();
    mockMvc
        .perform(
            get("/api/dashboard/summary")
                .header("X-Tenant-Id", String.valueOf(tenantId))
                .with(authentication(viewerToken)))
        .andExpect(status().isOk());

    assertThat(QueryCountProbe.totalQueries()).isEqualTo(EXPECTED_DASHBOARD_SUMMARY_QUERIES);
  }

  // 期待本数はローカル Testcontainers 実行で採取して pin(ADR-0039 §6)。内訳はテスト Javadoc 参照。
  private static final long EXPECTED_DASHBOARD_TASKS_QUERIES = 5L;
  private static final long EXPECTED_DASHBOARD_SUMMARY_QUERIES = 2L;
}
