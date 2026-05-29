package xyz.dgz48.tasks.webapi.task;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import xyz.dgz48.tasks.webapi.MockJwtDecoderConfiguration;
import xyz.dgz48.tasks.webapi.TestcontainersConfiguration;
import xyz.dgz48.tasks.webapi.security.TasksAuthenticationToken;
import xyz.dgz48.tasks.webapi.security.TasksPrincipal;
import xyz.dgz48.tasks.webapi.task.adapter.persistence.TaskJpaEntity;
import xyz.dgz48.tasks.webapi.task.domain.Priority;
import xyz.dgz48.tasks.webapi.task.domain.TaskStatus;
import xyz.dgz48.tasks.webapi.tenant.Tenant;
import xyz.dgz48.tasks.webapi.user.User;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, MockJwtDecoderConfiguration.class})
class TaskCrossTenantIT {

  @Autowired MockMvc mockMvc;
  @Autowired EntityManager entityManager;
  @Autowired TransactionTemplate transactionTemplate;

  /** テナント A のタスクにテナント B ユーザーがアクセスすると 404 が返ること(§9.4 クロステナント漏洩テスト) */
  @Test
  void getTask_returns404_whenAccessedFromDifferentTenant() throws Exception {
    long[] ids = setupCrossTenantData();
    long taskId = ids[0];
    long tenantBId = ids[1];
    long userBId = ids[2];

    TasksPrincipal principalB =
        new TasksPrincipal(userBId, "sub-cross-b", "userb@example.com", "ユーザー B", "ユーザー ビー", null);
    TasksAuthenticationToken tokenB = new TasksAuthenticationToken(principalB, List.of());

    mockMvc
        .perform(
            get("/api/tasks/" + taskId)
                .header("X-Tenant-Id", tenantBId)
                .with(authentication(tokenB)))
        .andExpect(status().isNotFound());
  }

  /**
   * テストデータを committed トランザクションで作成し、{taskId, tenantBId, userBId} を返す。 MockMvc リクエストは別トランザクションで動作するため
   * class-level @Transactional は使わない。
   */
  private long[] setupCrossTenantData() {
    long seed = System.nanoTime();
    Long[] result =
        transactionTemplate.execute(
            txStatus -> {
              var tenantA = new Tenant("CT-A-" + seed, "テナント A");
              entityManager.persist(tenantA);

              var userA =
                  new User(
                      "sub-ct-a-" + seed,
                      "usera-ct-" + seed + "@example.com",
                      "ユーザー A",
                      "ユーザー エー",
                      null);
              entityManager.persist(userA);
              entityManager.flush();

              var task =
                  new TaskJpaEntity(
                      tenantA.getId(),
                      userA.getId(),
                      "テナントAのタスク",
                      null,
                      TaskStatus.NOT_STARTED,
                      Priority.MEDIUM,
                      LocalDate.of(2026, 12, 31),
                      userA.getId(),
                      userA.getId());
              entityManager.persist(task);

              var tenantB = new Tenant("CT-B-" + seed, "テナント B");
              entityManager.persist(tenantB);

              var userB =
                  new User(
                      "sub-ct-b-" + seed,
                      "userb-ct-" + seed + "@example.com",
                      "ユーザー B",
                      "ユーザー ビー",
                      null);
              entityManager.persist(userB);
              entityManager.flush();

              return new Long[] {task.getId(), tenantB.getId(), userB.getId()};
            });

    assert result != null;
    return new long[] {result[0], result[1], result[2]};
  }
}
