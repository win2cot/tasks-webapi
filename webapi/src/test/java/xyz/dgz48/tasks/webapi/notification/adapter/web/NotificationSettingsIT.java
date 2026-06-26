package xyz.dgz48.tasks.webapi.notification.adapter.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import xyz.dgz48.tasks.webapi.MockJwtDecoderConfiguration;
import xyz.dgz48.tasks.webapi.TestcontainersConfiguration;
import xyz.dgz48.tasks.webapi.security.adapter.web.TasksAuthenticationToken;
import xyz.dgz48.tasks.webapi.security.domain.TasksPrincipal;
import xyz.dgz48.tasks.webapi.tenant.adapter.persistence.TenantJpaEntity;
import xyz.dgz48.tasks.webapi.user.adapter.persistence.UserJpaEntity;

/**
 * 通知設定 API(A-23/A-24、GET/PUT /api/users/me/notification-settings、S-10)の統合テスト。
 *
 * <p>レコード未存在=デフォルト全 true、upsert の永続化、必須フラグ欠如=400、テナント別スコープ(ADR-0010)、認可を検証する。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, MockJwtDecoderConfiguration.class})
class NotificationSettingsIT {

  private static final String PATH = "/api/users/me/notification-settings";

  @Autowired MockMvc mockMvc;
  @Autowired EntityManager em;
  @Autowired TransactionTemplate txTemplate;

  private Long tenantAId;
  private Long tenantBId;
  private Long memberUserId;
  private Long saasAdminUserId;

  private TasksAuthenticationToken memberToken;
  private TasksAuthenticationToken saasAdminToken;

  @BeforeEach
  void setUp() {
    txTemplate.execute(
        ignored -> {
          var member = new UserJpaEntity("sub-nsm", "nsm@example.com", "通知ユーザー", "ツウチ", null);
          var saasAdmin = new UserJpaEntity("sub-nss", "nss@example.com", "SaaS管理者", "サース", null);
          em.persist(member);
          em.persist(saasAdmin);
          em.flush();
          memberUserId = member.getId();
          saasAdminUserId = saasAdmin.getId();

          var tenantA = new TenantJpaEntity("NS-A", "通知テナントA");
          var tenantB = new TenantJpaEntity("NS-B", "通知テナントB");
          em.persist(tenantA);
          em.persist(tenantB);
          em.flush();
          tenantAId = tenantA.getId();
          tenantBId = tenantB.getId();

          // member は両テナントの ACTIVE メンバー
          insertMembership(memberUserId, tenantAId);
          insertMembership(memberUserId, tenantBId);

          return null;
        });

    SecurityContextHolder.clearContext();
    memberToken =
        new TasksAuthenticationToken(
            new TasksPrincipal(memberUserId, "sub-nsm", "nsm@example.com", "通知ユーザー", "ツウチ", null),
            List.of());
    saasAdminToken =
        new TasksAuthenticationToken(
            new TasksPrincipal(
                saasAdminUserId, "sub-nss", "nss@example.com", "SaaS管理者", "サース", null),
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
          em.createNativeQuery("DELETE FROM user_notification_settings WHERE tenant_id IN (?,?)")
              .setParameter(1, tenantAId)
              .setParameter(2, tenantBId)
              .executeUpdate();
          em.createNativeQuery("DELETE FROM user_tenants WHERE tenant_id IN (?,?)")
              .setParameter(1, tenantAId)
              .setParameter(2, tenantBId)
              .executeUpdate();
          em.createNativeQuery("DELETE FROM tenants WHERE id IN (?,?)")
              .setParameter(1, tenantAId)
              .setParameter(2, tenantBId)
              .executeUpdate();
          em.createNativeQuery("DELETE FROM users WHERE id IN (?,?)")
              .setParameter(1, memberUserId)
              .setParameter(2, saasAdminUserId)
              .executeUpdate();
          return null;
        });
  }

  @Test
  void get_returnsDefaultsWhenNoRecord() throws Exception {
    mockMvc
        .perform(
            get(PATH)
                .header("X-Tenant-Id", String.valueOf(tenantAId))
                .with(authentication(memberToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.emailDueToday").value(true))
        .andExpect(jsonPath("$.emailOverdue").value(true))
        .andExpect(jsonPath("$.emailStakeholder").value(true))
        // レコード未存在のため updatedAt は省略
        .andExpect(jsonPath("$.updatedAt").doesNotExist());
  }

  @Test
  void put_thenGet_persistsValues() throws Exception {
    mockMvc
        .perform(
            put(PATH)
                .header("X-Tenant-Id", String.valueOf(tenantAId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"emailDueToday\":false,\"emailOverdue\":true,\"emailStakeholder\":false}")
                .with(authentication(memberToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.emailDueToday").value(false))
        .andExpect(jsonPath("$.emailOverdue").value(true))
        .andExpect(jsonPath("$.emailStakeholder").value(false))
        .andExpect(jsonPath("$.updatedAt").exists());

    mockMvc
        .perform(
            get(PATH)
                .header("X-Tenant-Id", String.valueOf(tenantAId))
                .with(authentication(memberToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.emailDueToday").value(false))
        .andExpect(jsonPath("$.emailOverdue").value(true))
        .andExpect(jsonPath("$.emailStakeholder").value(false))
        .andExpect(jsonPath("$.updatedAt").exists());
  }

  @Test
  void put_missingField_returns400() throws Exception {
    mockMvc
        .perform(
            put(PATH)
                .header("X-Tenant-Id", String.valueOf(tenantAId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"emailDueToday\":true,\"emailOverdue\":true}")
                .with(authentication(memberToken)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("E_VALIDATION"));
  }

  @Test
  void settingsAreScopedPerTenant() throws Exception {
    // テナント A で全 false に更新
    mockMvc
        .perform(
            put(PATH)
                .header("X-Tenant-Id", String.valueOf(tenantAId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"emailDueToday\":false,\"emailOverdue\":false,\"emailStakeholder\":false}")
                .with(authentication(memberToken)))
        .andExpect(status().isOk());

    // テナント B には影響しない(デフォルト全 true)
    mockMvc
        .perform(
            get(PATH)
                .header("X-Tenant-Id", String.valueOf(tenantBId))
                .with(authentication(memberToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.emailDueToday").value(true))
        .andExpect(jsonPath("$.emailOverdue").value(true))
        .andExpect(jsonPath("$.emailStakeholder").value(true))
        .andExpect(jsonPath("$.updatedAt").doesNotExist());
  }

  @Test
  void saasAdmin_isForbidden() throws Exception {
    mockMvc
        .perform(
            get(PATH)
                .header("X-Tenant-Id", String.valueOf(tenantAId))
                .with(authentication(saasAdminToken)))
        .andExpect(status().isForbidden());
  }

  @Test
  void unauthenticated_returns401() throws Exception {
    mockMvc
        .perform(get(PATH).header("X-Tenant-Id", String.valueOf(tenantAId)))
        .andExpect(status().isUnauthorized());
  }

  // --- helpers ---

  private void insertMembership(Long userId, Long tenantId) {
    em.createNativeQuery(
            "INSERT INTO user_tenants (user_id, tenant_id, role, status, joined_at)"
                + " VALUES (?,?,?,?,?)")
        .setParameter(1, userId)
        .setParameter(2, tenantId)
        .setParameter(3, "MEMBER")
        .setParameter(4, "ACTIVE")
        .setParameter(5, LocalDateTime.of(2026, 1, 1, 0, 0))
        .executeUpdate();
  }
}
