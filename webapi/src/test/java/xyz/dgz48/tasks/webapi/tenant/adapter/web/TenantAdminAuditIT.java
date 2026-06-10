package xyz.dgz48.tasks.webapi.tenant.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import xyz.dgz48.tasks.webapi.FixedClockConfiguration;
import xyz.dgz48.tasks.webapi.MockJwtDecoderConfiguration;
import xyz.dgz48.tasks.webapi.TestcontainersConfiguration;
import xyz.dgz48.tasks.webapi.security.adapter.web.TasksAuthenticationToken;
import xyz.dgz48.tasks.webapi.security.domain.TasksPrincipal;
import xyz.dgz48.tasks.webapi.tenant.adapter.persistence.TenantJpaEntity;
import xyz.dgz48.tasks.webapi.user.adapter.persistence.UserJpaEntity;

/**
 * SaaS Admin プラットフォーム操作の監査記録統合テスト(ADR-0020)。
 *
 * <p>write diff / read 記録 / tenant_id 帰属 / A-22 での Tenant Admin からの可視性を検証する。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({
  TestcontainersConfiguration.class,
  MockJwtDecoderConfiguration.class,
  FixedClockConfiguration.class
})
class TenantAdminAuditIT {

  @Autowired MockMvc mockMvc;
  @Autowired EntityManager em;
  @Autowired TransactionTemplate txTemplate;

  private Long adminUserId;
  private Long tenantId;
  private TasksAuthenticationToken saasAdminToken;

  @BeforeEach
  void setUp() {
    txTemplate.execute(
        ignored -> {
          var adminUser =
              new UserJpaEntity(
                  "sub-audit-admin", "audit-admin@example.com", "監査管理者", "カンサカンリシャ", null);
          em.persist(adminUser);
          em.flush();
          adminUserId = adminUser.getId();

          var tenant = new TenantJpaEntity("audit-it-1", "監査ITテナント");
          em.persist(tenant);
          em.flush();
          tenantId = tenant.getId();

          em.createNativeQuery(
                  "INSERT INTO user_tenants (user_id, tenant_id, role, status, joined_at)"
                      + " VALUES (?,?,?,?,?)")
              .setParameter(1, adminUserId)
              .setParameter(2, tenantId)
              .setParameter(3, "TENANT_ADMIN")
              .setParameter(4, "ACTIVE")
              .setParameter(5, LocalDateTime.of(2026, 1, 1, 0, 0))
              .executeUpdate();

          return null;
        });

    SecurityContextHolder.clearContext();

    var principal =
        new TasksPrincipal(
            adminUserId, "sub-audit-admin", "audit-admin@example.com", "監査管理者", "カンサカンリシャ", null);
    saasAdminToken =
        new TasksAuthenticationToken(
            principal, List.of(new SimpleGrantedAuthority("ROLE_APP_ADMIN")));
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
    if (tenantId == null) return;
    txTemplate.execute(
        ignored -> {
          em.createNativeQuery("DELETE FROM audit_logs WHERE tenant_id = ? OR tenant_id IS NULL")
              .setParameter(1, tenantId)
              .executeUpdate();
          em.createNativeQuery("DELETE FROM user_tenants WHERE tenant_id = ?")
              .setParameter(1, tenantId)
              .executeUpdate();
          em.createNativeQuery("DELETE FROM tenants WHERE id = ?")
              .setParameter(1, tenantId)
              .executeUpdate();
          em.createNativeQuery("DELETE FROM users WHERE id = ?")
              .setParameter(1, adminUserId)
              .executeUpdate();
          return null;
        });
  }

  // =========================================================
  // A-06: PUT /api/tenants/{id} — write diff
  // =========================================================

  @Test
  void updateTenant_recordsTenantUpdatedAuditWithDiff() throws Exception {
    mockMvc
        .perform(
            put("/api/tenants/{id}", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"更新後テナント名\"}")
                .with(authentication(saasAdminToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("更新後テナント名"));

    txTemplate.execute(
        ignored -> {
          var row =
              em.createNativeQuery(
                      "SELECT action, tenant_id, detail FROM audit_logs"
                          + " WHERE action = 'TENANT_UPDATED' ORDER BY id DESC LIMIT 1")
                  .getSingleResult();
          Object[] cols = (Object[]) row;
          assertThat(cols[0]).isEqualTo("TENANT_UPDATED");
          assertThat(((Number) cols[1]).longValue()).isEqualTo(tenantId);
          assertThat((String) cols[2]).contains("\"field\": \"name\"");
          assertThat((String) cols[2]).contains("\"old\": \"監査ITテナント\"");
          assertThat((String) cols[2]).contains("\"new\": \"更新後テナント名\"");
          return null;
        });
  }

  @Test
  void updateTenant_noAuditRecord_whenNameUnchanged() throws Exception {
    mockMvc
        .perform(
            put("/api/tenants/{id}", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"監査ITテナント\"}")
                .with(authentication(saasAdminToken)))
        .andExpect(status().isOk());

    txTemplate.execute(
        ignored -> {
          long count =
              ((Number)
                      em.createNativeQuery(
                              "SELECT COUNT(*) FROM audit_logs WHERE action = 'TENANT_UPDATED'")
                          .getSingleResult())
                  .longValue();
          assertThat(count).isZero();
          return null;
        });
  }

  // =========================================================
  // A-26: PATCH /api/tenants/{id}/status — write audit
  // =========================================================

  @Test
  void updateTenantStatus_recordsTenantSuspendedAudit_withTargetTenantId() throws Exception {
    mockMvc
        .perform(
            patch("/api/tenants/{id}/status", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"SUSPENDED\"}")
                .with(authentication(saasAdminToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUSPENDED"));

    txTemplate.execute(
        ignored -> {
          var row =
              em.createNativeQuery(
                      "SELECT action, tenant_id FROM audit_logs"
                          + " WHERE action = 'TENANT_SUSPENDED' ORDER BY id DESC LIMIT 1")
                  .getSingleResult();
          Object[] cols = (Object[]) row;
          assertThat(cols[0]).isEqualTo("TENANT_SUSPENDED");
          assertThat(((Number) cols[1]).longValue()).isEqualTo(tenantId);
          return null;
        });
  }

  @Test
  void updateTenantStatus_noAuditRecord_whenStatusUnchanged() throws Exception {
    mockMvc
        .perform(
            patch("/api/tenants/{id}/status", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"ACTIVE\"}")
                .with(authentication(saasAdminToken)))
        .andExpect(status().isOk());

    txTemplate.execute(
        ignored -> {
          long count =
              ((Number)
                      em.createNativeQuery(
                              "SELECT COUNT(*) FROM audit_logs WHERE action IN"
                                  + " ('TENANT_SUSPENDED','TENANT_REACTIVATED')")
                          .getSingleResult())
                  .longValue();
          assertThat(count).isZero();
          return null;
        });
  }

  // =========================================================
  // A-25: GET /api/tenants/{id} — read audit (SaaS Admin only)
  // =========================================================

  @Test
  void getTenant_recordsTenantViewedAudit_forSaasAdmin() throws Exception {
    mockMvc
        .perform(get("/api/tenants/{id}", tenantId).with(authentication(saasAdminToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(tenantId));

    txTemplate.execute(
        ignored -> {
          var row =
              em.createNativeQuery(
                      "SELECT action, tenant_id FROM audit_logs"
                          + " WHERE action = 'TENANT_VIEWED' ORDER BY id DESC LIMIT 1")
                  .getSingleResult();
          Object[] cols = (Object[]) row;
          assertThat(cols[0]).isEqualTo("TENANT_VIEWED");
          assertThat(((Number) cols[1]).longValue()).isEqualTo(tenantId);
          return null;
        });
  }

  @Test
  void getTenant_noAuditRecord_forNormalUser() throws Exception {
    var memberPrincipal =
        new TasksPrincipal(
            adminUserId, "sub-audit-admin", "audit-admin@example.com", "監査管理者", "カンサカンリシャ", null);
    var memberToken =
        new TasksAuthenticationToken(
            memberPrincipal, List.of(new SimpleGrantedAuthority("ROLE_TENANT_ADMIN")));

    mockMvc
        .perform(get("/api/tenants/{id}", tenantId).with(authentication(memberToken)))
        .andExpect(status().isOk());

    txTemplate.execute(
        ignored -> {
          long count =
              ((Number)
                      em.createNativeQuery(
                              "SELECT COUNT(*) FROM audit_logs WHERE action = 'TENANT_VIEWED'")
                          .getSingleResult())
                  .longValue();
          assertThat(count).isZero();
          return null;
        });
  }

  // =========================================================
  // A-04: GET /api/tenants — read audit (tenant_id=NULL)
  // =========================================================

  @Test
  void listTenants_recordsTenantListViewedAuditWithNullTenantId() throws Exception {
    mockMvc
        .perform(get("/api/tenants").with(authentication(saasAdminToken)))
        .andExpect(status().isOk());

    txTemplate.execute(
        ignored -> {
          var row =
              em.createNativeQuery(
                      "SELECT action, tenant_id FROM audit_logs"
                          + " WHERE action = 'TENANT_LIST_VIEWED' ORDER BY id DESC LIMIT 1")
                  .getSingleResult();
          Object[] cols = (Object[]) row;
          assertThat(cols[0]).isEqualTo("TENANT_LIST_VIEWED");
          assertThat(cols[1]).isNull();
          return null;
        });
  }

  // =========================================================
  // A-27: GET /api/platform/metrics — read audit (tenant_id=NULL)
  // =========================================================

  @Test
  void getPlatformMetrics_recordsPlatformMetricsViewedAuditWithNullTenantId() throws Exception {
    mockMvc
        .perform(get("/api/platform/metrics").with(authentication(saasAdminToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalTenants").isNumber());

    txTemplate.execute(
        ignored -> {
          var row =
              em.createNativeQuery(
                      "SELECT action, tenant_id FROM audit_logs"
                          + " WHERE action = 'PLATFORM_METRICS_VIEWED' ORDER BY id DESC LIMIT 1")
                  .getSingleResult();
          Object[] cols = (Object[]) row;
          assertThat(cols[0]).isEqualTo("PLATFORM_METRICS_VIEWED");
          assertThat(cols[1]).isNull();
          return null;
        });
  }

  // =========================================================
  // A-22 可視性: Tenant Admin が自テナントへの SaaS Admin 操作を確認できる
  // =========================================================

  @Test
  void updateTenantStatus_auditVisibleToTenantAdmin_viaA22TenantIdAttribution() throws Exception {
    mockMvc
        .perform(
            patch("/api/tenants/{id}/status", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"SUSPENDED\"}")
                .with(authentication(saasAdminToken)))
        .andExpect(status().isOk());

    txTemplate.execute(
        ignored -> {
          long count =
              ((Number)
                      em.createNativeQuery(
                              "SELECT COUNT(*) FROM audit_logs"
                                  + " WHERE action = 'TENANT_SUSPENDED' AND tenant_id = ?")
                          .setParameter(1, tenantId)
                          .getSingleResult())
                  .longValue();
          // tenant_id=対象テナント id が設定されているため Tenant Admin が A-22 で参照可能(ADR-0020 §3.4)
          assertThat(count).isEqualTo(1);
          return null;
        });
  }
}
