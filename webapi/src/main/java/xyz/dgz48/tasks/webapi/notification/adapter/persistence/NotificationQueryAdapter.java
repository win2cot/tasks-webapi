package xyz.dgz48.tasks.webapi.notification.adapter.persistence;

import io.micrometer.observation.annotation.Observed;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import xyz.dgz48.tasks.webapi.notification.domain.DueTodayNotification;
import xyz.dgz48.tasks.webapi.notification.domain.DueTodayNotification.DueTask;
import xyz.dgz48.tasks.webapi.notification.usecase.DueTodayNotificationQueryPort;

/**
 * {@link DueTodayNotificationQueryPort} の JPA 実装。
 *
 * <p><b>バッチ横断クエリ</b>: 全テナントの当日期限通知対象を 1 度に抽出する。バッチには {@code TenantContext} が無く Hibernate
 * Filter(ADR-0010)は適用されないため、native query で {@code tenant_id} を明示保持する(設計規約 §3.3 native 許容(1)= バッチ集計
 * / §8.2 テナント分離)。受信者の通知は結果を {@code (tenant_id, user_id)} でグループ化することでテナント単位に分離する。
 */
@Observed(name = "notification.query")
@Component
@RequiredArgsConstructor
class NotificationQueryAdapter implements DueTodayNotificationQueryPort {

  // native 理由: モジュール境界(task/user/notification)を越えた 3 テーブル結合のバッチ抽出。
  // tenant_id 明示: バッチは Filter 非適用のため tenant_id を結果に保持し受信者をテナント単位に分離(設計規約 §3.3 / §8.2)。
  private static final String DUE_TODAY_SQL =
      """
      SELECT t.tenant_id, u.id, u.email, u.full_name, t.id, t.title
      FROM tasks t
      JOIN users u ON (u.id = t.owner_id OR u.id = t.assignee_id)
      LEFT JOIN user_notification_settings s
        ON s.user_id = u.id AND s.tenant_id = t.tenant_id
      WHERE t.deleted_at IS NULL
        AND t.status <> 'DONE'
        AND t.due_date = :today
        AND u.status = 'ACTIVE'
        AND u.deleted_at IS NULL
        AND COALESCE(s.email_due_today, TRUE) = TRUE
      ORDER BY t.tenant_id, u.id, t.id
      """;

  private final EntityManager em;

  @Override
  @Transactional(readOnly = true)
  public List<DueTodayNotification> findDueTodayRecipients(LocalDate today) {
    @SuppressWarnings("unchecked")
    List<Object[]> rows =
        em.createNativeQuery(DUE_TODAY_SQL).setParameter("today", today).getResultList();

    Map<RecipientKey, Accumulator> grouped = new LinkedHashMap<>();
    for (Object[] row : rows) {
      long tenantId = ((Number) row[0]).longValue();
      long userId = ((Number) row[1]).longValue();
      String email = (String) row[2];
      String fullName = (String) row[3];
      long taskId = ((Number) row[4]).longValue();
      String title = (String) row[5];

      grouped
          .computeIfAbsent(
              new RecipientKey(tenantId, userId), k -> new Accumulator(email, fullName))
          .tasks
          .add(new DueTask(taskId, title));
    }

    List<DueTodayNotification> result = new ArrayList<>(grouped.size());
    grouped.forEach(
        (key, acc) ->
            result.add(
                new DueTodayNotification(
                    key.tenantId(), key.userId(), acc.email, acc.fullName, acc.tasks)));
    return result;
  }

  private record RecipientKey(long tenantId, long userId) {}

  private static final class Accumulator {
    private final String email;
    private final String fullName;
    private final List<DueTask> tasks = new ArrayList<>();

    Accumulator(String email, String fullName) {
      this.email = email;
      this.fullName = fullName;
    }
  }
}
