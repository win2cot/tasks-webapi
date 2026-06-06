package xyz.dgz48.tasks.webapi.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionTemplate;
import xyz.dgz48.tasks.webapi.MockJwtDecoderConfiguration;
import xyz.dgz48.tasks.webapi.TestcontainersConfiguration;
import xyz.dgz48.tasks.webapi.security.adapter.web.TasksAuthenticationToken;
import xyz.dgz48.tasks.webapi.security.domain.TasksPrincipal;
import xyz.dgz48.tasks.webapi.shared.domain.TenantContext;
import xyz.dgz48.tasks.webapi.shared.usecase.TenantFilterBypassService;
import xyz.dgz48.tasks.webapi.task.usecase.TaskRepository;

/**
 * クロステナント違反検知ログの統合テスト。
 *
 * <p>TenantContext 未設定で tenant-filtered テーブルへの SQL が発行された場合に {@code audit_logs} へ {@code
 * CROSS_TENANT_VIOLATION_ATTEMPT} が記録されること、および D-1 の正当 bypass で false positive が 発生しないことを検証する。
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, MockJwtDecoderConfiguration.class})
class CrossTenantViolationDetectionIT {

  @Autowired TaskRepository taskRepository;
  @Autowired TenantFilterBypassService bypassService;
  @Autowired JdbcTemplate jdbcTemplate;
  @Autowired TransactionTemplate txTemplate;

  @BeforeEach
  void setUp() {
    TenantContext.clear();
    SecurityContextHolder.clearContext();
    jdbcTemplate.execute("DELETE FROM audit_logs");
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
    SecurityContextHolder.clearContext();
    jdbcTemplate.execute("DELETE FROM audit_logs");
  }

  @Test
  void whenTenantContextNull_andQueryTasksTable_thenViolationRecorded() {
    // TenantContext 未設定のまま tasks テーブルを参照 → 違反として検知されるべき
    taskRepository.findById(Long.MAX_VALUE);

    List<Map<String, Object>> rows =
        jdbcTemplate.queryForList(
            "SELECT * FROM audit_logs WHERE action = 'CROSS_TENANT_VIOLATION_ATTEMPT'");
    assertThat(rows).isNotEmpty();
    assertThat(rows.get(0)).containsEntry("action", "CROSS_TENANT_VIOLATION_ATTEMPT");
  }

  @Test
  void whenTenantContextSet_thenNoViolation() {
    // TenantContext が設定済みの場合は Filter が有効化されるため違反ではない
    TenantContext.set(999L);
    try {
      txTemplate.execute(
          status -> {
            taskRepository.findById(Long.MAX_VALUE);
            return null;
          });
    } finally {
      TenantContext.clear();
    }

    List<Map<String, Object>> rows =
        jdbcTemplate.queryForList(
            "SELECT * FROM audit_logs WHERE action = 'CROSS_TENANT_VIOLATION_ATTEMPT'");
    assertThat(rows).isEmpty();
  }

  @Test
  void whenSaasAdminBypass_andTenantContextNull_thenNoFalsePositive() {
    // D-1: SaaS Admin が TenantFilterBypassService 経由で filter を解除しても false positive にならない
    setUpSaasAdmin();

    txTemplate.execute(
        status -> {
          bypassService.runAsSaaSAdmin(
              () -> {
                taskRepository.findById(Long.MAX_VALUE);
                return null;
              });
          return null;
        });

    List<Map<String, Object>> rows =
        jdbcTemplate.queryForList(
            "SELECT * FROM audit_logs WHERE action = 'CROSS_TENANT_VIOLATION_ATTEMPT'");
    assertThat(rows).isEmpty();
  }

  @Test
  void whenTenantContextNull_andQueryUsersTable_thenNoViolation() {
    // users テーブルは TenantFilteredEntity 対象外のため違反検知しない
    // (TenantAwareJpaTransactionManager + TenantContextFilter が users を参照する正当ケース)
    jdbcTemplate.queryForList("SELECT id FROM users LIMIT 1");

    List<Map<String, Object>> rows =
        jdbcTemplate.queryForList(
            "SELECT * FROM audit_logs WHERE action = 'CROSS_TENANT_VIOLATION_ATTEMPT'");
    assertThat(rows).isEmpty();
  }

  private void setUpSaasAdmin() {
    var principal =
        new TasksPrincipal(1L, "admin-sub-vd", "admin-vd@example.com", "VD管理者", "ブイディーカンリシャ", null);
    var auth =
        new TasksAuthenticationToken(
            principal, List.of(new SimpleGrantedAuthority("ROLE_APP_ADMIN")));
    SecurityContextHolder.getContext().setAuthentication(auth);
  }
}
