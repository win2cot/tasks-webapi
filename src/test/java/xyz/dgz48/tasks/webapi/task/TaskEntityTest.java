package xyz.dgz48.tasks.webapi.task;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;
import xyz.dgz48.tasks.webapi.TestcontainersConfiguration;
import xyz.dgz48.tasks.webapi.user.User;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
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
    var user = new User("sub-003", "owner@example.com", "田中 一郎", "タナカ イチロウ", null);
    entityManager.persist(user);
    entityManager.flush();

    var task = new Task(TaskStatus.INCOMPLETE, user.getId(), "タスク1", "タスクの詳細");
    entityManager.persist(task);
    entityManager.flush();

    assertThat(task.getId()).isNotNull();
    assertThat(task.getStatus()).isEqualTo(TaskStatus.INCOMPLETE);
    assertThat(task.getOwnerId()).isEqualTo(user.getId());
    assertThat(task.getTitle()).isEqualTo("タスク1");
    assertThat(task.getBody()).isEqualTo("タスクの詳細");
  }

  @Test
  void canPersistTaskWithoutBody() {
    var user = new User("sub-004", "owner2@example.com", "佐藤 次郎", "サトウ ジロウ", null);
    entityManager.persist(user);
    entityManager.flush();

    var task = new Task(TaskStatus.COMPLETE, user.getId(), "完了タスク", null);
    entityManager.persist(task);
    entityManager.flush();

    assertThat(task.getId()).isNotNull();
    assertThat(task.getStatus()).isEqualTo(TaskStatus.COMPLETE);
    assertThat(task.getBody()).isNull();
  }
}
