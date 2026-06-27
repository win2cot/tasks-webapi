package xyz.dgz48.tasks.webapi.tenant.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import xyz.dgz48.tasks.webapi.FixedClockConfiguration;
import xyz.dgz48.tasks.webapi.MockJwtDecoderConfiguration;
import xyz.dgz48.tasks.webapi.TestcontainersConfiguration;
import xyz.dgz48.tasks.webapi.notification.usecase.EmailSenderPort;
import xyz.dgz48.tasks.webapi.security.adapter.web.TasksAuthenticationToken;
import xyz.dgz48.tasks.webapi.security.domain.TasksPrincipal;
import xyz.dgz48.tasks.webapi.tenant.adapter.persistence.TenantJpaEntity;
import xyz.dgz48.tasks.webapi.tenant.usecase.InviteTokenHasher;
import xyz.dgz48.tasks.webapi.user.adapter.persistence.UserJpaEntity;

/**
 * A-09 招待発行(POST /api/tenant/users/invite、ADR-0017)の統合テスト。
 *
 * <p>Tenant Admin が新規 email を招待 → 201・invitations 行 PENDING・SES 送信(平文トークンの SHA-256 が DB の
 * token_hash と一致)を検証する。既メンバーの重複招待は 409、再招待で旧 PENDING は REVOKED、SaaS Admin(非メンバー)は 403。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({
  TestcontainersConfiguration.class,
  MockJwtDecoderConfiguration.class,
  FixedClockConfiguration.class
})
class InvitationIT {

  private static final Pattern TOKEN_PATTERN = Pattern.compile("token=([A-Za-z0-9_-]+)");

  @Autowired MockMvc mockMvc;
  @Autowired EntityManager em;
  @Autowired TransactionTemplate txTemplate;
  @MockitoBean EmailSenderPort emailSenderPort;

  private Long adminUserId;
  private Long memberUserId;
  private Long saasAdminUserId;
  private Long tenantId;

  private TasksAuthenticationToken tenantAdminToken;
  private TasksAuthenticationToken saasAdminToken;

  @BeforeEach
  void setUp() {
    txTemplate.execute(
        ignored -> {
          var adminUser =
              new UserJpaEntity("sub-inv-admin", "inv-admin@example.com", "招待太郎", "ショウタイタロウ", null);
          var memberUser =
              new UserJpaEntity("sub-inv-member", "member@example.com", "既存花子", "キゾンハナコ", null);
          var saasAdminUser =
              new UserJpaEntity("sub-inv-saas", "inv-saas@example.com", "運営三郎", "ウンエイサブロウ", null);
          em.persist(adminUser);
          em.persist(memberUser);
          em.persist(saasAdminUser);
          em.flush();
          adminUserId = adminUser.getId();
          memberUserId = memberUser.getId();
          saasAdminUserId = saasAdminUser.getId();

          var tenant = new TenantJpaEntity("INV-IT-1", "招待ITテナント");
          em.persist(tenant);
          em.flush();
          tenantId = tenant.getId();

          insertMembership(adminUserId, tenantId, "TENANT_ADMIN");
          insertMembership(memberUserId, tenantId, "MEMBER");
          // saasAdminUser はどのテナントにも所属させない(非メンバー)。
          return null;
        });

    SecurityContextHolder.clearContext();
    tenantAdminToken =
        new TasksAuthenticationToken(
            new TasksPrincipal(
                adminUserId, "sub-inv-admin", "inv-admin@example.com", "招待太郎", "ショウタイタロウ", null),
            List.of());
    saasAdminToken =
        new TasksAuthenticationToken(
            new TasksPrincipal(
                saasAdminUserId, "sub-inv-saas", "inv-saas@example.com", "運営三郎", "ウンエイサブロウ", null),
            List.of(new SimpleGrantedAuthority("ROLE_APP_ADMIN")));
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
    if (tenantId == null) {
      return;
    }
    txTemplate.execute(
        ignored -> {
          em.createNativeQuery("DELETE FROM invitations WHERE tenant_id = ?")
              .setParameter(1, tenantId)
              .executeUpdate();
          em.createNativeQuery("DELETE FROM user_tenants WHERE tenant_id = ?")
              .setParameter(1, tenantId)
              .executeUpdate();
          em.createNativeQuery("DELETE FROM tenants WHERE id = ?")
              .setParameter(1, tenantId)
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
  void inviteUser_returns201_persistsPending_andSendsMailWithMatchingTokenHash() throws Exception {
    String email = "newcomer@example.com";

    mockMvc
        .perform(
            post("/api/tenant/users/invite")
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"role\":\"MEMBER\"}")
                .with(authentication(tenantAdminToken)))
        .andExpect(status().isCreated());

    // SES(モック)に送られた本文から平文トークンを取り出す
    ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
    org.mockito.Mockito.verify(emailSenderPort)
        .send(
            org.mockito.ArgumentMatchers.eq(email),
            org.mockito.ArgumentMatchers.anyString(),
            body.capture());
    Matcher matcher = TOKEN_PATTERN.matcher(body.getValue());
    assertThat(matcher.find()).isTrue();
    String rawToken = matcher.group(1);
    String expectedHash = InviteTokenHasher.sha256Hex(rawToken);

    Object[] row = findInvitation(email);
    assertThat(row[0]).isEqualTo(expectedHash); // token_hash
    assertThat(row[1]).isEqualTo("PENDING"); // status
    assertThat(row[2]).isEqualTo("MEMBER"); // role
    assertThat(((Number) row[3]).longValue()).isEqualTo(adminUserId); // invited_by
    // DB には平文を保存しない
    assertThat(row[0]).isNotEqualTo(rawToken);
  }

  @Test
  void inviteUser_returns409_whenEmailAlreadyMember() throws Exception {
    mockMvc
        .perform(
            post("/api/tenant/users/invite")
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"member@example.com\",\"role\":\"MEMBER\"}")
                .with(authentication(tenantAdminToken)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("E_CONFLICT"));

    assertThat(countInvitations("member@example.com")).isZero();
    org.mockito.Mockito.verifyNoInteractions(emailSenderPort);
  }

  @Test
  void inviteUser_revokesPreviousPending_onReInvite() throws Exception {
    String email = "twice@example.com";
    invite(email);
    invite(email);

    assertThat(countInvitationsWithStatus(email, "PENDING")).isEqualTo(1);
    assertThat(countInvitationsWithStatus(email, "REVOKED")).isEqualTo(1);
  }

  @Test
  void inviteUser_returns403_whenSaasAdmin() throws Exception {
    mockMvc
        .perform(
            post("/api/tenant/users/invite")
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"newcomer@example.com\",\"role\":\"MEMBER\"}")
                .with(authentication(saasAdminToken)))
        .andExpect(status().isForbidden());
  }

  // --- helpers ---

  private void invite(String email) throws Exception {
    mockMvc
        .perform(
            post("/api/tenant/users/invite")
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"role\":\"MEMBER\"}")
                .with(authentication(tenantAdminToken)))
        .andExpect(status().isCreated());
  }

  private void insertMembership(Long userId, Long tId, String role) {
    em.createNativeQuery(
            "INSERT INTO user_tenants (user_id, tenant_id, role, status, joined_at)"
                + " VALUES (?,?,?,?,?)")
        .setParameter(1, userId)
        .setParameter(2, tId)
        .setParameter(3, role)
        .setParameter(4, "ACTIVE")
        .setParameter(5, LocalDateTime.of(2026, 1, 1, 0, 0))
        .executeUpdate();
  }

  private Object[] findInvitation(String email) {
    return txTemplate.execute(
        ignored ->
            (Object[])
                em.createNativeQuery(
                        "SELECT token_hash, status, role, invited_by FROM invitations"
                            + " WHERE tenant_id = ? AND email = ?")
                    .setParameter(1, tenantId)
                    .setParameter(2, email)
                    .getSingleResult());
  }

  private long countInvitations(String email) {
    return txTemplate.execute(
        ignored ->
            ((Number)
                    em.createNativeQuery(
                            "SELECT COUNT(*) FROM invitations WHERE tenant_id = ? AND email = ?")
                        .setParameter(1, tenantId)
                        .setParameter(2, email)
                        .getSingleResult())
                .longValue());
  }

  private long countInvitationsWithStatus(String email, String status) {
    return txTemplate.execute(
        ignored ->
            ((Number)
                    em.createNativeQuery(
                            "SELECT COUNT(*) FROM invitations"
                                + " WHERE tenant_id = ? AND email = ? AND status = ?")
                        .setParameter(1, tenantId)
                        .setParameter(2, email)
                        .setParameter(3, status)
                        .getSingleResult())
                .longValue());
  }
}
