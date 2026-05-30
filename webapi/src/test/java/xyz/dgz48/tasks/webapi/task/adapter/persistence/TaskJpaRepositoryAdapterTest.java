package xyz.dgz48.tasks.webapi.task.adapter.persistence;

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
import xyz.dgz48.tasks.webapi.task.domain.Priority;
import xyz.dgz48.tasks.webapi.task.domain.Task;
import xyz.dgz48.tasks.webapi.task.domain.TaskStatus;
import xyz.dgz48.tasks.webapi.task.usecase.TaskRepository;
import xyz.dgz48.tasks.webapi.tenant.adapter.persistence.Tenant;
import xyz.dgz48.tasks.webapi.user.adapter.persistence.User;

@SpringBootTest
@Import({TestcontainersConfiguration.class, MockJwtDecoderConfiguration.class})
@Transactional
class TaskJpaRepositoryAdapterTest {

  @Autowired EntityManager entityManager;
  @Autowired TaskRepository taskRepository;

  @Test
  void findByIdAndTenantIdReturnsDomainTaskWhenFound() {
    var tenant = new Tenant("TENANT-ADP-1", "テナント A");
    entityManager.persist(tenant);
    var user = new User("sub-adp-1", "adp1@example.com", "山田 太郎", "ヤマダ タロウ", null);
    entityManager.persist(user);
    entityManager.flush();

    var jpa =
        new TaskJpaEntity(
            tenant.getId(),
            user.getId(),
            "アダプタテスト",
            "詳細",
            TaskStatus.NOT_STARTED,
            Priority.MEDIUM,
            LocalDate.of(2026, 12, 31));
    entityManager.persist(jpa);
    entityManager.flush();
    entityManager.clear();

    var found = taskRepository.findByIdAndTenantId(jpa.getId(), tenant.getId());

    assertThat(found).isPresent();
    Task task = found.get();
    assertThat(task.getId()).isEqualTo(jpa.getId());
    assertThat(task.getTenantId()).isEqualTo(tenant.getId());
    assertThat(task.getTitle()).isEqualTo("アダプタテスト");
    assertThat(task.getDescription()).isEqualTo("詳細");
    assertThat(task.getStatus()).isEqualTo(TaskStatus.NOT_STARTED);
    assertThat(task.getPriority()).isEqualTo(Priority.MEDIUM);
    assertThat(task.getOwnerId()).isEqualTo(user.getId());
  }

  @Test
  void findByIdAndTenantIdReturnsEmptyWhenNotFound() {
    var found = taskRepository.findByIdAndTenantId(999_999L, 999_999L);
    assertThat(found).isEmpty();
  }

  @Test
  void findByIdAndTenantIdReturnsEmptyWhenTenantMismatch() {
    var tenant = new Tenant("TENANT-ADP-2", "テナント B");
    entityManager.persist(tenant);
    var user = new User("sub-adp-2", "adp2@example.com", "鈴木 花子", "スズキ ハナコ", null);
    entityManager.persist(user);
    entityManager.flush();

    var jpa =
        new TaskJpaEntity(
            tenant.getId(),
            user.getId(),
            "別テナント",
            null,
            TaskStatus.NOT_STARTED,
            Priority.LOW,
            LocalDate.of(2026, 12, 31));
    entityManager.persist(jpa);
    entityManager.flush();

    // 別 tenant id で探すと空
    var found = taskRepository.findByIdAndTenantId(jpa.getId(), tenant.getId() + 99_999L);
    assertThat(found).isEmpty();
  }
}
