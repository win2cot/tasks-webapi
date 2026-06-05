package xyz.dgz48.tasks.webapi.shared.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import xyz.dgz48.tasks.webapi.MockJwtDecoderConfiguration;
import xyz.dgz48.tasks.webapi.TestcontainersConfiguration;
import xyz.dgz48.tasks.webapi.security.adapter.web.TasksAuthenticationToken;
import xyz.dgz48.tasks.webapi.security.domain.TasksPrincipal;
import xyz.dgz48.tasks.webapi.shared.domain.TenantContext;
import xyz.dgz48.tasks.webapi.shared.exception.SaasAdminRequiredException;
import xyz.dgz48.tasks.webapi.task.adapter.persistence.TaskJpaEntity;
import xyz.dgz48.tasks.webapi.task.domain.Priority;
import xyz.dgz48.tasks.webapi.task.domain.TaskStatus;
import xyz.dgz48.tasks.webapi.task.usecase.TaskRepository;
import xyz.dgz48.tasks.webapi.tenant.adapter.persistence.TenantJpaEntity;
import xyz.dgz48.tasks.webapi.user.adapter.persistence.UserJpaEntity;

/** {@link TenantFilterBypassService} の統合テスト。Testcontainers MySQL で実際の Hibernate Filter 動作を検証する。 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, MockJwtDecoderConfiguration.class})
@Transactional
class TenantFilterBypassServiceIT {

  @Autowired TenantFilterBypassService bypassService;
  @Autowired EntityManager entityManager;
  @Autowired TaskRepository taskRepository;

  @BeforeEach
  void setUpAuditor() {
    var auditor =
        new UserJpaEntity("sub-bps-auditor", "bps-auditor@example.com", "バイパス監査", "バイパスカンサ", null);
    entityManager.persist(auditor);
    entityManager.flush();
    var principal =
        new TasksPrincipal(
            auditor.getId(),
            "sub-bps-auditor",
            "bps-auditor@example.com",
            "バイパス監査",
            "バイパスカンサ",
            null);
    SecurityContextHolder.getContext()
        .setAuthentication(new TasksAuthenticationToken(principal, List.of()));
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
    TenantContext.clear();
    entityManager.unwrap(Session.class).disableFilter("tenantFilter");
  }

  @Test
  void runAsSaaSAdmin_disablesFilterAndAllowsCrossTenantAccess() {
    var tenantA = new TenantJpaEntity("BPS-IT-A", "バイパスITテナントA");
    var tenantB = new TenantJpaEntity("BPS-IT-B", "バイパスITテナントB");
    entityManager.persist(tenantA);
    entityManager.persist(tenantB);

    var user = new UserJpaEntity("sub-bps-it-1", "bps-it-1@example.com", "バイパス太郎", "バイパスタロウ", null);
    entityManager.persist(user);
    entityManager.flush();

    var taskA =
        new TaskJpaEntity(
            tenantA.getId(),
            user.getId(),
            "テナントAタスク",
            null,
            TaskStatus.NOT_STARTED,
            Priority.MEDIUM,
            LocalDate.of(2026, 12, 31));
    var taskB =
        new TaskJpaEntity(
            tenantB.getId(),
            user.getId(),
            "テナントBタスク",
            null,
            TaskStatus.NOT_STARTED,
            Priority.MEDIUM,
            LocalDate.of(2026, 12, 31));
    entityManager.persist(taskA);
    entityManager.persist(taskB);
    entityManager.flush();
    entityManager.clear();

    // テナントA のフィルタを有効化(通常リクエストと同じ状態)
    entityManager
        .unwrap(Session.class)
        .enableFilter("tenantFilter")
        .setParameter("tenantId", tenantA.getId());

    // フィルタ有効時: テナントBのタスクは参照不可
    assertThat(taskRepository.findById(taskB.getId())).isEmpty();
    // テナントAのタスクは参照可
    assertThat(taskRepository.findById(taskA.getId())).isPresent();

    // SaaS Admin bypass: テナントBのタスクが横断参照可
    setUpSaasAdmin();
    TenantContext.set(tenantA.getId());
    var bypassResult = bypassService.runAsSaaSAdmin(() -> taskRepository.findById(taskB.getId()));
    assertThat(bypassResult).isPresent();

    // bypass 後: フィルタがテナントA に復元されているためテナントBタスクは再び不可
    assertThat(taskRepository.findById(taskB.getId())).isEmpty();
  }

  @Test
  void runAsSaaSAdmin_throwsWhenNotSaasAdmin() {
    setUpMember();

    assertThatThrownBy(() -> bypassService.runAsSaaSAdmin(() -> taskRepository.findById(999L)))
        .isInstanceOf(SaasAdminRequiredException.class);
  }

  @Test
  void runAsSaaSAdmin_filterRestoredEvenWhenActionThrows() {
    var tenant = new TenantJpaEntity("BPS-IT-C", "バイパスITテナントC");
    entityManager.persist(tenant);

    var user = new UserJpaEntity("sub-bps-it-c", "bps-it-c@example.com", "復元確認", "フクゲンカクニン", null);
    entityManager.persist(user);
    entityManager.flush();

    var task =
        new TaskJpaEntity(
            tenant.getId(),
            user.getId(),
            "復元確認タスク",
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

    setUpSaasAdmin();
    TenantContext.set(tenant.getId());

    assertThatThrownBy(
            () ->
                bypassService.runAsSaaSAdmin(
                    () -> {
                      throw new RuntimeException("deliberate failure");
                    }))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("deliberate failure");

    // フィルタが try-finally で復元されていれば、テナントスコープのタスクが参照可
    assertThat(taskRepository.findById(task.getId())).isPresent();
  }

  private void setUpSaasAdmin() {
    var principal =
        new TasksPrincipal(
            100L, "admin-sub-it", "admin-it@example.com", "IT管理者", "アイティーカンリシャ", null);
    var auth =
        new TasksAuthenticationToken(
            principal, List.of(new SimpleGrantedAuthority("ROLE_APP_ADMIN")));
    SecurityContextHolder.getContext().setAuthentication(auth);
  }

  private void setUpMember() {
    var principal =
        new TasksPrincipal(
            101L, "member-sub-it", "member-it@example.com", "ITメンバー", "アイティーメンバー", null);
    var auth =
        new TasksAuthenticationToken(principal, List.of(new SimpleGrantedAuthority("ROLE_MEMBER")));
    SecurityContextHolder.getContext().setAuthentication(auth);
  }
}
