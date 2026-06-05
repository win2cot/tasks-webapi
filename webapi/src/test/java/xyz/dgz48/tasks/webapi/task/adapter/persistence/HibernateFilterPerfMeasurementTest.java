package xyz.dgz48.tasks.webapi.task.adapter.persistence;

import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import org.hibernate.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import xyz.dgz48.tasks.webapi.MockJwtDecoderConfiguration;
import xyz.dgz48.tasks.webapi.TestcontainersConfiguration;
import xyz.dgz48.tasks.webapi.security.adapter.web.TasksAuthenticationToken;
import xyz.dgz48.tasks.webapi.security.domain.TasksPrincipal;
import xyz.dgz48.tasks.webapi.task.domain.Priority;
import xyz.dgz48.tasks.webapi.task.domain.TaskStatus;
import xyz.dgz48.tasks.webapi.tenant.adapter.persistence.TenantJpaEntity;
import xyz.dgz48.tasks.webapi.user.adapter.persistence.UserJpaEntity;

/**
 * Hibernate Filter パフォーマンス計測スクリプト — Issue #316 D-3
 *
 * <p>3 シナリオ（一覧取得 / 単件取得 / 作成）において filter あり/なしの EXPLAIN と timing を比較する。CI
 * ではスキップ（@Disabled）。ローカルで手動実行する際は @Disabled を外して実行すること。結果は
 * docs/reviews/2026-06-05-hibernate-filter-perf.md に記録済み。
 */
@Disabled("手動計測用スクリプト — CI では実行しない(Issue #316)")
@SpringBootTest
@Import({TestcontainersConfiguration.class, MockJwtDecoderConfiguration.class})
@Transactional
class HibernateFilterPerfMeasurementTest {

  private static final int TASKS_PER_TENANT = 200;
  private static final int WARMUP_ITERATIONS = 10;
  private static final int MEASURE_ITERATIONS = 100;

  @Autowired EntityManager entityManager;
  @Autowired TaskJpaRepository taskJpaRepository;

  private Long tenantAId;
  private Long tenantBId;
  private Long userId;
  private Long sampleTaskId;

  @BeforeEach
  void setUp() {
    var auditor =
        new UserJpaEntity(
            "sub-perf-auditor", "perf-auditor@example.com", "計測監査", "ケイソクカンサ", null);
    entityManager.persist(auditor);
    entityManager.flush();

    var principal =
        new TasksPrincipal(
            auditor.getId(),
            "sub-perf-auditor",
            "perf-auditor@example.com",
            "計測監査",
            "ケイソクカンサ",
            null);
    SecurityContextHolder.getContext()
        .setAuthentication(new TasksAuthenticationToken(principal, List.of()));

    var tenantA = new TenantJpaEntity("PERF-A", "計測テナントA");
    var tenantB = new TenantJpaEntity("PERF-B", "計測テナントB");
    entityManager.persist(tenantA);
    entityManager.persist(tenantB);

    var user =
        new UserJpaEntity(
            "sub-perf-user", "perf-user@example.com", "計測ユーザー", "ケイソクユーザー", null);
    entityManager.persist(user);
    entityManager.flush();

    tenantAId = tenantA.getId();
    tenantBId = tenantB.getId();
    userId = user.getId();

    for (int i = 0; i < TASKS_PER_TENANT; i++) {
      entityManager.persist(
          new TaskJpaEntity(
              tenantAId,
              userId,
              "テナントAタスク" + i,
              null,
              TaskStatus.NOT_STARTED,
              Priority.MEDIUM,
              LocalDate.of(2026, 12, 31).minusDays(i % 30)));
      entityManager.persist(
          new TaskJpaEntity(
              tenantBId,
              userId,
              "テナントBタスク" + i,
              null,
              TaskStatus.NOT_STARTED,
              Priority.MEDIUM,
              LocalDate.of(2026, 12, 31).minusDays(i % 30)));
    }
    entityManager.flush();
    entityManager.clear();

    sampleTaskId = taskJpaRepository.findAll().stream().findFirst().orElseThrow().getId();
  }

  @AfterEach
  void clearContexts() {
    SecurityContextHolder.clearContext();
    try {
      entityManager.unwrap(Session.class).disableFilter("tenantFilter");
    } catch (Exception ignored) {
      // filter が未有効の場合は無視
    }
  }

  @Test
  void scenario1_listTasks() {
    System.out.println("\n=== Scenario 1: List Tasks — findAll() ===");
    System.out.println(
        "Data: " + TASKS_PER_TENANT + " tasks x 2 tenants = " + (TASKS_PER_TENANT * 2) + " total");

    Session session = entityManager.unwrap(Session.class);

    explain(
        "filter=OFF  SELECT FROM tasks ORDER BY due_date",
        "EXPLAIN SELECT t.id, t.tenant_id, t.title, t.status, t.due_date"
            + " FROM tasks t ORDER BY t.due_date",
        new Object[0]);

    explain(
        "filter=ON   SELECT FROM tasks WHERE tenant_id=? ORDER BY due_date",
        "EXPLAIN SELECT t.id, t.tenant_id, t.title, t.status, t.due_date"
            + " FROM tasks t WHERE t.tenant_id = ? ORDER BY t.due_date",
        new Object[] {tenantAId});

    session.disableFilter("tenantFilter");
    for (int i = 0; i < WARMUP_ITERATIONS; i++) {
      taskJpaRepository.findAll();
      entityManager.clear();
    }
    long startOff = System.nanoTime();
    for (int i = 0; i < MEASURE_ITERATIONS; i++) {
      taskJpaRepository.findAll();
      entityManager.clear();
    }
    double avgOff = (System.nanoTime() - startOff) / 1_000_000.0 / MEASURE_ITERATIONS;

    session.enableFilter("tenantFilter").setParameter("tenantId", tenantAId);
    for (int i = 0; i < WARMUP_ITERATIONS; i++) {
      taskJpaRepository.findAll();
      entityManager.clear();
    }
    long startOn = System.nanoTime();
    for (int i = 0; i < MEASURE_ITERATIONS; i++) {
      taskJpaRepository.findAll();
      entityManager.clear();
    }
    double avgOn = (System.nanoTime() - startOn) / 1_000_000.0 / MEASURE_ITERATIONS;

    System.out.printf("[Timing] filter=OFF avg: %.3f ms (%d iters)%n", avgOff, MEASURE_ITERATIONS);
    System.out.printf("[Timing] filter=ON  avg: %.3f ms (%d iters)%n", avgOn, MEASURE_ITERATIONS);
    System.out.printf("[Ratio]  ON/OFF = %.2fx%n", avgOn / avgOff);
  }

  @Test
  void scenario2_findById() {
    System.out.println("\n=== Scenario 2: GET /tasks/{id} — findById ===");
    System.out.println("Target task ID: " + sampleTaskId);

    Session session = entityManager.unwrap(Session.class);

    explain(
        "filter=OFF  SELECT FROM tasks WHERE id=?",
        "EXPLAIN SELECT t.id, t.tenant_id, t.title, t.status FROM tasks t WHERE t.id = ?",
        new Object[] {sampleTaskId});

    explain(
        "filter=ON   SELECT FROM tasks WHERE id=? AND tenant_id=?",
        "EXPLAIN SELECT t.id, t.tenant_id, t.title, t.status FROM tasks t"
            + " WHERE t.id = ? AND t.tenant_id = ?",
        new Object[] {sampleTaskId, tenantAId});

    session.disableFilter("tenantFilter");
    for (int i = 0; i < WARMUP_ITERATIONS; i++) {
      taskJpaRepository.findById(sampleTaskId);
      entityManager.clear();
    }
    long startOff = System.nanoTime();
    for (int i = 0; i < MEASURE_ITERATIONS; i++) {
      taskJpaRepository.findById(sampleTaskId);
      entityManager.clear();
    }
    double avgOff = (System.nanoTime() - startOff) / 1_000_000.0 / MEASURE_ITERATIONS;

    session.enableFilter("tenantFilter").setParameter("tenantId", tenantAId);
    for (int i = 0; i < WARMUP_ITERATIONS; i++) {
      taskJpaRepository.findById(sampleTaskId);
      entityManager.clear();
    }
    long startOn = System.nanoTime();
    for (int i = 0; i < MEASURE_ITERATIONS; i++) {
      taskJpaRepository.findById(sampleTaskId);
      entityManager.clear();
    }
    double avgOn = (System.nanoTime() - startOn) / 1_000_000.0 / MEASURE_ITERATIONS;

    System.out.printf("[Timing] filter=OFF avg: %.3f ms (%d iters)%n", avgOff, MEASURE_ITERATIONS);
    System.out.printf("[Timing] filter=ON  avg: %.3f ms (%d iters)%n", avgOn, MEASURE_ITERATIONS);
    System.out.printf("[Ratio]  ON/OFF = %.2fx%n", avgOn / avgOff);
  }

  @Test
  void scenario3_insertTask() {
    System.out.println("\n=== Scenario 3: POST /tasks — INSERT ===");
    System.out.println("[Note] Hibernate Filter does not apply to INSERT");

    entityManager.unwrap(Session.class).disableFilter("tenantFilter");

    for (int i = 0; i < WARMUP_ITERATIONS; i++) {
      entityManager.persist(
          new TaskJpaEntity(
              tenantAId,
              userId,
              "warmup" + i,
              null,
              TaskStatus.NOT_STARTED,
              Priority.LOW,
              LocalDate.of(2027, 1, 1)));
      entityManager.flush();
    }

    long start = System.nanoTime();
    for (int i = 0; i < MEASURE_ITERATIONS; i++) {
      entityManager.persist(
          new TaskJpaEntity(
              tenantAId,
              userId,
              "計測タスク" + i,
              null,
              TaskStatus.NOT_STARTED,
              Priority.MEDIUM,
              LocalDate.of(2027, 1, 1)));
      entityManager.flush();
    }
    double avg = (System.nanoTime() - start) / 1_000_000.0 / MEASURE_ITERATIONS;

    System.out.printf("[Timing] INSERT avg: %.3f ms (%d iters)%n", avg, MEASURE_ITERATIONS);
  }

  @SuppressWarnings("unchecked")
  private void explain(String label, String sql, Object[] params) {
    var q = entityManager.createNativeQuery(sql);
    for (int i = 0; i < params.length; i++) {
      q.setParameter(i + 1, params[i]);
    }
    // MySQL EXPLAIN columns: id, select_type, table, partitions, type,
    //                        possible_keys, key, key_len, ref, rows, filtered, Extra
    List<Object[]> rows = q.getResultList();
    System.out.println("\n[EXPLAIN] " + label + ":");
    for (Object[] row : rows) {
      System.out.printf(
          "  type=%-6s key=%-25s rows=%-5s filtered=%-6s Extra=%s%n",
          str(row[4]), str(row[6]), str(row[9]), str(row[10]), str(row[11]));
    }
  }

  private String str(Object o) {
    return o == null ? "NULL" : String.valueOf(o);
  }
}
