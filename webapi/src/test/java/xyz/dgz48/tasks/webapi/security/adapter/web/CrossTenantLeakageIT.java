package xyz.dgz48.tasks.webapi.security.adapter.web;

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
import xyz.dgz48.tasks.webapi.security.domain.TasksPrincipal;
import xyz.dgz48.tasks.webapi.task.adapter.persistence.TaskJpaEntity;
import xyz.dgz48.tasks.webapi.task.domain.Priority;
import xyz.dgz48.tasks.webapi.task.domain.TaskStatus;
import xyz.dgz48.tasks.webapi.tenant.adapter.persistence.TenantJpaEntity;
import xyz.dgz48.tasks.webapi.user.adapter.persistence.UserJpaEntity;

/**
 * R-05(クロステナントデータ漏洩)緩和の HTTP 層 横断 受け入れ IT(Issue #767 / ADR-0010)。
 *
 * <p>越境遮断は 2 つの独立した防御層で成立する。本 IT は <strong>参照系 endpoint</strong> を実エンドポイント + 実 DB (Testcontainers
 * MySQL 8.4)で通し、各層が業務ロジックの手前で fail-closed になることを feature 横断で固定する:
 *
 * <ol>
 *   <li><strong>{@link TenantContextFilter}(コンテキスト確立層)</strong>: 非メンバーテナントを {@code X-Tenant-Id}
 *       に指定した参照要求は、テナントコンテキスト確立段階で <strong>403</strong>(設計規約 HTTP ステータス方針「テナント越境 =
 *       403」)。読み取り経路でもこの遮断が効くことを検証する(更新経路は {@code CrossTenantWriteForbiddenIT} で検証済)。不正値(非数値)の
 *       {@code X-Tenant-Id} は <strong>400</strong>。
 *   <li><strong>Hibernate Filter(データアクセス層)</strong>: 自テナントの有効なコンテキストでは別テナントの資源 ID が
 *       <strong>不可視</strong>になり、参照は <strong>404</strong>(NIST AC-4 — 存在を漏らさない)。ヘッダ未指定時の初期テナント自動解決
 *       (ADR-0016)を経た場合も同様に越境資源は不可視であることを検証する。
 * </ol>
 *
 * <p>本 IT は「参照系の非メンバーヘッダ 403」「実エンドポイントでの未指定/不正ヘッダ挙動」という、既存テストでは {@code ProbeController}({@code
 * TenantContextFilterTest})経由でしか押さえられていなかった経路を、実 feature endpoint で補完する。 関連する既存の越境テスト(本 IT
 * で重複させない):
 *
 * <ul>
 *   <li>参照 自ctx・別資源 → 404: {@code TaskCrossTenantIT}、一覧分離 {@code ListTasksIT.CrossTenantIsolation}、
 *       ダッシュボード集計分離 {@code DashboardIT.CrossTenantIsolation}
 *   <li>更新 非メンバーヘッダ → 403 / 自ctx別資源 → 404: {@code CrossTenantWriteForbiddenIT}
 *   <li>3 経路(PK ロード / メソッド名導出 / bulk DML)の永続化層 fail-closed: {@code TenantFilterFailClosedIT}
 *   <li>テナント運営操作の越境 → 403: {@code TenantMemberIT}、切替時の越境不可視 → 404: {@code TenantSwitchContextIT}
 *   <li>通知バッチ(Filter 非適用)のテナント単位分離: {@code DueTodayNotificationQueryIT}
 *   <li>TenantContext 未設定での tenant-filtered テーブルアクセス検知: {@code CrossTenantViolationDetectionIT}
 *   <li>未指定/不正ヘッダのフィルタ単体挙動: {@code TenantContextFilterTest}
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({
  TestcontainersConfiguration.class,
  MockJwtDecoderConfiguration.class,
  FixedClockConfiguration.class
})
class CrossTenantLeakageIT {

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
              new UserJpaEntity("sub-xtl", "xtl@example.com", "越境参照太郎", "エッキョウサンショウタロウ", null);
          em.persist(user);
          em.flush();
          userId = user.getId();

          var principal =
              new TasksPrincipal(
                  userId, "sub-xtl", "xtl@example.com", "越境参照太郎", "エッキョウサンショウタロウ", null);
          SecurityContextHolder.getContext()
              .setAuthentication(new TasksAuthenticationToken(principal, List.of()));

          var tenantA = new TenantJpaEntity("XTL-A", "越境参照A");
          var tenantB = new TenantJpaEntity("XTL-B", "越境参照B");
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

          // 参照対象のタスクは tenantB に存在する(user は越境してこれを参照しようとする)
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
        new TasksPrincipal(userId, "sub-xtl", "xtl@example.com", "越境参照太郎", "エッキョウサンショウタロウ", null);
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

  // --- コンテキスト確立層: 非メンバーヘッダ参照 → 403 ---

  @Test
  void taskGetById_nonMemberTenantHeader_returns403() throws Exception {
    // tenantB は user の非所属テナント → TenantContextFilter がコンテキスト確立段階で 403(参照経路でも遮断)
    mockMvc
        .perform(
            get("/api/tasks/" + taskBId)
                .header("X-Tenant-Id", String.valueOf(tenantBId))
                .with(authentication(authToken)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("E_FORBIDDEN"));
  }

  @Test
  void taskList_nonMemberTenantHeader_returns403() throws Exception {
    mockMvc
        .perform(
            get("/api/tasks")
                .header("X-Tenant-Id", String.valueOf(tenantBId))
                .with(authentication(authToken)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("E_FORBIDDEN"));
  }

  @Test
  void dashboardTasks_nonMemberTenantHeader_returns403() throws Exception {
    mockMvc
        .perform(
            get("/api/dashboard/tasks")
                .header("X-Tenant-Id", String.valueOf(tenantBId))
                .with(authentication(authToken)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("E_FORBIDDEN"));
  }

  @Test
  void dashboardSummary_nonMemberTenantHeader_returns403() throws Exception {
    mockMvc
        .perform(
            get("/api/dashboard/summary")
                .header("X-Tenant-Id", String.valueOf(tenantBId))
                .with(authentication(authToken)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("E_FORBIDDEN"));
  }

  // --- コンテキスト確立層: 不正値ヘッダ → 400 ---

  @Test
  void taskGetById_invalidTenantHeader_returns400() throws Exception {
    // 非数値の X-Tenant-Id は実エンドポイントでも E_VALIDATION 400(コンテキスト確立前に弾く)
    mockMvc
        .perform(
            get("/api/tasks/" + taskBId)
                .header("X-Tenant-Id", "not-a-number")
                .with(authentication(authToken)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("E_VALIDATION"));
  }

  // --- データアクセス層: 自ctx / 未指定(自動解決)で別資源は不可視 → 404 ---

  @Test
  void taskGetById_ownTenantContext_foreignTask_returns404() throws Exception {
    // 自テナント(A)コンテキストでは tenantB のタスクは Hibernate Filter により不可視 → 参照 404(存在を漏らさない)
    mockMvc
        .perform(
            get("/api/tasks/" + taskBId)
                .header("X-Tenant-Id", String.valueOf(tenantAId))
                .with(authentication(authToken)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("E_NOT_FOUND"));
  }

  @Test
  void taskGetById_noHeader_autoResolvedTenant_foreignTask_returns404() throws Exception {
    // ヘッダ未指定 → 所属する唯一のテナント A に自動解決(ADR-0016)→ tenantB のタスクは不可視 → 404
    mockMvc
        .perform(get("/api/tasks/" + taskBId).with(authentication(authToken)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("E_NOT_FOUND"));
  }
}
