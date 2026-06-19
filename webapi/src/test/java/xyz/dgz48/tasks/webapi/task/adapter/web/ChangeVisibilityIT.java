package xyz.dgz48.tasks.webapi.task.adapter.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import xyz.dgz48.tasks.webapi.FixedClockConfiguration;
import xyz.dgz48.tasks.webapi.MockJwtDecoderConfiguration;
import xyz.dgz48.tasks.webapi.TestcontainersConfiguration;
import xyz.dgz48.tasks.webapi.security.adapter.web.TasksAuthenticationToken;
import xyz.dgz48.tasks.webapi.security.domain.TasksPrincipal;
import xyz.dgz48.tasks.webapi.shared.domain.TenantContext;
import xyz.dgz48.tasks.webapi.task.adapter.persistence.TaskJpaEntity;
import xyz.dgz48.tasks.webapi.task.domain.Priority;
import xyz.dgz48.tasks.webapi.task.domain.TaskStatus;
import xyz.dgz48.tasks.webapi.tenant.adapter.persistence.TenantJpaEntity;
import xyz.dgz48.tasks.webapi.user.adapter.persistence.UserJpaEntity;

@SpringBootTest
@AutoConfigureMockMvc
@Import({
  TestcontainersConfiguration.class,
  MockJwtDecoderConfiguration.class,
  FixedClockConfiguration.class
})
class ChangeVisibilityIT {

  @Autowired MockMvc mockMvc;
  @Autowired EntityManager em;
  @Autowired TransactionTemplate txTemplate;

  private Long ownerId;
  private Long memberId;
  private Long tenantId;
  private Long taskId;
  private TasksAuthenticationToken ownerToken;
  private TasksAuthenticationToken memberToken;

  @BeforeEach
  void setUp() {
    txTemplate.execute(
        ignored -> {
          var owner =
              new UserJpaEntity("sub-vis-own", "vis-own@example.com", "可視性 太郎", "カシセイ タロウ", null);
          em.persist(owner);
          var member =
              new UserJpaEntity("sub-vis-mem", "vis-mem@example.com", "一般 次郎", "イッパン ジロウ", null);
          em.persist(member);
          em.flush();
          ownerId = owner.getId();
          memberId = member.getId();

          var principal =
              new TasksPrincipal(
                  ownerId, "sub-vis-own", "vis-own@example.com", "可視性 太郎", "カシセイ タロウ", null);
          SecurityContextHolder.getContext()
              .setAuthentication(new TasksAuthenticationToken(principal, List.of()));

          var tenant = new TenantJpaEntity("VIS-IT-1", "可視性IT");
          em.persist(tenant);
          em.flush();
          tenantId = tenant.getId();

          for (Long uid : List.of(ownerId, memberId)) {
            em.createNativeQuery(
                    "INSERT INTO user_tenants"
                        + " (user_id, tenant_id, role, status, joined_at) VALUES (?,?,?,?,?)")
                .setParameter(1, uid)
                .setParameter(2, tenantId)
                .setParameter(3, "MEMBER")
                .setParameter(4, "ACTIVE")
                .setParameter(5, LocalDateTime.of(2026, 1, 1, 0, 0))
                .executeUpdate();
          }

          var task =
              new TaskJpaEntity(
                  tenantId,
                  ownerId,
                  "可視性テスト",
                  null,
                  TaskStatus.NOT_STARTED,
                  Priority.MEDIUM,
                  LocalDate.of(2026, 12, 31));
          em.persist(task);
          em.flush();
          taskId = task.getId();

          return null;
        });

    SecurityContextHolder.clearContext();

    var ownerPrincipal =
        new TasksPrincipal(
            ownerId, "sub-vis-own", "vis-own@example.com", "可視性 太郎", "カシセイ タロウ", null);
    ownerToken = new TasksAuthenticationToken(ownerPrincipal, List.of());

    var memberPrincipal =
        new TasksPrincipal(
            memberId, "sub-vis-mem", "vis-mem@example.com", "一般 次郎", "イッパン ジロウ", null);
    memberToken = new TasksAuthenticationToken(memberPrincipal, List.of());
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
    if (ownerId == null) {
      return;
    }
    TenantContext.set(tenantId);
    try {
      txTemplate.execute(
          ignored -> {
            em.createNativeQuery("DELETE FROM audit_logs WHERE tenant_id = ?")
                .setParameter(1, tenantId)
                .executeUpdate();
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
            em.createNativeQuery("DELETE FROM users WHERE id = ? OR id = ?")
                .setParameter(1, ownerId)
                .setParameter(2, memberId)
                .executeUpdate();
            return null;
          });
    } finally {
      TenantContext.clear();
    }
  }

  @Test
  void changeVisibility_returns200_andUpdatesVisibility() throws Exception {
    mockMvc
        .perform(
            patch("/api/tasks/" + taskId + "/visibility")
                .header("X-Tenant-Id", String.valueOf(tenantId))
                .header(HttpHeaders.IF_MATCH, "W/\"0\"")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"visibility\":\"PRIVATE\"}")
                .with(authentication(ownerToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.visibility").value("PRIVATE"));
  }

  @Test
  void changeVisibility_returns400_whenIfMatchHeaderMissing() throws Exception {
    mockMvc
        .perform(
            patch("/api/tasks/" + taskId + "/visibility")
                .header("X-Tenant-Id", String.valueOf(tenantId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"visibility\":\"PRIVATE\"}")
                .with(authentication(ownerToken)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("E_VALIDATION"));
  }

  @Test
  void changeVisibility_returns412_whenIfMatchVersionStale() throws Exception {
    mockMvc
        .perform(
            patch("/api/tasks/" + taskId + "/visibility")
                .header("X-Tenant-Id", String.valueOf(tenantId))
                .header(HttpHeaders.IF_MATCH, "W/\"99\"")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"visibility\":\"PRIVATE\"}")
                .with(authentication(ownerToken)))
        .andExpect(status().isPreconditionFailed())
        .andExpect(jsonPath("$.code").value("E_PRECONDITION_FAILED"));
  }

  @Test
  void changeVisibility_returns403_whenNotOwner() throws Exception {
    mockMvc
        .perform(
            patch("/api/tasks/" + taskId + "/visibility")
                .header("X-Tenant-Id", String.valueOf(tenantId))
                .header(HttpHeaders.IF_MATCH, "W/\"0\"")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"visibility\":\"PRIVATE\"}")
                .with(authentication(memberToken)))
        .andExpect(status().isForbidden());
  }

  @Test
  void changeVisibility_toPrivate_memberCannotSeeTask() throws Exception {
    // TENANT → PRIVATE: 担当者でない member は参照不可になる
    mockMvc
        .perform(
            patch("/api/tasks/" + taskId + "/visibility")
                .header("X-Tenant-Id", String.valueOf(tenantId))
                .header(HttpHeaders.IF_MATCH, "W/\"0\"")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"visibility\":\"PRIVATE\"}")
                .with(authentication(ownerToken)))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            get("/api/tasks/" + taskId)
                .header("X-Tenant-Id", String.valueOf(tenantId))
                .with(authentication(memberToken)))
        .andExpect(status().isNotFound());
  }

  @Test
  void changeVisibility_toStakeholders_withStakeholderIds_replacesStakeholders() throws Exception {
    mockMvc
        .perform(
            patch("/api/tasks/" + taskId + "/visibility")
                .header("X-Tenant-Id", String.valueOf(tenantId))
                .header(HttpHeaders.IF_MATCH, "W/\"0\"")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"visibility\":\"STAKEHOLDERS\",\"stakeholderUserIds\":[" + memberId + "]}")
                .with(authentication(ownerToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.visibility").value("STAKEHOLDERS"));

    // 関係者として登録された member はタスクを参照できる
    mockMvc
        .perform(
            get("/api/tasks/" + taskId)
                .header("X-Tenant-Id", String.valueOf(tenantId))
                .with(authentication(memberToken)))
        .andExpect(status().isOk());
  }

  @Test
  void changeVisibility_returns400_whenVisibilityMissing() throws Exception {
    mockMvc
        .perform(
            patch("/api/tasks/" + taskId + "/visibility")
                .header("X-Tenant-Id", String.valueOf(tenantId))
                .header(HttpHeaders.IF_MATCH, "W/\"0\"")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .with(authentication(ownerToken)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("E_VALIDATION"));
  }

  @Test
  void changeVisibility_returns404_whenTaskNotFound() throws Exception {
    mockMvc
        .perform(
            patch("/api/tasks/99999999/visibility")
                .header("X-Tenant-Id", String.valueOf(tenantId))
                .header(HttpHeaders.IF_MATCH, "W/\"0\"")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"visibility\":\"PRIVATE\"}")
                .with(authentication(ownerToken)))
        .andExpect(status().isNotFound());
  }

  @Test
  void changeVisibility_toTenant_purgedStakeholdersStillSeeTask() throws Exception {
    // まず STAKEHOLDERS に変更して member を関係者登録
    mockMvc
        .perform(
            patch("/api/tasks/" + taskId + "/visibility")
                .header("X-Tenant-Id", String.valueOf(tenantId))
                .header(HttpHeaders.IF_MATCH, "W/\"0\"")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"visibility\":\"STAKEHOLDERS\",\"stakeholderUserIds\":[" + memberId + "]}")
                .with(authentication(ownerToken)))
        .andExpect(status().isOk());

    // TENANT に戻す(関係者レコードは保持される設計だが、TENANT なので全員参照可)
    mockMvc
        .perform(
            patch("/api/tasks/" + taskId + "/visibility")
                .header("X-Tenant-Id", String.valueOf(tenantId))
                .header(HttpHeaders.IF_MATCH, "W/\"1\"")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"visibility\":\"TENANT\"}")
                .with(authentication(ownerToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.visibility").value("TENANT"));

    mockMvc
        .perform(
            get("/api/tasks/" + taskId)
                .header("X-Tenant-Id", String.valueOf(tenantId))
                .with(authentication(memberToken)))
        .andExpect(status().isOk());
  }
}
