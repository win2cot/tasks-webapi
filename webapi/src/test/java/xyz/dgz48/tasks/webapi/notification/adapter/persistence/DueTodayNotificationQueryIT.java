package xyz.dgz48.tasks.webapi.notification.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionTemplate;
import xyz.dgz48.tasks.webapi.MockJwtDecoderConfiguration;
import xyz.dgz48.tasks.webapi.TestcontainersConfiguration;
import xyz.dgz48.tasks.webapi.notification.domain.DueTodayNotification;
import xyz.dgz48.tasks.webapi.notification.usecase.DueTodayNotificationQueryPort;
import xyz.dgz48.tasks.webapi.security.adapter.web.TasksAuthenticationToken;
import xyz.dgz48.tasks.webapi.security.domain.TasksPrincipal;
import xyz.dgz48.tasks.webapi.task.adapter.persistence.TaskJpaEntity;
import xyz.dgz48.tasks.webapi.task.domain.Priority;
import xyz.dgz48.tasks.webapi.task.domain.TaskStatus;
import xyz.dgz48.tasks.webapi.task.domain.Visibility;
import xyz.dgz48.tasks.webapi.tenant.adapter.persistence.TenantJpaEntity;
import xyz.dgz48.tasks.webapi.user.adapter.persistence.UserJpaEntity;

/**
 * B-01 期限当日通知の対象抽出({@link DueTodayNotificationQueryPort})の統合テスト。
 *
 * <p>抽出ルール(所有者・担当者のみ / DONE・未来日除外 / 設定 OFF 除外 / 関係者除外 / INACTIVE 除外)と、テナント横断バッチでの テナント単位グループ化を検証する。
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, MockJwtDecoderConfiguration.class})
class DueTodayNotificationQueryIT {

  private static final LocalDate TODAY = LocalDate.of(2026, 1, 15);

  @Autowired DueTodayNotificationQueryPort queryPort;
  @Autowired EntityManager em;
  @Autowired TransactionTemplate txTemplate;

  private Long tenant1Id;
  private Long tenant2Id;
  private Long userAId; // T1, default ON
  private Long userBId; // T1, email_due_today OFF
  private Long userCId; // T2, default ON
  private Long userDId; // T1, INACTIVE
  private Long userEId; // T1, stakeholder only

  private Long taskA1;
  private Long taskA2;
  private Long taskAsg; // owner B, assignee A
  private Long taskStake; // owner A, STAKEHOLDERS, stakeholder E
  private Long taskDoneA;
  private Long taskFutureA;

  @BeforeEach
  void setUp() {
    txTemplate.execute(
        ignored -> {
          var userA = new UserJpaEntity("sub-n-a", "n-a@example.com", "通知A", "ツウチエー", null);
          var userB = new UserJpaEntity("sub-n-b", "n-b@example.com", "通知B", "ツウチビー", null);
          var userC = new UserJpaEntity("sub-n-c", "n-c@example.com", "通知C", "ツウチシー", null);
          var userD = new UserJpaEntity("sub-n-d", "n-d@example.com", "通知D", "ツウチディー", null);
          var userE = new UserJpaEntity("sub-n-e", "n-e@example.com", "通知E", "ツウチイー", null);
          em.persist(userA);
          em.persist(userB);
          em.persist(userC);
          em.persist(userD);
          em.persist(userE);
          em.flush();
          userAId = userA.getId();
          userBId = userB.getId();
          userCId = userC.getId();
          userDId = userD.getId();
          userEId = userE.getId();

          SecurityContextHolder.getContext()
              .setAuthentication(
                  new TasksAuthenticationToken(
                      new TasksPrincipal(
                          userAId, "sub-n-a", "n-a@example.com", "通知A", "ツウチエー", null),
                      List.of()));

          var t1 = new TenantJpaEntity("N-1", "通知テナント1");
          var t2 = new TenantJpaEntity("N-2", "通知テナント2");
          em.persist(t1);
          em.persist(t2);
          em.flush();
          tenant1Id = t1.getId();
          tenant2Id = t2.getId();

          // userD を INACTIVE 化
          em.createNativeQuery("UPDATE users SET status = 'INACTIVE' WHERE id = ?")
              .setParameter(1, userDId)
              .executeUpdate();

          // userB は email_due_today = FALSE(OFF)
          em.createNativeQuery(
                  "INSERT INTO user_notification_settings"
                      + " (user_id, tenant_id, email_due_today, email_overdue, email_stakeholder, updated_at)"
                      + " VALUES (?,?,?,?,?,?)")
              .setParameter(1, userBId)
              .setParameter(2, tenant1Id)
              .setParameter(3, false)
              .setParameter(4, true)
              .setParameter(5, true)
              .setParameter(6, LocalDateTime.of(2026, 1, 1, 0, 0))
              .executeUpdate();

          taskA1 =
              persist(
                  tenant1Id, userAId, null, "A1", TaskStatus.NOT_STARTED, Visibility.TENANT, TODAY);
          taskA2 =
              persist(
                  tenant1Id, userAId, null, "A2", TaskStatus.IN_PROGRESS, Visibility.TENANT, TODAY);
          taskAsg =
              persist(
                  tenant1Id,
                  userBId,
                  userAId,
                  "Asg",
                  TaskStatus.NOT_STARTED,
                  Visibility.TENANT,
                  TODAY);
          taskStake =
              persist(
                  tenant1Id,
                  userAId,
                  null,
                  "Stake",
                  TaskStatus.NOT_STARTED,
                  Visibility.STAKEHOLDERS,
                  TODAY);
          taskDoneA =
              persist(tenant1Id, userAId, null, "DoneA", TaskStatus.DONE, Visibility.TENANT, TODAY);
          taskFutureA =
              persist(
                  tenant1Id,
                  userAId,
                  null,
                  "FutureA",
                  TaskStatus.NOT_STARTED,
                  Visibility.TENANT,
                  TODAY.plusDays(1));
          // B1: 所有者 B は email_due_today=OFF のため通知対象外(設定 OFF 除外の検証用)。
          persist(tenant1Id, userBId, null, "B1", TaskStatus.NOT_STARTED, Visibility.TENANT, TODAY);
          // C1: 別テナント(tenant2)の C。テナント単位グループ化の検証用。
          persist(tenant2Id, userCId, null, "C1", TaskStatus.NOT_STARTED, Visibility.TENANT, TODAY);
          // D1: 所有者 D は INACTIVE のため通知対象外(INACTIVE 除外の検証用)。
          persist(tenant1Id, userDId, null, "D1", TaskStatus.NOT_STARTED, Visibility.TENANT, TODAY);

          // userE を taskStake の関係者として登録(所有者・担当者ではない)
          em.createNativeQuery(
                  "INSERT INTO task_stakeholders (task_id, user_id, tenant_id, added_by, added_at)"
                      + " VALUES (?,?,?,?,?)")
              .setParameter(1, taskStake)
              .setParameter(2, userEId)
              .setParameter(3, tenant1Id)
              .setParameter(4, userAId)
              .setParameter(5, LocalDateTime.of(2026, 1, 1, 0, 0))
              .executeUpdate();

          return null;
        });
    SecurityContextHolder.clearContext();
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
    if (tenant1Id == null) {
      return;
    }
    txTemplate.execute(
        ignored -> {
          em.createNativeQuery("DELETE FROM task_stakeholders WHERE tenant_id IN (?,?)")
              .setParameter(1, tenant1Id)
              .setParameter(2, tenant2Id)
              .executeUpdate();
          em.createNativeQuery("DELETE FROM tasks WHERE tenant_id IN (?,?)")
              .setParameter(1, tenant1Id)
              .setParameter(2, tenant2Id)
              .executeUpdate();
          em.createNativeQuery("DELETE FROM user_notification_settings WHERE tenant_id IN (?,?)")
              .setParameter(1, tenant1Id)
              .setParameter(2, tenant2Id)
              .executeUpdate();
          em.createNativeQuery("DELETE FROM tenants WHERE id IN (?,?)")
              .setParameter(1, tenant1Id)
              .setParameter(2, tenant2Id)
              .executeUpdate();
          em.createNativeQuery("DELETE FROM users WHERE id IN (?,?,?,?,?)")
              .setParameter(1, userAId)
              .setParameter(2, userBId)
              .setParameter(3, userCId)
              .setParameter(4, userDId)
              .setParameter(5, userEId)
              .executeUpdate();
          return null;
        });
  }

  @Test
  void extractsOwnerAndAssignee_excludesOff_done_future_stakeholder_inactive_andSeparatesTenants() {
    List<DueTodayNotification> result = queryPort.findDueTodayRecipients(TODAY);

    // 通知される受信者は A(T1)と C(T2)のみ
    assertThat(result).hasSize(2);

    DueTodayNotification a = findRecipient(result, userAId);
    assertThat(a.tenantId()).isEqualTo(tenant1Id);
    assertThat(a.email()).isEqualTo("n-a@example.com");
    List<Long> aTaskIds = a.tasks().stream().map(DueTodayNotification.DueTask::taskId).toList();
    // 所有(A1/A2/Stake)+ 担当(Asg)= 4 件。DONE / 未来日は除外。
    assertThat(aTaskIds)
        .containsExactlyInAnyOrder(taskA1, taskA2, taskStake, taskAsg)
        .doesNotContain(taskDoneA, taskFutureA);

    DueTodayNotification c = findRecipient(result, userCId);
    assertThat(c.tenantId()).isEqualTo(tenant2Id);
    assertThat(c.tasks()).hasSize(1);

    // B(設定 OFF)・D(INACTIVE)・E(関係者のみ)は通知対象外
    assertThat(result.stream().map(DueTodayNotification::userId))
        .doesNotContain(userBId, userDId, userEId);
  }

  private static DueTodayNotification findRecipient(
      List<DueTodayNotification> result, Long userId) {
    Optional<DueTodayNotification> found =
        result.stream().filter(n -> n.userId().equals(userId)).findFirst();
    assertThat(found).as("受信者 userId=%s が存在する", userId).isPresent();
    return found.orElseThrow();
  }

  private Long persist(
      Long tenantId,
      Long ownerId,
      @org.jspecify.annotations.Nullable Long assigneeId,
      String title,
      TaskStatus status,
      Visibility visibility,
      LocalDate dueDate) {
    var task =
        new TaskJpaEntity(
            tenantId,
            ownerId,
            title,
            null,
            status,
            Priority.MEDIUM,
            visibility,
            assigneeId,
            dueDate);
    em.persist(task);
    em.flush();
    return task.getId();
  }
}
