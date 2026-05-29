package xyz.dgz48.tasks.webapi.task;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;
import xyz.dgz48.tasks.webapi.MockJwtDecoderConfiguration;
import xyz.dgz48.tasks.webapi.TestcontainersConfiguration;
import xyz.dgz48.tasks.webapi.task.adapter.persistence.Task;
import xyz.dgz48.tasks.webapi.task.domain.Priority;
import xyz.dgz48.tasks.webapi.task.domain.TaskStatus;
import xyz.dgz48.tasks.webapi.tenant.adapter.persistence.Tenant;
import xyz.dgz48.tasks.webapi.user.adapter.persistence.User;

@SpringBootTest
@Import({TestcontainersConfiguration.class, MockJwtDecoderConfiguration.class})
@Transactional
class TaskEntityTest {

  @Autowired EntityManager entityManager;

  @Test
  void canPersistUser() {
    var user = new User("sub-001", "user@example.com", "山田 太郎", "ヤマダ タロウ", "開発部");
    entityManager.persist(user);
    entityManager.flush();

    assertThat(user.getId()).isNotNull();
    assertThat(user.getEmail()).isEqualTo("user@example.com");
    assertThat(user.getDepartmentName()).isEqualTo("開発部");
  }

  @Test
  void canPersistUserWithoutDepartment() {
    var user = new User("sub-002", "nodept@example.com", "鈴木 花子", "スズキ ハナコ", null);
    entityManager.persist(user);
    entityManager.flush();

    assertThat(user.getId()).isNotNull();
    assertThat(user.getDepartmentName()).isNull();
  }

  @Test
  void canPersistTask() {
    var tenant = new Tenant("TENANT-001", "テスト株式会社");
    entityManager.persist(tenant);
    entityManager.flush();

    var user = new User("sub-003", "owner@example.com", "田中 一郎", "タナカ イチロウ", null);
    entityManager.persist(user);
    entityManager.flush();

    var task =
        new Task(
            tenant.getId(),
            user.getId(),
            "タスク1",
            "タスクの詳細",
            TaskStatus.NOT_STARTED,
            Priority.MEDIUM,
            LocalDate.of(2026, 12, 31));
    entityManager.persist(task);
    entityManager.flush();

    assertThat(task.getId()).isNotNull();
    assertThat(task.getStatus()).isEqualTo(TaskStatus.NOT_STARTED);
    assertThat(task.getOwnerId()).isEqualTo(user.getId());
    assertThat(task.getTitle()).isEqualTo("タスク1");
    assertThat(task.getDescription()).isEqualTo("タスクの詳細");
  }

  @Test
  void canPersistTaskWithoutDescription() {
    var tenant = new Tenant("TENANT-002", "別テスト株式会社");
    entityManager.persist(tenant);
    entityManager.flush();

    var user = new User("sub-004", "owner2@example.com", "佐藤 次郎", "サトウ ジロウ", null);
    entityManager.persist(user);
    entityManager.flush();

    var task =
        new Task(
            tenant.getId(),
            user.getId(),
            "完了タスク",
            null,
            TaskStatus.DONE,
            Priority.HIGH,
            LocalDate.of(2026, 12, 31));
    entityManager.persist(task);
    entityManager.flush();

    assertThat(task.getId()).isNotNull();
    assertThat(task.getStatus()).isEqualTo(TaskStatus.DONE);
    assertThat(task.getDescription()).isNull();
  }

  @Test
  void canPersistTaskWithInProgressStatus() {
    var tenant = new Tenant("TENANT-003", "進行中テスト株式会社");
    entityManager.persist(tenant);
    entityManager.flush();

    var user = new User("sub-005", "owner3@example.com", "伊藤 三郎", "イトウ サブロウ", null);
    entityManager.persist(user);
    entityManager.flush();

    var task =
        new Task(
            tenant.getId(),
            user.getId(),
            "進行中タスク",
            null,
            TaskStatus.IN_PROGRESS,
            Priority.MEDIUM,
            LocalDate.of(2026, 12, 31));
    entityManager.persist(task);
    entityManager.flush();

    assertThat(task.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
  }

  @Test
  void canPersistTaskWithOnHoldStatus() {
    var tenant = new Tenant("TENANT-004", "保留テスト株式会社");
    entityManager.persist(tenant);
    entityManager.flush();

    var user = new User("sub-006", "owner4@example.com", "渡辺 四郎", "ワタナベ シロウ", null);
    entityManager.persist(user);
    entityManager.flush();

    var task =
        new Task(
            tenant.getId(),
            user.getId(),
            "保留タスク",
            null,
            TaskStatus.ON_HOLD,
            Priority.LOW,
            LocalDate.of(2026, 12, 31));
    entityManager.persist(task);
    entityManager.flush();

    assertThat(task.getStatus()).isEqualTo(TaskStatus.ON_HOLD);
  }
}
