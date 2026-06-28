package xyz.dgz48.tasks.webapi.tenant.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import xyz.dgz48.tasks.webapi.FixedClockConfiguration;
import xyz.dgz48.tasks.webapi.MockJwtDecoderConfiguration;
import xyz.dgz48.tasks.webapi.TestcontainersConfiguration;
import xyz.dgz48.tasks.webapi.tenant.adapter.persistence.TenantJpaEntity;
import xyz.dgz48.tasks.webapi.tenant.usecase.InviteTokenHasher;
import xyz.dgz48.tasks.webapi.user.adapter.persistence.UserJpaEntity;

/**
 * Flow A 招待受諾(GET /api/invitations/{token} ・ POST .../accept、ADR-0040 §3.3)の統合テスト。
 *
 * <p>受諾は未認証・X-Tenant-Id なしの公開フロー。未登録 email の受諾 → 会員登録 + user_tenants 紐付け + 招待 USED 消費(ハッピーパス)、 期限切れ
 * → 409、未知トークン → 404、登録済み email → 409(ログインして参加)を検証する。FixedClock により expiresAt 判定は決定的。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({
  TestcontainersConfiguration.class,
  MockJwtDecoderConfiguration.class,
  FixedClockConfiguration.class
})
class InvitationAcceptIT {

  @Autowired MockMvc mockMvc;
  @Autowired EntityManager em;
  @Autowired TransactionTemplate txTemplate;

  private Long inviterUserId;
  private Long tenantId;

  @BeforeEach
  void setUp() {
    txTemplate.execute(
        ignored -> {
          var inviter =
              new UserJpaEntity("sub-acc-admin", "acc-admin@example.com", "招待者", "ショウタイシャ", null);
          em.persist(inviter);
          em.flush();
          inviterUserId = inviter.getId();

          var tenant = new TenantJpaEntity("ACC-IT-1", "受諾ITテナント");
          em.persist(tenant);
          em.flush();
          tenantId = tenant.getId();

          insertMembership(inviterUserId, tenantId, "TENANT_ADMIN");
          return null;
        });
    SecurityContextHolder.clearContext();
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
          // インバイター + 受諾で作られたユーザーを email で掃除する。
          em.createNativeQuery(
                  "DELETE FROM users WHERE email IN"
                      + " ('acc-admin@example.com','newcomer@example.com','existing@example.com')")
              .executeUpdate();
          return null;
        });
  }

  @Test
  void getInvitation_returnsDetail_withoutConsuming() throws Exception {
    String rawToken = "view-token-abc";
    insertInvitation(rawToken, "newcomer@example.com", "MEMBER", "PENDING", futureExpiry());

    mockMvc
        .perform(get("/api/invitations/{token}", rawToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value("newcomer@example.com"))
        .andExpect(jsonPath("$.tenantName").value("受諾ITテナント"))
        .andExpect(jsonPath("$.role").value("MEMBER"))
        .andExpect(jsonPath("$.status").value("PENDING"))
        .andExpect(jsonPath("$.alreadyRegistered").value(false));

    // 非消費: まだ PENDING のまま。
    assertThat(invitationStatus(rawToken)).isEqualTo("PENDING");
  }

  @Test
  void getInvitation_reportsExpiredStatus_for200Guidance() throws Exception {
    String rawToken = "view-expired";
    insertInvitation(
        rawToken, "newcomer@example.com", "MEMBER", "PENDING", LocalDateTime.of(2020, 1, 1, 0, 0));

    mockMvc
        .perform(get("/api/invitations/{token}", rawToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("EXPIRED"));
  }

  @Test
  void acceptInvitation_returns409_whenAlreadyUsed() throws Exception {
    String rawToken = "used-token";
    insertInvitation(rawToken, "newcomer@example.com", "MEMBER", "USED", futureExpiry());

    mockMvc
        .perform(
            post("/api/invitations/{token}/accept", rawToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"fullName\":\"新顔太郎\",\"fullNameKana\":\"シンガオタロウ\","
                        + "\"departmentName\":null,\"password\":\"Passw0rd!\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("E_CONFLICT"));
  }

  @Test
  void getInvitation_returns404_whenUnknownToken() throws Exception {
    mockMvc
        .perform(get("/api/invitations/{token}", "no-such-token"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("E_NOT_FOUND"));
  }

  @Test
  void acceptInvitation_registersUser_linksMembership_andConsumesToken() throws Exception {
    String rawToken = "accept-token-xyz";
    insertInvitation(rawToken, "newcomer@example.com", "MEMBER", "PENDING", futureExpiry());

    mockMvc
        .perform(
            post("/api/invitations/{token}/accept", rawToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"fullName\":\"新顔太郎\",\"fullNameKana\":\"シンガオタロウ\","
                        + "\"departmentName\":\"開発部\",\"password\":\"Passw0rd!\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tenantId").value(tenantId))
        .andExpect(jsonPath("$.userId").isNumber());

    // 招待 USED 消費。
    assertThat(invitationStatus(rawToken)).isEqualTo("USED");

    // users 行が pending correlation で作成され、user_tenants が ACTIVE で紐付く。
    Object[] user = findUserByEmail("newcomer@example.com");
    Long newUserId = ((Number) user[0]).longValue();
    assertThat((String) user[1]).startsWith("pending:");
    assertThat(membershipStatus(newUserId)).isEqualTo("ACTIVE");
    assertThat(membershipRole(newUserId)).isEqualTo("MEMBER");
  }

  @Test
  void acceptInvitation_returns409_whenExpired() throws Exception {
    String rawToken = "expired-token";
    insertInvitation(
        rawToken, "newcomer@example.com", "MEMBER", "PENDING", LocalDateTime.of(2020, 1, 1, 0, 0));

    mockMvc
        .perform(
            post("/api/invitations/{token}/accept", rawToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"fullName\":\"新顔太郎\",\"fullNameKana\":\"シンガオタロウ\","
                        + "\"departmentName\":null,\"password\":\"Passw0rd!\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("E_CONFLICT"));

    assertThat(invitationStatus(rawToken)).isEqualTo("PENDING");
  }

  @Test
  void acceptInvitation_returns409_whenAlreadyRegistered() throws Exception {
    // correlation 済(実アカウント)の既存ユーザーを email で用意する。
    txTemplate.execute(
        ignored -> {
          var existing =
              new UserJpaEntity("sub-existing", "existing@example.com", "既存者", "キゾンシャ", null);
          em.persist(existing);
          return null;
        });
    String rawToken = "registered-token";
    insertInvitation(rawToken, "existing@example.com", "MEMBER", "PENDING", futureExpiry());

    mockMvc
        .perform(
            post("/api/invitations/{token}/accept", rawToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"fullName\":\"既存者\",\"fullNameKana\":\"キゾンシャ\","
                        + "\"departmentName\":null,\"password\":\"Passw0rd!\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("E_CONFLICT"));

    assertThat(invitationStatus(rawToken)).isEqualTo("PENDING");
  }

  // --- helpers ---

  private LocalDateTime futureExpiry() {
    return LocalDateTime.of(2099, 1, 1, 0, 0);
  }

  private void insertInvitation(
      String rawToken, String email, String role, String status, LocalDateTime expiresAt) {
    txTemplate.execute(
        ignored -> {
          em.createNativeQuery(
                  "INSERT INTO invitations"
                      + " (tenant_id, email, token_hash, status, role, expires_at, invited_by, created_at)"
                      + " VALUES (?,?,?,?,?,?,?,?)")
              .setParameter(1, tenantId)
              .setParameter(2, email)
              .setParameter(3, InviteTokenHasher.sha256Hex(rawToken))
              .setParameter(4, status)
              .setParameter(5, role)
              .setParameter(6, expiresAt)
              .setParameter(7, inviterUserId)
              .setParameter(8, LocalDateTime.of(2026, 1, 1, 0, 0))
              .executeUpdate();
          return null;
        });
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

  private String invitationStatus(String rawToken) {
    return txTemplate.execute(
        ignored ->
            (String)
                em.createNativeQuery("SELECT status FROM invitations WHERE token_hash = ?")
                    .setParameter(1, InviteTokenHasher.sha256Hex(rawToken))
                    .getSingleResult());
  }

  private Object[] findUserByEmail(String email) {
    return txTemplate.execute(
        ignored ->
            (Object[])
                em.createNativeQuery("SELECT id, oidc_sub FROM users WHERE email = ?")
                    .setParameter(1, email)
                    .getSingleResult());
  }

  private String membershipStatus(Long userId) {
    return txTemplate.execute(
        ignored ->
            (String)
                em.createNativeQuery(
                        "SELECT status FROM user_tenants WHERE user_id = ? AND tenant_id = ?")
                    .setParameter(1, userId)
                    .setParameter(2, tenantId)
                    .getSingleResult());
  }

  private String membershipRole(Long userId) {
    return txTemplate.execute(
        ignored ->
            (String)
                em.createNativeQuery(
                        "SELECT role FROM user_tenants WHERE user_id = ? AND tenant_id = ?")
                    .setParameter(1, userId)
                    .setParameter(2, tenantId)
                    .getSingleResult());
  }
}
