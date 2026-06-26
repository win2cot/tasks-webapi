package xyz.dgz48.tasks.webapi.audit.adapter.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import xyz.dgz48.tasks.webapi.MockJwtDecoderConfiguration;
import xyz.dgz48.tasks.webapi.TestcontainersConfiguration;
import xyz.dgz48.tasks.webapi.security.adapter.web.TasksAuthenticationToken;
import xyz.dgz48.tasks.webapi.security.domain.TasksPrincipal;
import xyz.dgz48.tasks.webapi.tenant.adapter.persistence.TenantJpaEntity;
import xyz.dgz48.tasks.webapi.user.adapter.persistence.UserJpaEntity;

/**
 * 監査ログ参照 API(A-22、GET /api/audit-logs)の統合テスト。
 *
 * <p>自テナントスコープ(ADR-0020 §3.4: 他テナント行・横断 {@code tenant_id=NULL} 行を返さない)、created_at 降順、
 * from/to/action 絞り込み、認可(Tenant Admin のみ・Member / SaaS Admin は 403)を検証する。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, MockJwtDecoderConfiguration.class})
class AuditLogIT {

  private static final String DUMMY_HASH = "a".repeat(64);

  @Autowired MockMvc mockMvc;
  @Autowired EntityManager em;
  @Autowired TransactionTemplate txTemplate;

  private Long tenantAId;
  private Long tenantBId;
  private Long adminUserId;
  private Long memberUserId;
  private Long saasAdminUserId;

  private TasksAuthenticationToken adminToken;
  private TasksAuthenticationToken memberToken;
  private TasksAuthenticationToken saasAdminToken;

  @BeforeEach
  void setUp() {
    txTemplate.execute(
        ignored -> {
          var admin = new UserJpaEntity("sub-ala", "ala@example.com", "監査管理者", "カンサカンリ", null);
          var member = new UserJpaEntity("sub-alm", "alm@example.com", "一般", "イッパン", null);
          var saasAdmin = new UserJpaEntity("sub-als", "als@example.com", "SaaS管理者", "サース", null);
          em.persist(admin);
          em.persist(member);
          em.persist(saasAdmin);
          em.flush();
          adminUserId = admin.getId();
          memberUserId = member.getId();
          saasAdminUserId = saasAdmin.getId();

          var tenantA = new TenantJpaEntity("AL-A", "監査テナントA");
          var tenantB = new TenantJpaEntity("AL-B", "監査テナントB");
          em.persist(tenantA);
          em.persist(tenantB);
          em.flush();
          tenantAId = tenantA.getId();
          tenantBId = tenantB.getId();

          insertMembership(adminUserId, tenantAId, "TENANT_ADMIN");
          insertMembership(memberUserId, tenantAId, "MEMBER");

          // テナント A の監査ログ(4 件、created_at 昇順 = chain_seq 順)
          insertAudit(tenantAId, adminUserId, "CREATE", "{\"taskId\":1}", day(1), 1);
          insertAudit(tenantAId, adminUserId, "UPDATE", "{\"taskId\":1}", day(2), 2);
          insertAudit(tenantAId, null, "LOGIN_FAILED", "{}", day(3), 3);
          insertAudit(tenantAId, null, "TENANT_SUSPENDED", "{\"by\":\"saas\"}", day(4), 4);
          // テナント B の行(返してはならない)
          insertAudit(tenantBId, null, "DELETE", "{}", day(1), 1);
          // 横断操作 tenant_id=NULL(返してはならない)
          insertAudit(null, null, "LOGIN", "{}", day(1), 1);

          return null;
        });

    SecurityContextHolder.clearContext();
    adminToken =
        new TasksAuthenticationToken(
            new TasksPrincipal(adminUserId, "sub-ala", "ala@example.com", "監査管理者", "カンサカンリ", null),
            List.of());
    memberToken =
        new TasksAuthenticationToken(
            new TasksPrincipal(memberUserId, "sub-alm", "alm@example.com", "一般", "イッパン", null),
            List.of());
    saasAdminToken =
        new TasksAuthenticationToken(
            new TasksPrincipal(
                saasAdminUserId, "sub-als", "als@example.com", "SaaS管理者", "サース", null),
            List.of(new SimpleGrantedAuthority("ROLE_APP_ADMIN")));
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
    if (tenantAId == null) {
      return;
    }
    txTemplate.execute(
        ignored -> {
          em.createNativeQuery(
                  "DELETE FROM audit_logs WHERE tenant_id IN (?,?) OR tenant_id IS NULL")
              .setParameter(1, tenantAId)
              .setParameter(2, tenantBId)
              .executeUpdate();
          em.createNativeQuery("DELETE FROM user_tenants WHERE tenant_id = ?")
              .setParameter(1, tenantAId)
              .executeUpdate();
          em.createNativeQuery("DELETE FROM tenants WHERE id IN (?,?)")
              .setParameter(1, tenantAId)
              .setParameter(2, tenantBId)
              .executeUpdate();
          em.createNativeQuery("DELETE FROM users WHERE id IN (?,?,?)")
              .setParameter(1, adminUserId)
              .setParameter(2, memberUserId)
              .setParameter(3, saasAdminUserId)
              .executeUpdate();
          return null;
        });
  }

  @Test
  void returnsOnlyOwnTenantLogs_orderedByCreatedAtDesc() throws Exception {
    mockMvc
        .perform(
            get("/api/audit-logs")
                .header("X-Tenant-Id", String.valueOf(tenantAId))
                .with(authentication(adminToken)))
        .andExpect(status().isOk())
        // テナント A の 4 件のみ(テナント B の DELETE・横断の LOGIN は除外)
        .andExpect(jsonPath("$.totalElements").value(4))
        .andExpect(jsonPath("$.content.length()").value(4))
        // created_at 降順
        .andExpect(jsonPath("$.content[0].action").value("TENANT_SUSPENDED"))
        .andExpect(jsonPath("$.content[3].action").value("CREATE"))
        // detail は object として復元される
        .andExpect(jsonPath("$.content[3].detail.taskId").value(1))
        // userId null(LOGIN_FAILED)
        .andExpect(jsonPath("$.content[1].action").value("LOGIN_FAILED"))
        .andExpect(jsonPath("$.content[1].userId").doesNotExist())
        // 他テナント / 横断行の action が混入しない
        .andExpect(jsonPath("$.content[?(@.action == 'DELETE')]").doesNotExist())
        .andExpect(jsonPath("$.content[?(@.action == 'LOGIN')]").doesNotExist());
  }

  @Test
  void filtersByAction() throws Exception {
    mockMvc
        .perform(
            get("/api/audit-logs")
                .header("X-Tenant-Id", String.valueOf(tenantAId))
                .param("action", "CREATE")
                .with(authentication(adminToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].action").value("CREATE"));
  }

  @Test
  void filtersByCreatedAtRange_halfOpen() throws Exception {
    // [06-02T00:00+09:00, 06-04T00:00+09:00) →
    // UPDATE(06-02)・LOGIN_FAILED(06-03)。TENANT_SUSPENDED(06-04)は除外。
    mockMvc
        .perform(
            get("/api/audit-logs")
                .header("X-Tenant-Id", String.valueOf(tenantAId))
                .param("from", "2026-06-02T00:00:00+09:00")
                .param("to", "2026-06-04T00:00:00+09:00")
                .with(authentication(adminToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(2))
        .andExpect(jsonPath("$.content[0].action").value("LOGIN_FAILED"))
        .andExpect(jsonPath("$.content[1].action").value("UPDATE"));
  }

  @Test
  void paginates() throws Exception {
    mockMvc
        .perform(
            get("/api/audit-logs")
                .header("X-Tenant-Id", String.valueOf(tenantAId))
                .param("size", "2")
                .with(authentication(adminToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(4))
        .andExpect(jsonPath("$.content.length()").value(2));
  }

  @Test
  void member_isForbidden() throws Exception {
    mockMvc
        .perform(
            get("/api/audit-logs")
                .header("X-Tenant-Id", String.valueOf(tenantAId))
                .with(authentication(memberToken)))
        .andExpect(status().isForbidden());
  }

  @Test
  void saasAdmin_isForbidden() throws Exception {
    mockMvc
        .perform(
            get("/api/audit-logs")
                .header("X-Tenant-Id", String.valueOf(tenantAId))
                .with(authentication(saasAdminToken)))
        .andExpect(status().isForbidden());
  }

  @Test
  void unauthenticated_returns401() throws Exception {
    mockMvc
        .perform(get("/api/audit-logs").header("X-Tenant-Id", String.valueOf(tenantAId)))
        .andExpect(status().isUnauthorized());
  }

  // --- helpers ---

  private static LocalDateTime day(int dayOfMonth) {
    return LocalDateTime.of(2026, 6, dayOfMonth, 10, 0);
  }

  private void insertMembership(Long userId, Long tenantId, String role) {
    em.createNativeQuery(
            "INSERT INTO user_tenants (user_id, tenant_id, role, status, joined_at)"
                + " VALUES (?,?,?,?,?)")
        .setParameter(1, userId)
        .setParameter(2, tenantId)
        .setParameter(3, role)
        .setParameter(4, "ACTIVE")
        .setParameter(5, LocalDateTime.of(2026, 1, 1, 0, 0))
        .executeUpdate();
  }

  private void insertAudit(
      @org.jspecify.annotations.Nullable Long tenantId,
      @org.jspecify.annotations.Nullable Long userId,
      String action,
      String detailJson,
      LocalDateTime createdAt,
      long chainSeq) {
    em.createNativeQuery(
            "INSERT INTO audit_logs"
                + " (tenant_id, user_id, action, detail, hash_chain, chain_seq, hash_key_id, created_at)"
                + " VALUES (?,?,?,?,?,?,?,?)")
        .setParameter(1, tenantId)
        .setParameter(2, userId)
        .setParameter(3, action)
        .setParameter(4, detailJson)
        .setParameter(5, DUMMY_HASH)
        .setParameter(6, chainSeq)
        .setParameter(7, "v1")
        .setParameter(8, createdAt)
        .executeUpdate();
  }
}
