package xyz.dgz48.tasks.webapi.tenant.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import xyz.dgz48.tasks.webapi.FixedClockConfiguration;
import xyz.dgz48.tasks.webapi.MockJwtDecoderConfiguration;
import xyz.dgz48.tasks.webapi.TestcontainersConfiguration;
import xyz.dgz48.tasks.webapi.notification.usecase.EmailSenderPort;
import xyz.dgz48.tasks.webapi.tenant.usecase.InviteTokenHasher;
import xyz.dgz48.tasks.webapi.user.adapter.persistence.UserJpaEntity;

/**
 * Flow B セルフサインアップ(double opt-in、ADR-0040 §3.3)の統合テスト。
 *
 * <p>すべて未認証・X-Tenant-Id なしの公開フロー。request は PENDING 作成 + 確認メール送信 + 常に同一 200(列挙耐性)、GET は email
 * を非消費で返す、 complete は会員登録(users 行 pending correlation)+ USED 消費。期限切れ/使用済み 409、未知トークン 404、登録済み email
 * 409 を検証する。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({
  TestcontainersConfiguration.class,
  MockJwtDecoderConfiguration.class,
  FixedClockConfiguration.class
})
class SignupIT {

  private static final Pattern TOKEN_PATTERN = Pattern.compile("token=([A-Za-z0-9_-]+)");

  @Autowired MockMvc mockMvc;
  @Autowired EntityManager em;
  @Autowired TransactionTemplate txTemplate;
  @MockitoBean EmailSenderPort emailSenderPort;

  @BeforeEach
  void setUp() {
    SecurityContextHolder.clearContext();
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
    txTemplate.execute(
        ignored -> {
          em.createNativeQuery(
                  "DELETE FROM signup_requests WHERE email IN"
                      + " ('newcomer@example.com','existing@example.com')")
              .executeUpdate();
          em.createNativeQuery(
                  "DELETE FROM users WHERE email IN"
                      + " ('newcomer@example.com','existing@example.com')")
              .executeUpdate();
          return null;
        });
  }

  @Test
  void requestSignup_returns200_createsPending_andSendsMailWithMatchingTokenHash()
      throws Exception {
    mockMvc
        .perform(
            post("/api/signup/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"newcomer@example.com\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").isNotEmpty());

    ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
    Mockito.verify(emailSenderPort)
        .send(
            org.mockito.ArgumentMatchers.eq("newcomer@example.com"),
            org.mockito.ArgumentMatchers.anyString(),
            body.capture());
    Matcher matcher = TOKEN_PATTERN.matcher(body.getValue());
    assertThat(matcher.find()).isTrue();
    String rawToken = matcher.group(1);

    Object[] row = findSignup("newcomer@example.com");
    assertThat(row[0]).isEqualTo(InviteTokenHasher.sha256Hex(rawToken)); // token_hash
    assertThat(row[1]).isEqualTo("PENDING"); // status
    assertThat(row[0]).isNotEqualTo(rawToken); // 平文非保存
  }

  @Test
  void requestSignup_revokesPreviousPending_onReRequest() throws Exception {
    requestSignup("newcomer@example.com");
    requestSignup("newcomer@example.com");

    assertThat(countWithStatus("newcomer@example.com", "PENDING")).isEqualTo(1);
    assertThat(countWithStatus("newcomer@example.com", "REVOKED")).isEqualTo(1);
  }

  @Test
  void getSignup_returnsEmail_withoutConsuming() throws Exception {
    String rawToken = "signup-view-token";
    insertSignup(rawToken, "newcomer@example.com", "PENDING", futureExpiry());

    mockMvc
        .perform(get("/api/signup/{token}", rawToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value("newcomer@example.com"))
        .andExpect(jsonPath("$.status").value("PENDING"));

    assertThat(signupStatus(rawToken)).isEqualTo("PENDING");
  }

  @Test
  void getSignup_reportsExpiredStatus_for200Guidance() throws Exception {
    String rawToken = "signup-expired-view";
    insertSignup(rawToken, "newcomer@example.com", "PENDING", LocalDateTime.of(2020, 1, 1, 0, 0));

    mockMvc
        .perform(get("/api/signup/{token}", rawToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("EXPIRED"));
  }

  @Test
  void getSignup_returns404_whenUnknownToken() throws Exception {
    mockMvc
        .perform(get("/api/signup/{token}", "no-such-token"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("E_NOT_FOUND"));
  }

  @Test
  void completeSignup_registersUser_andConsumesToken() throws Exception {
    String rawToken = "signup-complete-token";
    insertSignup(rawToken, "newcomer@example.com", "PENDING", futureExpiry());

    mockMvc
        .perform(
            post("/api/signup/{token}/complete", rawToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"fullName\":\"新顔太郎\",\"fullNameKana\":\"シンガオタロウ\","
                        + "\"departmentName\":\"開発部\",\"password\":\"Passw0rd!\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.userId").isNumber());

    assertThat(signupStatus(rawToken)).isEqualTo("USED");

    Object[] user = findUserByEmail("newcomer@example.com");
    assertThat((String) user[1]).startsWith("pending:"); // oidc_sub correlation 未了
  }

  @Test
  void completeSignup_returns409_whenExpired() throws Exception {
    String rawToken = "signup-expired-complete";
    insertSignup(rawToken, "newcomer@example.com", "PENDING", LocalDateTime.of(2020, 1, 1, 0, 0));

    mockMvc
        .perform(
            post("/api/signup/{token}/complete", rawToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"fullName\":\"新顔太郎\",\"fullNameKana\":\"シンガオタロウ\","
                        + "\"departmentName\":null,\"password\":\"Passw0rd!\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("E_CONFLICT"));

    assertThat(signupStatus(rawToken)).isEqualTo("PENDING");
  }

  @Test
  void completeSignup_returns409_whenAlreadyUsed() throws Exception {
    String rawToken = "signup-used-token";
    insertSignup(rawToken, "newcomer@example.com", "USED", futureExpiry());

    mockMvc
        .perform(
            post("/api/signup/{token}/complete", rawToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"fullName\":\"新顔太郎\",\"fullNameKana\":\"シンガオタロウ\","
                        + "\"departmentName\":null,\"password\":\"Passw0rd!\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("E_CONFLICT"));
  }

  @Test
  void completeSignup_returns409_whenEmailAlreadyRegistered() throws Exception {
    txTemplate.execute(
        ignored -> {
          em.persist(
              new UserJpaEntity("sub-existing", "existing@example.com", "既存者", "キゾンシャ", null));
          return null;
        });
    String rawToken = "signup-registered-token";
    insertSignup(rawToken, "existing@example.com", "PENDING", futureExpiry());

    mockMvc
        .perform(
            post("/api/signup/{token}/complete", rawToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"fullName\":\"既存者\",\"fullNameKana\":\"キゾンシャ\","
                        + "\"departmentName\":null,\"password\":\"Passw0rd!\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("E_CONFLICT"));

    assertThat(signupStatus(rawToken)).isEqualTo("PENDING");
  }

  // --- helpers ---

  private LocalDateTime futureExpiry() {
    return LocalDateTime.of(2099, 1, 1, 0, 0);
  }

  private void requestSignup(String email) throws Exception {
    mockMvc
        .perform(
            post("/api/signup/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\"}"))
        .andExpect(status().isOk());
  }

  private void insertSignup(String rawToken, String email, String status, LocalDateTime expiresAt) {
    txTemplate.execute(
        ignored -> {
          em.createNativeQuery(
                  "INSERT INTO signup_requests"
                      + " (email, token_hash, status, expires_at, created_at)"
                      + " VALUES (?,?,?,?,?)")
              .setParameter(1, email)
              .setParameter(2, InviteTokenHasher.sha256Hex(rawToken))
              .setParameter(3, status)
              .setParameter(4, expiresAt)
              .setParameter(5, LocalDateTime.of(2026, 1, 1, 0, 0))
              .executeUpdate();
          return null;
        });
  }

  private Object[] findSignup(String email) {
    return txTemplate.execute(
        ignored ->
            (Object[])
                em.createNativeQuery(
                        "SELECT token_hash, status FROM signup_requests WHERE email = ?")
                    .setParameter(1, email)
                    .getSingleResult());
  }

  private String signupStatus(String rawToken) {
    return txTemplate.execute(
        ignored ->
            (String)
                em.createNativeQuery("SELECT status FROM signup_requests WHERE token_hash = ?")
                    .setParameter(1, InviteTokenHasher.sha256Hex(rawToken))
                    .getSingleResult());
  }

  private long countWithStatus(String email, String status) {
    return txTemplate.execute(
        ignored ->
            ((Number)
                    em.createNativeQuery(
                            "SELECT COUNT(*) FROM signup_requests WHERE email = ? AND status = ?")
                        .setParameter(1, email)
                        .setParameter(2, status)
                        .getSingleResult())
                .longValue());
  }

  private Object[] findUserByEmail(String email) {
    return txTemplate.execute(
        ignored ->
            (Object[])
                em.createNativeQuery("SELECT id, oidc_sub FROM users WHERE email = ?")
                    .setParameter(1, email)
                    .getSingleResult());
  }
}
