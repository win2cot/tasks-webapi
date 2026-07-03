package xyz.dgz48.tasks.webapi.perf;

import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
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
import xyz.dgz48.tasks.webapi.task.adapter.persistence.TaskJpaEntity;
import xyz.dgz48.tasks.webapi.task.domain.Priority;
import xyz.dgz48.tasks.webapi.task.domain.TaskStatus;
import xyz.dgz48.tasks.webapi.tenant.adapter.persistence.TenantJpaEntity;
import xyz.dgz48.tasks.webapi.user.adapter.persistence.UserJpaEntity;

/**
 * ADR-0039 / #769 PR② — 主要 endpoint 代表クエリの index EXPLAIN 計測スクリプト。
 *
 * <p>Hibernate Filter がクエリに注入する {@code tenant_id = ?} を明示的に含めた「MySQL が実際に実行する形」の native SQL を
 * マルチテナント実配分(対象テナント ≒ 120 タスク / 全体 ≒ 16 テナント × 数千タスク)に対して {@code EXPLAIN} し、{@code type} / 使用 index
 * / {@code rows} / {@code Extra} を出力する。他テナントにも実データを積むのは、対象テナントの {@code tenant_id} を
 * 選択的にして計測ボリューム由来の見かけの full scan を避けるため(所見メモ §3.1 参照)。結果は {@code
 * docs/reviews/2026-07-03-adr0039-performance-findings.md} に記録する。CI ではスキップ({@code @Disabled})。ローカルで
 * 手動実行する際は {@code @Disabled} を外すこと。
 */
@Disabled("手動計測用スクリプト — CI では実行しない(ADR-0039 / #769)。所見は docs/reviews に記録済み。")
@SpringBootTest
@Import({TestcontainersConfiguration.class, MockJwtDecoderConfiguration.class})
@Transactional
class IndexExplainMeasurementIT {

  private static final int USERS = 30;
  private static final int TASKS = 120;
  private static final int OTHER_TENANTS = 15;

  /**
   * 他テナントにも実データを積み、対象テナントの {@code tenant_id = ?} が選択的になる「マルチテナント実配分」を再現する。これを積まないと 全行が 1
   * テナントに偏り、{@code tenant_id} index が効かず full scan が最適(=計測ボリューム由来の見かけの {@code type=ALL})に なってしまう。
   */
  private static final int FILLER_TASKS_PER_TENANT = 300;

  private Long auditorId;
  private static final LocalDate BASE = LocalDate.of(2026, 6, 15);
  private static final LocalDateTime DAY_START = LocalDate.of(2026, 6, 15).atStartOfDay();

  @Autowired EntityManager em;

  private Long tenantId;
  private Long userId;
  private Long stakeholderTaskId;

  @BeforeEach
  void seed() {
    var auditor =
        new UserJpaEntity("sub-ix-auditor", "ix-auditor@example.com", "計測監査", "ケイソクカンサ", null);
    em.persist(auditor);
    em.flush();
    auditorId = auditor.getId();
    SecurityContextHolder.getContext()
        .setAuthentication(
            new TasksAuthenticationToken(
                new TasksPrincipal(
                    auditor.getId(),
                    "sub-ix-auditor",
                    "ix-auditor@example.com",
                    "計測監査",
                    "ケイソクカンサ",
                    null),
                List.of()));

    var tenant = new TenantJpaEntity("IX-1", "計測テナント");
    em.persist(tenant);
    em.flush();
    tenantId = tenant.getId();

    var userIds = new Long[USERS];
    for (int i = 0; i < USERS; i++) {
      var u =
          new UserJpaEntity(
              "sub-ix-u" + i, "ix-u" + i + "@example.com", "利用者" + i, "りようしゃ" + i, null);
      em.persist(u);
      em.flush();
      userIds[i] = u.getId();
      insertMembership(userIds[i], tenantId);
    }
    userId = userIds[0];

    var visibilities = new String[] {"TENANT", "STAKEHOLDERS", "PRIVATE"};
    var statuses =
        new TaskStatus[] {
          TaskStatus.NOT_STARTED, TaskStatus.IN_PROGRESS, TaskStatus.DONE, TaskStatus.NOT_STARTED
        };
    Long firstStakeholderTask = null;
    for (int i = 0; i < TASKS; i++) {
      var status = statuses[i % statuses.length];
      var task =
          new TaskJpaEntity(
              tenantId,
              userIds[i % USERS],
              "計測タスク" + i,
              i % 5 == 0 ? "四半期レビュー資料" : null,
              status,
              Priority.values()[i % Priority.values().length],
              BASE.plusDays((i % 60) - 30));
      em.persist(task);
      em.flush();
      var vis = visibilities[i % visibilities.length];
      em.createNativeQuery("UPDATE tasks SET visibility = ? WHERE id = ?")
          .setParameter(1, vis)
          .setParameter(2, task.getId())
          .executeUpdate();
      if (i % 3 == 0) {
        em.createNativeQuery("UPDATE tasks SET assignee_id = ? WHERE id = ?")
            .setParameter(1, userIds[(i + 1) % USERS])
            .setParameter(2, task.getId())
            .executeUpdate();
      }
      if (status == TaskStatus.DONE) {
        em.createNativeQuery("UPDATE tasks SET completed_at = ? WHERE id = ?")
            .setParameter(1, DAY_START.plusHours(i % 12))
            .setParameter(2, task.getId())
            .executeUpdate();
      }
      if ("STAKEHOLDERS".equals(vis)) {
        if (firstStakeholderTask == null) {
          firstStakeholderTask = task.getId();
        }
        for (int s = 0; s < 3; s++) {
          insertStakeholder(task.getId(), userIds[(i + s) % USERS], tenantId, userIds[i % USERS]);
        }
      }
    }
    stakeholderTaskId = firstStakeholderTask;

    // 他テナントにも実データを積み、対象テナントの tenant_id を選択的にする(マルチテナント実配分の再現)。
    int flushCounter = 0;
    for (int t = 0; t < OTHER_TENANTS; t++) {
      var other = new TenantJpaEntity("IX-OTHER-" + t, "他テナント" + t);
      em.persist(other);
      em.flush();
      var otherTenantId = other.getId();
      for (int i = 0; i < FILLER_TASKS_PER_TENANT; i++) {
        em.persist(
            new TaskJpaEntity(
                otherTenantId,
                auditorId,
                "他テナントタスク" + t + "-" + i,
                null,
                TaskStatus.NOT_STARTED,
                Priority.MEDIUM,
                BASE.plusDays((i % 60) - 30)));
        if (++flushCounter % 200 == 0) {
          em.flush();
          em.clear();
          // em.clear() で detached になるため以降の参照はしない(挿入専用ループ)
        }
      }
    }
    em.flush();
    em.clear();
  }

  @Test
  void explainRepresentativeQueries() {
    var today = java.sql.Date.valueOf(BASE);
    var upper = java.sql.Date.valueOf(BASE.plusDays(7));
    var dayStart = java.sql.Timestamp.valueOf(DAY_START);
    var dayEnd = java.sql.Timestamp.valueOf(DAY_START.plusDays(1));

    // A-1: タスク一覧 データ取得(認可述語 + 既定 due_date ソート + paging)
    explain(
        "A-1 GET /api/tasks (data, auth predicate, ORDER BY due_date, LIMIT)",
        "EXPLAIN SELECT t.id FROM tasks t"
            + " WHERE t.tenant_id = ? AND t.deleted_at IS NULL"
            + " AND (t.visibility = 'TENANT' OR t.owner_id = ? OR t.assignee_id = ?"
            + "   OR (t.visibility = 'STAKEHOLDERS' AND EXISTS ("
            + "     SELECT 1 FROM task_stakeholders ts"
            + "     WHERE ts.task_id = t.id AND ts.user_id = ? AND ts.tenant_id = ?)))"
            + " ORDER BY t.due_date ASC LIMIT 20",
        tenantId,
        userId,
        userId,
        userId,
        tenantId);

    // A-2: タスク一覧 COUNT(同一述語)
    explain(
        "A-2 GET /api/tasks (count, auth predicate)",
        "EXPLAIN SELECT COUNT(t.id) FROM tasks t"
            + " WHERE t.tenant_id = ? AND t.deleted_at IS NULL"
            + " AND (t.visibility = 'TENANT' OR t.owner_id = ? OR t.assignee_id = ?"
            + "   OR (t.visibility = 'STAKEHOLDERS' AND EXISTS ("
            + "     SELECT 1 FROM task_stakeholders ts"
            + "     WHERE ts.task_id = t.id AND ts.user_id = ? AND ts.tenant_id = ?)))",
        tenantId,
        userId,
        userId,
        userId,
        tenantId);

    // A-3: keyword LIKE(前方ワイルドカード=index 不可、ADR-0039 §5 は所見記録のみ)
    explain(
        "A-3 GET /api/tasks (keyword LIKE %kw%, leading wildcard — record only)",
        "EXPLAIN SELECT t.id FROM tasks t"
            + " WHERE t.tenant_id = ? AND t.deleted_at IS NULL"
            + " AND (LOWER(t.title) LIKE '%レビュー%' OR LOWER(t.description) LIKE '%レビュー%')"
            + " ORDER BY t.due_date ASC LIMIT 20",
        tenantId);

    // A-4: overdue 件数(認可述語 + due_date < today AND status <> DONE)
    explain(
        "A-4 GET /api/tasks (overdueCount)",
        "EXPLAIN SELECT COUNT(t.id) FROM tasks t"
            + " WHERE t.tenant_id = ? AND t.deleted_at IS NULL"
            + " AND (t.visibility = 'TENANT' OR t.owner_id = ? OR t.assignee_id = ?)"
            + " AND t.due_date < ? AND t.status <> 'DONE'",
        tenantId,
        userId,
        userId,
        today);

    // B-1: 関係者一覧(native JOIN users x2)
    explain(
        "B-1 GET /api/tasks/{id}/stakeholders (native JOIN)",
        "EXPLAIN SELECT ts.user_id, u.full_name, u.email, ts.added_by, ab.full_name, ts.added_at"
            + " FROM task_stakeholders ts"
            + " JOIN users u ON ts.user_id = u.id"
            + " JOIN users ab ON ts.added_by = ab.id"
            + " WHERE ts.task_id = ? AND ts.tenant_id = ?"
            + " ORDER BY ts.added_at ASC",
        stakeholderTaskId,
        tenantId);

    // C-1: dashboard overdue
    explain(
        "C-1 GET /api/dashboard/tasks findOverdue",
        "EXPLAIN SELECT t.id FROM tasks t"
            + " WHERE t.tenant_id = ? AND t.deleted_at IS NULL"
            + " AND (t.owner_id = ? OR t.assignee_id = ?) AND t.due_date < ? AND t.status <> 'DONE'"
            + " ORDER BY t.due_date ASC",
        tenantId,
        userId,
        userId,
        today);

    // C-2: dashboard today
    explain(
        "C-2 GET /api/dashboard/tasks findToday",
        "EXPLAIN SELECT t.id FROM tasks t"
            + " WHERE t.tenant_id = ? AND t.deleted_at IS NULL"
            + " AND (t.owner_id = ? OR t.assignee_id = ?) AND t.due_date = ?",
        tenantId,
        userId,
        userId,
        today);

    // C-3: dashboard upcoming
    explain(
        "C-3 GET /api/dashboard/tasks findUpcoming",
        "EXPLAIN SELECT t.id FROM tasks t"
            + " WHERE t.tenant_id = ? AND t.deleted_at IS NULL"
            + " AND (t.owner_id = ? OR t.assignee_id = ?)"
            + " AND t.due_date > ? AND t.due_date <= ? AND t.status <> 'DONE'"
            + " ORDER BY t.due_date ASC",
        tenantId,
        userId,
        userId,
        today,
        upper);

    // C-4: dashboard completedToday
    explain(
        "C-4 GET /api/dashboard/tasks findCompletedToday",
        "EXPLAIN SELECT t.id FROM tasks t"
            + " WHERE t.tenant_id = ? AND t.deleted_at IS NULL"
            + " AND (t.owner_id = ? OR t.assignee_id = ?)"
            + " AND t.status = 'DONE' AND t.completed_at >= ? AND t.completed_at < ?"
            + " ORDER BY t.completed_at DESC",
        tenantId,
        userId,
        userId,
        dayStart,
        dayEnd);

    // D-1: dashboard summary(EXISTS 相関サブクエリ)
    explain(
        "D-1 GET /api/dashboard/summary findVisibleSummaryRows",
        "EXPLAIN SELECT t.status, t.priority, t.due_date, t.completed_at, t.owner_id FROM tasks t"
            + " WHERE t.tenant_id = ? AND t.deleted_at IS NULL"
            + " AND (t.visibility = 'TENANT' OR t.owner_id = ? OR t.assignee_id = ?"
            + "   OR (t.visibility = 'STAKEHOLDERS' AND EXISTS ("
            + "     SELECT 1 FROM task_stakeholders s"
            + "     WHERE s.task_id = t.id AND s.user_id = ? AND s.tenant_id = ?)))",
        tenantId,
        userId,
        userId,
        userId,
        tenantId);

    // D-2: tenant dashboard summary
    explain(
        "D-2 GET /api/tenant/dashboard/summary findTenantVisibleSummaryRows",
        "EXPLAIN SELECT t.status, t.priority, t.due_date, t.completed_at, t.owner_id FROM tasks t"
            + " WHERE t.tenant_id = ? AND t.deleted_at IS NULL"
            + " AND t.visibility IN ('TENANT','STAKEHOLDERS')",
        tenantId);

    // D-3: countActiveMembers(native)
    explain(
        "D-3 GET /api/tenant/dashboard/summary countActiveMembers",
        "EXPLAIN SELECT COUNT(*) FROM user_tenants WHERE tenant_id = ? AND status = 'ACTIVE'",
        tenantId);

    // E-1: テナントユーザー一覧(derived, ORDER BY joined_at)
    explain(
        "E-1 GET /api/tenant/users findByIdTenantIdOrderByJoinedAtAsc",
        "EXPLAIN SELECT ut.user_id, ut.tenant_id, ut.role, ut.status, ut.joined_at"
            + " FROM user_tenants ut WHERE ut.tenant_id = ? ORDER BY ut.joined_at ASC",
        tenantId);

    // F-1: 管理者テナント一覧(status + name LIKE、小テーブル)
    explain(
        "F-1 GET /api/tenants findAllFiltered (status + name LIKE %kw%)",
        "EXPLAIN SELECT t.id, t.code, t.name, t.status FROM tenants t"
            + " WHERE (t.status = 'ACTIVE') AND (t.name LIKE '%計測%')"
            + " ORDER BY t.id DESC LIMIT 20",
        new Object[0]);

    // F-2: テナント別タスク件数集計(native, IN batch)
    explain(
        "F-2 GET /api/tenants countTasksByTenantIds",
        "EXPLAIN SELECT tenant_id, COUNT(*) FROM tasks"
            + " WHERE tenant_id IN (?) AND deleted_at IS NULL GROUP BY tenant_id",
        tenantId);
  }

  private void insertMembership(Long user, Long tenant) {
    em.createNativeQuery(
            "INSERT INTO user_tenants (user_id, tenant_id, role, status, joined_at) VALUES (?,?,?,?,?)")
        .setParameter(1, user)
        .setParameter(2, tenant)
        .setParameter(3, "MEMBER")
        .setParameter(4, "ACTIVE")
        .setParameter(5, LocalDateTime.of(2026, 1, 1, 0, 0))
        .executeUpdate();
  }

  private void insertStakeholder(Long taskId, Long user, Long tenant, Long addedBy) {
    em.createNativeQuery(
            "INSERT IGNORE INTO task_stakeholders (task_id, user_id, tenant_id, added_by, added_at)"
                + " VALUES (?,?,?,?,?)")
        .setParameter(1, taskId)
        .setParameter(2, user)
        .setParameter(3, tenant)
        .setParameter(4, addedBy)
        .setParameter(5, LocalDateTime.of(2026, 1, 1, 0, 0))
        .executeUpdate();
  }

  private void explain(String label, String sql, Object... params) {
    var q = em.createNativeQuery(sql);
    for (int i = 0; i < params.length; i++) {
      q.setParameter(i + 1, params[i]);
    }
    @SuppressWarnings("unchecked")
    List<Object[]> rows = q.getResultList();
    System.out.println("\n[EXPLAIN] " + label);
    for (Object[] row : rows) {
      // MySQL EXPLAIN cols: 0 id,1 select_type,2 table,3 partitions,4 type,5 possible_keys,
      //                     6 key,7 key_len,8 ref,9 rows,10 filtered,11 Extra
      System.out.printf(
          "  select_type=%-12s table=%-18s type=%-8s key=%-26s rows=%-6s filtered=%-6s Extra=%s%n",
          str(row[1]),
          str(row[2]),
          str(row[4]),
          str(row[6]),
          str(row[9]),
          str(row[10]),
          str(row[11]));
    }
  }

  private String str(Object o) {
    return o == null ? "NULL" : String.valueOf(o);
  }
}
