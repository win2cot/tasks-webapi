package xyz.dgz48.tasks.webapi.user.adapter.web;

import static org.hamcrest.Matchers.nullValue;
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
 * プロフィール取得 API(A-07、GET /api/users/me、S-09)の統合テスト。
 *
 * <p>テナント選択状態に依存しないこと(X-Tenant-Id なし・テナント未所属でも 200)、{@code users} の値を返すこと、認可(認証必須)を検証する。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, MockJwtDecoderConfiguration.class})
class UserProfileIT {

  private static final String PATH = "/api/users/me";

  @Autowired MockMvc mockMvc;
  @Autowired EntityManager em;
  @Autowired TransactionTemplate txTemplate;

  private Long tenantId;
  private Long memberUserId;
  private Long tenantlessUserId;

  private TasksAuthenticationToken memberToken;
  private TasksAuthenticationToken tenantlessToken;

  @BeforeEach
  void setUp() {
    txTemplate.execute(
        ignored -> {
          var member = new UserJpaEntity("sub-upm", "upm@example.com", "山田太郎", "ヤマダタロウ", "営業部");
          var tenantless = new UserJpaEntity("sub-upt", "upt@example.com", "鈴木花子", "スズキハナコ", null);
          em.persist(member);
          em.persist(tenantless);
          em.flush();
          memberUserId = member.getId();
          tenantlessUserId = tenantless.getId();

          var tenant = new TenantJpaEntity("UP-1", "プロフィールテナント");
          em.persist(tenant);
          em.flush();
          tenantId = tenant.getId();

          insertMembership(memberUserId, tenantId);
          // tenantless は意図的にどのテナントにも所属させない

          return null;
        });

    SecurityContextHolder.clearContext();
    memberToken =
        new TasksAuthenticationToken(
            new TasksPrincipal(memberUserId, "sub-upm", "upm@example.com", "山田太郎", "ヤマダタロウ", "営業部"),
            List.of());
    tenantlessToken =
        new TasksAuthenticationToken(
            new TasksPrincipal(
                tenantlessUserId, "sub-upt", "upt@example.com", "鈴木花子", "スズキハナコ", null),
            List.of());
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
    if (tenantId == null) {
      return;
    }
    txTemplate.execute(
        ignored -> {
          em.createNativeQuery("DELETE FROM user_tenants WHERE tenant_id = ?")
              .setParameter(1, tenantId)
              .executeUpdate();
          em.createNativeQuery("DELETE FROM tenants WHERE id = ?")
              .setParameter(1, tenantId)
              .executeUpdate();
          em.createNativeQuery("DELETE FROM users WHERE id IN (?,?)")
              .setParameter(1, memberUserId)
              .setParameter(2, tenantlessUserId)
              .executeUpdate();
          return null;
        });
  }

  @Test
  void returnsProfileFromUsersTable_withoutTenantHeader() throws Exception {
    mockMvc
        .perform(get(PATH).with(authentication(memberToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(memberUserId))
        .andExpect(jsonPath("$.email").value("upm@example.com"))
        .andExpect(jsonPath("$.fullName").value("山田太郎"))
        .andExpect(jsonPath("$.fullNameKana").value("ヤマダタロウ"))
        .andExpect(jsonPath("$.departmentName").value("営業部"));
  }

  @Test
  void returnsProfileForTenantlessUser() throws Exception {
    // どのテナントにも所属しないユーザーでも、X-Tenant-Id なしで自身のプロフィールを取得できる(テナント非依存)。
    mockMvc
        .perform(get(PATH).with(authentication(tenantlessToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(tenantlessUserId))
        .andExpect(jsonPath("$.email").value("upt@example.com"))
        .andExpect(jsonPath("$.fullName").value("鈴木花子"))
        .andExpect(jsonPath("$.departmentName").value(nullValue()));
  }

  @Test
  void returnsProfile_withTenantHeader() throws Exception {
    // X-Tenant-Id を付与してもメンバーであれば 200。
    mockMvc
        .perform(
            get(PATH)
                .header("X-Tenant-Id", String.valueOf(tenantId))
                .with(authentication(memberToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(memberUserId));
  }

  @Test
  void unauthenticated_returns401() throws Exception {
    mockMvc.perform(get(PATH)).andExpect(status().isUnauthorized());
  }

  // --- helpers ---

  private void insertMembership(Long userId, Long tid) {
    em.createNativeQuery(
            "INSERT INTO user_tenants (user_id, tenant_id, role, status, joined_at)"
                + " VALUES (?,?,?,?,?)")
        .setParameter(1, userId)
        .setParameter(2, tid)
        .setParameter(3, "MEMBER")
        .setParameter(4, "ACTIVE")
        .setParameter(5, LocalDateTime.of(2026, 1, 1, 0, 0))
        .executeUpdate();
  }
}
