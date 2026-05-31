package xyz.dgz48.tasks.webapi.task.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import org.hibernate.Session;
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
import xyz.dgz48.tasks.webapi.task.domain.TaskStatus;
import xyz.dgz48.tasks.webapi.task.usecase.TaskRepository;
import xyz.dgz48.tasks.webapi.tenant.adapter.persistence.TenantJpaEntity;
import xyz.dgz48.tasks.webapi.user.adapter.persistence.UserJpaEntity;

/**
 * Hibernate Filter "tenantFilter" のクロステナント漏洩防止を検証する IT。
 *
 * <p>設計規約 §9.4 の受け入れ条件: 参照系でフィルタ有効時に別テナントのタスクが見えないこと。
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, MockJwtDecoderConfiguration.class})
@Transactional
class TenantFilterIsolationTest {

  @Autowired EntityManager entityManager;
  @Autowired TaskRepository taskRepository;

  @BeforeEach
  void setUpAuditor() {
    var auditor =
        new UserJpaEntity(
            "sub-iso-auditor", "iso-auditor@example.com", "分離テスト監査", "ブンリテストカンサ", null);
    entityManager.persist(auditor);
    entityManager.flush();

    var principal =
        new TasksPrincipal(
            auditor.getId(),
            "sub-iso-auditor",
            "iso-auditor@example.com",
            "分離テスト監査",
            "ブンリテストカンサ",
            null);
    SecurityContextHolder.getContext()
        .setAuthentication(new TasksAuthenticationToken(principal, List.of()));
  }

  @AfterEach
  void clearContexts() {
    SecurityContextHolder.clearContext();
    entityManager.unwrap(Session.class).disableFilter("tenantFilter");
  }

  @Test
  void filterBlocksCrossTenantTaskAccess() {
    var tenantA = new TenantJpaEntity("ISO-TF-A", "テナントA");
    var tenantB = new TenantJpaEntity("ISO-TF-B", "テナントB");
    entityManager.persist(tenantA);
    entityManager.persist(tenantB);

    var user = new UserJpaEntity("sub-iso-1", "iso1@example.com", "田中 太郎", "タナカ タロウ", null);
    entityManager.persist(user);
    entityManager.flush();

    var taskA =
        new TaskJpaEntity(
            tenantA.getId(),
            user.getId(),
            "テナントAのタスク",
            null,
            TaskStatus.NOT_STARTED,
            Priority.MEDIUM,
            LocalDate.of(2026, 12, 31));
    var taskB =
        new TaskJpaEntity(
            tenantB.getId(),
            user.getId(),
            "テナントBのタスク",
            null,
            TaskStatus.NOT_STARTED,
            Priority.MEDIUM,
            LocalDate.of(2026, 12, 31));
    entityManager.persist(taskA);
    entityManager.persist(taskB);
    entityManager.flush();
    entityManager.clear();

    // Enable filter for tenantA
    entityManager
        .unwrap(Session.class)
        .enableFilter("tenantFilter")
        .setParameter("tenantId", tenantA.getId());

    // tenantB's task must be blocked (参照系 404 相当: empty result)
    assertThat(taskRepository.findById(taskB.getId())).isEmpty();

    // tenantA's task must be visible
    assertThat(taskRepository.findById(taskA.getId())).isPresent();
  }

  @Test
  void filterAllowsAccessWithinSameTenant() {
    var tenant = new TenantJpaEntity("ISO-TF-C", "テナントC");
    entityManager.persist(tenant);

    var user = new UserJpaEntity("sub-iso-2", "iso2@example.com", "鈴木 花子", "スズキ ハナコ", null);
    entityManager.persist(user);
    entityManager.flush();

    var task =
        new TaskJpaEntity(
            tenant.getId(),
            user.getId(),
            "テナントCのタスク",
            null,
            TaskStatus.NOT_STARTED,
            Priority.LOW,
            LocalDate.of(2026, 12, 31));
    entityManager.persist(task);
    entityManager.flush();
    entityManager.clear();

    entityManager
        .unwrap(Session.class)
        .enableFilter("tenantFilter")
        .setParameter("tenantId", tenant.getId());

    assertThat(taskRepository.findById(task.getId())).isPresent();
  }
}
