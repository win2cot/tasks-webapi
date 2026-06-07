package xyz.dgz48.tasks.webapi.task.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
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
import xyz.dgz48.tasks.webapi.security.adapter.web.TasksAuthenticationToken;
import xyz.dgz48.tasks.webapi.security.domain.TasksPrincipal;
import xyz.dgz48.tasks.webapi.tenant.adapter.persistence.TenantJpaEntity;
import xyz.dgz48.tasks.webapi.user.adapter.persistence.UserJpaEntity;

/**
 * POST /api/tasks タスク作成 API の統合テスト。
 *
 * <p>201 正常系 / 400 バリデーション / 401 未認証 / 403 テナント未選択 を検証する。 また、visibility=STAKEHOLDERS のときの
 * stakeholderUserIds 反映も確認する。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({
  TestcontainersConfiguration.class,
  MockJwtDecoderConfiguration.class,
  FixedClockConfiguration.class
})
class CreateTaskIT {

  @Autowired MockMvc mockMvc;
  @Autowired EntityManager em;
  @Autowired TransactionTemplate txTemplate;

  private Long userId;
  private Long otherUserId;
  private Long tenantId;
  private TasksAuthenticationToken authToken;

  @BeforeEach
  void setUp() {
    txTemplate.execute(
        ignored -> {
          var user =
              new UserJpaEntity(
                  "sub-ct-owner", "ct-owner@example.com", "作成テスト太郎", "サクセイテストタロウ", null);
          em.persist(user);

          var otherUser =
              new UserJpaEntity(
                  "sub-ct-other", "ct-other@example.com", "作成テスト次郎", "サクセイテストジロウ", null);
          em.persist(otherUser);
          em.flush();

          userId = user.getId();
          otherUserId = otherUser.getId();

          var principal =
              new TasksPrincipal(
                  userId, "sub-ct-owner", "ct-owner@example.com", "作成テスト太郎", "サクセイテストタロウ", null);
          SecurityContextHolder.getContext()
              .setAuthentication(new TasksAuthenticationToken(principal, List.of()));

          var tenant = new TenantJpaEntity("CT-1", "タスク作成テストテナント");
          em.persist(tenant);
          em.flush();
          tenantId = tenant.getId();

          em.createNativeQuery(
                  "INSERT INTO user_tenants (user_id, tenant_id, role, status, joined_at)"
                      + " VALUES (?,?,?,?,?)")
              .setParameter(1, userId)
              .setParameter(2, tenantId)
              .setParameter(3, "MEMBER")
              .setParameter(4, "ACTIVE")
              .setParameter(5, LocalDateTime.of(2026, 1, 1, 0, 0))
              .executeUpdate();

          em.createNativeQuery(
                  "INSERT INTO user_tenants (user_id, tenant_id, role, status, joined_at)"
                      + " VALUES (?,?,?,?,?)")
              .setParameter(1, otherUserId)
              .setParameter(2, tenantId)
              .setParameter(3, "MEMBER")
              .setParameter(4, "ACTIVE")
              .setParameter(5, LocalDateTime.of(2026, 1, 1, 0, 0))
              .executeUpdate();

          return null;
        });

    SecurityContextHolder.clearContext();

    var principal =
        new TasksPrincipal(
            userId, "sub-ct-owner", "ct-owner@example.com", "作成テスト太郎", "サクセイテストタロウ", null);
    authToken = new TasksAuthenticationToken(principal, List.of());
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
    if (tenantId == null) {
      return;
    }
    txTemplate.execute(
        ignored -> {
          em.createNativeQuery("DELETE FROM task_stakeholders WHERE tenant_id = ?")
              .setParameter(1, tenantId)
              .executeUpdate();
          em.createNativeQuery("DELETE FROM tasks WHERE tenant_id = ?")
              .setParameter(1, tenantId)
              .executeUpdate();
          em.createNativeQuery("DELETE FROM user_tenants WHERE tenant_id = ?")
              .setParameter(1, tenantId)
              .executeUpdate();
          em.createNativeQuery("DELETE FROM tenants WHERE id = ?")
              .setParameter(1, tenantId)
              .executeUpdate();
          em.createNativeQuery("DELETE FROM users WHERE id IN (?,?)")
              .setParameter(1, userId)
              .setParameter(2, otherUserId)
              .executeUpdate();
          return null;
        });
  }

  @Nested
  class SuccessCase {

    @Test
    void createTask_returns201_withTenantVisibility() throws Exception {
      mockMvc
          .perform(
              post("/api/tasks")
                  .header("X-Tenant-Id", String.valueOf(tenantId))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
                      {
                        "title": "テストタスク",
                        "priority": "MEDIUM",
                        "visibility": "TENANT",
                        "dueDate": "2026-12-31"
                      }
                      """)
                  .with(authentication(authToken)))
          .andExpect(status().isCreated())
          .andExpect(header().exists("Location"))
          .andExpect(jsonPath("$.id").isNumber())
          .andExpect(jsonPath("$.title").value("テストタスク"))
          .andExpect(jsonPath("$.status").value("NOT_STARTED"))
          .andExpect(jsonPath("$.priority").value("MEDIUM"))
          .andExpect(jsonPath("$.visibility").value("TENANT"))
          .andExpect(jsonPath("$.dueDate").value("2026-12-31"));
    }

    @Test
    void createTask_sets_tenantId_from_context() throws Exception {
      var result =
          mockMvc
              .perform(
                  post("/api/tasks")
                      .header("X-Tenant-Id", String.valueOf(tenantId))
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(
                          """
                          {
                            "title": "テナントID確認タスク",
                            "priority": "LOW",
                            "visibility": "TENANT",
                            "dueDate": "2026-12-31"
                          }
                          """)
                      .with(authentication(authToken)))
              .andExpect(status().isCreated())
              .andReturn();

      String body = result.getResponse().getContentAsString();
      assertThat(body).contains("\"tenantId\":" + tenantId);
    }

    @Test
    void createTask_withStakeholderVisibility_addsStakeholders() throws Exception {
      var result =
          mockMvc
              .perform(
                  post("/api/tasks")
                      .header("X-Tenant-Id", String.valueOf(tenantId))
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(
                          String.format(
                              """
                              {
                                "title": "STAKEHOLDERS タスク",
                                "priority": "HIGH",
                                "visibility": "STAKEHOLDERS",
                                "dueDate": "2026-12-31",
                                "stakeholderUserIds": [%d]
                              }
                              """,
                              otherUserId))
                      .with(authentication(authToken)))
              .andExpect(status().isCreated())
              .andExpect(jsonPath("$.visibility").value("STAKEHOLDERS"))
              .andReturn();

      String body = result.getResponse().getContentAsString();
      // task ID を取り出してステークホルダーが登録されたか確認
      String taskIdStr = body.replaceAll(".*\"id\":(\\d+).*", "$1");
      Long taskId = Long.parseLong(taskIdStr);

      txTemplate.execute(
          ignored -> {
            List<?> rows =
                em.createNativeQuery(
                        "SELECT user_id FROM task_stakeholders WHERE task_id = ? AND tenant_id = ?")
                    .setParameter(1, taskId)
                    .setParameter(2, tenantId)
                    .getResultList();
            assertThat(rows).hasSize(1);
            return null;
          });
    }

    @Test
    void createTask_withDescription_andAssignee() throws Exception {
      mockMvc
          .perform(
              post("/api/tasks")
                  .header("X-Tenant-Id", String.valueOf(tenantId))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      String.format(
                          """
                          {
                            "title": "詳細付きタスク",
                            "description": "これは説明です",
                            "priority": "HIGH",
                            "visibility": "PRIVATE",
                            "assigneeId": %d,
                            "dueDate": "2026-12-31"
                          }
                          """,
                          otherUserId))
                  .with(authentication(authToken)))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.visibility").value("PRIVATE"))
          .andExpect(jsonPath("$.assigneeId").value(otherUserId));
    }
  }

  @Nested
  class ValidationError {

    @Test
    void createTask_returns400_whenTitleIsBlank() throws Exception {
      mockMvc
          .perform(
              post("/api/tasks")
                  .header("X-Tenant-Id", String.valueOf(tenantId))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
                      {
                        "title": "",
                        "priority": "MEDIUM",
                        "visibility": "TENANT",
                        "dueDate": "2026-12-31"
                      }
                      """)
                  .with(authentication(authToken)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("E_VALIDATION"));
    }

    @Test
    void createTask_returns400_whenDueDateIsMissing() throws Exception {
      mockMvc
          .perform(
              post("/api/tasks")
                  .header("X-Tenant-Id", String.valueOf(tenantId))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
                      {
                        "title": "期限なしタスク",
                        "priority": "MEDIUM",
                        "visibility": "TENANT"
                      }
                      """)
                  .with(authentication(authToken)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("E_VALIDATION"));
    }

    @Test
    void createTask_returns400_whenPriorityIsMissing() throws Exception {
      mockMvc
          .perform(
              post("/api/tasks")
                  .header("X-Tenant-Id", String.valueOf(tenantId))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
                      {
                        "title": "優先度なしタスク",
                        "visibility": "TENANT",
                        "dueDate": "2026-12-31"
                      }
                      """)
                  .with(authentication(authToken)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("E_VALIDATION"));
    }

    @Test
    void createTask_returns400_whenTitleExceedsMaxLength() throws Exception {
      String longTitle = "A".repeat(101);
      mockMvc
          .perform(
              post("/api/tasks")
                  .header("X-Tenant-Id", String.valueOf(tenantId))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      String.format(
                          """
                          {
                            "title": "%s",
                            "priority": "MEDIUM",
                            "visibility": "TENANT",
                            "dueDate": "2026-12-31"
                          }
                          """,
                          longTitle))
                  .with(authentication(authToken)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("E_VALIDATION"));
    }
  }

  @Nested
  class Authentication {

    @Test
    void createTask_returns401_whenUnauthenticated() throws Exception {
      mockMvc
          .perform(
              post("/api/tasks")
                  .header("X-Tenant-Id", String.valueOf(tenantId))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
                      {
                        "title": "テストタスク",
                        "priority": "MEDIUM",
                        "visibility": "TENANT",
                        "dueDate": "2026-12-31"
                      }
                      """))
          .andExpect(status().isUnauthorized());
    }
  }

  @Nested
  class TenantIsolation {

    @Test
    void createTask_sets_tenantId_from_xTenantIdHeader() throws Exception {
      var result =
          mockMvc
              .perform(
                  post("/api/tasks")
                      .header("X-Tenant-Id", String.valueOf(tenantId))
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(
                          """
                          {
                            "title": "テナント分離テスト",
                            "priority": "LOW",
                            "visibility": "TENANT",
                            "dueDate": "2026-12-31"
                          }
                          """)
                      .with(authentication(authToken)))
              .andExpect(status().isCreated())
              .andReturn();

      String body = result.getResponse().getContentAsString();
      assertThat(body).contains("\"tenantId\":" + tenantId);
    }
  }
}
