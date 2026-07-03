package xyz.dgz48.tasks.webapi.task.adapter.web;

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
 * N+1 回帰固定テスト(ADR-0039 / #769)— タスク系エンドポイント。
 *
 * <p>複数の異なる所有者を持つタスク / 複数の関係者を「多めに」seed し、endpoint のクエリ本数がフィクスチャ件数 N に依存せず 定数であることを固定する。将来 {@code
 * loadUserMap} の一括解決や関係者 JOIN が誤って per-row ループ化(N+1)した場合、 本数が N に比例して増えるため本テストが落ちる。
 *
 * <ul>
 *   <li>{@code GET /api/tasks}(一覧)— {@code loadUserMap} が {@code findAllById} で 1 回一括解決。
 *   <li>{@code GET /api/tasks/{id}/stakeholders} — {@code users} を JOIN した単一 native query。
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
class TaskQueryCountIT {

  @Autowired MockMvc mockMvc;
  @Autowired EntityManager em;
  @Autowired TransactionTemplate txTemplate;

  @MockitoBean Clock clock;

  private static final LocalDate TODAY = LocalDate.of(2026, 1, 15);

  /** N+1 なら本数が N に比例するよう、所有者を全て別ユーザーにしたタスクを多めに seed する。 */
  private static final int OWNED_TASK_COUNT = 6;

  /** 関係者一覧 N+1 検出用に、単一タスクへ複数の別ユーザー関係者を登録する。 */
  private static final int STAKEHOLDER_COUNT = 4;

  private Long tenantId;
  private Long viewerId;
  private TasksAuthenticationToken viewerToken;
  private Long stakeholderTaskId;

  @BeforeEach
  void setUp() {
    when(clock.getZone()).thenReturn(AppZones.JST);
    when(clock.instant())
        .thenReturn(FixedClockConfiguration.FIXED_NOW.atZone(AppZones.JST).toInstant());

    txTemplate.execute(
        ignored -> {
          var viewer =
              new UserJpaEntity(
                  "sub-qc-viewer", "qc-viewer@example.com", "計数閲覧者", "けいすうえつらんしゃ", null);
          em.persist(viewer);
          em.flush();
          viewerId = viewer.getId();

          // JpaAuditorAware が created_by / updated_by を解決できるよう認証コンテキストを設定
          SecurityContextHolder.getContext()
              .setAuthentication(
                  new TasksAuthenticationToken(
                      new TasksPrincipal(
                          viewerId,
                          "sub-qc-viewer",
                          "qc-viewer@example.com",
                          "計数閲覧者",
                          "けいすうえつらんしゃ",
                          null),
                      List.of()));

          var tenant = new TenantJpaEntity("QC-1", "計数テストテナント");
          em.persist(tenant);
          em.flush();
          tenantId = tenant.getId();

          insertMembership(viewerId, tenantId);

          // 所有者が全て別ユーザーの TENANT タスクを複数 seed(viewer は tenant member なので全件参照可)
          for (int i = 0; i < OWNED_TASK_COUNT; i++) {
            var owner =
                new UserJpaEntity(
                    "sub-qc-owner-" + i,
                    "qc-owner-" + i + "@example.com",
                    "所有者" + i,
                    "しょゆうしゃ" + i,
                    null);
            em.persist(owner);
            em.flush();
            var task =
                new TaskJpaEntity(
                    tenantId,
                    owner.getId(),
                    "計数タスク" + i,
                    null,
                    TaskStatus.NOT_STARTED,
                    Priority.MEDIUM,
                    TODAY);
            em.persist(task);
            em.flush();
          }

          // 関係者一覧用: TENANT 可視のタスク 1 件 + 別ユーザー関係者を複数登録(viewer は member なので参照可)
          var stkOwner =
              new UserJpaEntity(
                  "sub-qc-stk-owner",
                  "qc-stk-owner@example.com",
                  "関係者タスク所有者",
                  "かんけいしゃたすくしょゆうしゃ",
                  null);
          em.persist(stkOwner);
          em.flush();
          var stkTask =
              new TaskJpaEntity(
                  tenantId,
                  stkOwner.getId(),
                  "関係者計数タスク",
                  null,
                  TaskStatus.NOT_STARTED,
                  Priority.MEDIUM,
                  TODAY);
          em.persist(stkTask);
          em.flush();
          stakeholderTaskId = stkTask.getId();

          for (int i = 0; i < STAKEHOLDER_COUNT; i++) {
            var s =
                new UserJpaEntity(
                    "sub-qc-stk-" + i,
                    "qc-stk-" + i + "@example.com",
                    "関係者" + i,
                    "かんけいしゃ" + i,
                    null);
            em.persist(s);
            em.flush();
            insertStakeholder(stakeholderTaskId, s.getId(), tenantId, stkOwner.getId());
          }

          return null;
        });

    SecurityContextHolder.clearContext();

    viewerToken =
        new TasksAuthenticationToken(
            new TasksPrincipal(
                viewerId, "sub-qc-viewer", "qc-viewer@example.com", "計数閲覧者", "けいすうえつらんしゃ", null),
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
          em.createNativeQuery("DELETE FROM users WHERE email LIKE 'qc-%@example.com'")
              .executeUpdate();
          return null;
        });
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

  private void insertStakeholder(Long taskId, Long userId, Long tenant, Long addedBy) {
    em.createNativeQuery(
            "INSERT INTO task_stakeholders (task_id, user_id, tenant_id, added_by, added_at)"
                + " VALUES (?,?,?,?,?)")
        .setParameter(1, taskId)
        .setParameter(2, userId)
        .setParameter(3, tenant)
        .setParameter(4, addedBy)
        .setParameter(5, LocalDateTime.of(2026, 1, 1, 0, 0))
        .executeUpdate();
  }

  /**
   * {@code GET /api/tasks} のクエリ本数が所有者数(N)に依存せず定数であることを固定する。内訳: TenantContextFilter の membership 解決
   * + 一覧の count / page / overdue 集計 + {@code loadUserMap} の一括 {@code findAllById}。N+1 退行時は
   * 所有者数だけ本数が増える。
   */
  @Test
  void listTasks_queryCountIsConstantRegardlessOfOwnerCount() throws Exception {
    QueryCountProbe.reset();
    mockMvc
        .perform(
            get("/api/tasks")
                .header("X-Tenant-Id", String.valueOf(tenantId))
                .with(authentication(viewerToken)))
        .andExpect(status().isOk());

    assertThat(QueryCountProbe.totalQueries()).isEqualTo(EXPECTED_LIST_TASKS_QUERIES);
  }

  /**
   * {@code GET /api/tasks/{id}/stakeholders} のクエリ本数が関係者数(N)に依存せず定数であることを固定する。関係者情報は {@code users} を
   * JOIN した単一 native query で解決される。N+1 退行時は関係者数だけ本数が増える。
   */
  @Test
  void listStakeholders_queryCountIsConstantRegardlessOfStakeholderCount() throws Exception {
    QueryCountProbe.reset();
    mockMvc
        .perform(
            get("/api/tasks/{id}/stakeholders", stakeholderTaskId)
                .header("X-Tenant-Id", String.valueOf(tenantId))
                .with(authentication(viewerToken)))
        .andExpect(status().isOk());

    assertThat(QueryCountProbe.totalQueries()).isEqualTo(EXPECTED_LIST_STAKEHOLDERS_QUERIES);
  }

  // 期待本数はローカル Testcontainers 実行で採取して pin(ADR-0039 §6)。内訳はテスト Javadoc 参照。
  private static final long EXPECTED_LIST_TASKS_QUERIES = 5L;
  private static final long EXPECTED_LIST_STAKEHOLDERS_QUERIES = 4L;
}
