package xyz.dgz48.tasks.webapi.task.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import xyz.dgz48.tasks.webapi.task.domain.Task;
import xyz.dgz48.tasks.webapi.task.domain.TaskStatus;
import xyz.dgz48.tasks.webapi.task.usecase.TaskRepository;
import xyz.dgz48.tasks.webapi.tenant.adapter.persistence.TenantJpaEntity;
import xyz.dgz48.tasks.webapi.user.adapter.persistence.UserJpaEntity;

@SpringBootTest
@Import({TestcontainersConfiguration.class, MockJwtDecoderConfiguration.class})
@Transactional
class TaskJpaRepositoryAdapterTest {

  @Autowired EntityManager entityManager;
  @Autowired TaskRepository taskRepository;

  @BeforeEach
  void setUpAuditor() {
    var auditor = new UserJpaEntity("sub-auditor", "auditor@example.com", "監査 太郎", "カンサ タロウ", null);
    entityManager.persist(auditor);
    entityManager.flush();

    var principal =
        new TasksPrincipal(
            auditor.getId(), "sub-auditor", "auditor@example.com", "監査 太郎", "カンサ タロウ", null);
    SecurityContextHolder.getContext()
        .setAuthentication(new TasksAuthenticationToken(principal, List.of()));
  }

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void findByIdReturnsDomainTaskWhenFound() {
    var tenant = new TenantJpaEntity("TENANT-ADP-1", "テナント A");
    entityManager.persist(tenant);
    var user = new UserJpaEntity("sub-adp-1", "adp1@example.com", "山田 太郎", "ヤマダ タロウ", null);
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

    var found = taskRepository.findById(jpa.getId());

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
  void findByIdReturnsEmptyWhenNotFound() {
    var found = taskRepository.findById(999_999L);
    assertThat(found).isEmpty();
  }

  @Test
  void save_updatesStatusAndCompletedAt() {
    var tenant = new TenantJpaEntity("TENANT-ADP-2", "テナント B");
    entityManager.persist(tenant);
    var user = new UserJpaEntity("sub-adp-2", "adp2@example.com", "鈴木 次郎", "スズキ ジロウ", null);
    entityManager.persist(user);
    entityManager.flush();

    var jpa =
        new TaskJpaEntity(
            tenant.getId(),
            user.getId(),
            "ステータス変更テスト",
            null,
            TaskStatus.IN_PROGRESS,
            Priority.HIGH,
            LocalDate.of(2026, 12, 31));
    entityManager.persist(jpa);
    entityManager.flush();
    entityManager.clear();

    Task loaded = taskRepository.findById(jpa.getId()).orElseThrow();
    LocalDateTime completedAt = LocalDateTime.of(2026, 6, 1, 10, 0);
    loaded.changeStatus(TaskStatus.DONE, completedAt);

    Task saved = taskRepository.save(loaded);

    assertThat(saved.getStatus()).isEqualTo(TaskStatus.DONE);
    assertThat(saved.getCompletedAt()).isEqualTo(completedAt);
  }

  @Test
  void save_clearsCompletedAt_whenReopened() {
    var tenant = new TenantJpaEntity("TENANT-ADP-3", "テナント C");
    entityManager.persist(tenant);
    var user = new UserJpaEntity("sub-adp-3", "adp3@example.com", "田中 三郎", "タナカ サブロウ", null);
    entityManager.persist(user);
    entityManager.flush();

    LocalDateTime firstCompletedAt = LocalDateTime.of(2026, 6, 1, 10, 0);
    var jpa =
        new TaskJpaEntity(
            tenant.getId(),
            user.getId(),
            "再オープンテスト",
            null,
            TaskStatus.DONE,
            Priority.MEDIUM,
            LocalDate.of(2026, 12, 31));
    jpa.updateStatus(TaskStatus.DONE, firstCompletedAt);
    entityManager.persist(jpa);
    entityManager.flush();
    entityManager.clear();

    Task loaded = taskRepository.findById(jpa.getId()).orElseThrow();
    loaded.changeStatus(TaskStatus.IN_PROGRESS, firstCompletedAt.plusHours(1));

    Task saved = taskRepository.save(loaded);

    assertThat(saved.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
    assertThat(saved.getCompletedAt()).isNull();
  }
}
