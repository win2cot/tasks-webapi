package xyz.dgz48.tasks.webapi.task.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.hibernate.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import xyz.dgz48.tasks.webapi.MockJwtDecoderConfiguration;
import xyz.dgz48.tasks.webapi.TestcontainersConfiguration;
import xyz.dgz48.tasks.webapi.security.adapter.web.TasksAuthenticationToken;
import xyz.dgz48.tasks.webapi.security.domain.TasksPrincipal;
import xyz.dgz48.tasks.webapi.shared.domain.TenantContext;
import xyz.dgz48.tasks.webapi.task.domain.Priority;
import xyz.dgz48.tasks.webapi.task.domain.TaskStatus;
import xyz.dgz48.tasks.webapi.tenant.adapter.persistence.TenantJpaEntity;
import xyz.dgz48.tasks.webapi.user.adapter.persistence.UserJpaEntity;

/**
 * Hibernate Filter "tenantFilter" + TenantContext によるクロステナント越境遮断を、機能実装に先行して結合テストで担保する IT(R-05 /
 * ADR-0010)。
 *
 * <p>越境アクセスが SELECT 系の各経路で fail-closed(別テナント行が一切返らない)になることを Testcontainers MySQL 8.4 で検証する。検証対象の経路:
 *
 * <ul>
 *   <li><strong>PK ロード</strong>: {@code TaskJpaRepository#findById}(JPQL override) — Filter
 *       が乗るため別テナント行は不可視。 素の {@code EntityManager#find}(PK 直ロード)は Filter が乗らないことも併せて文書化する(JPQL
 *       override が必要な理由)。
 *   <li><strong>メソッド名導出クエリ</strong>: {@code TaskStakeholderJpaRepository#findByTaskId} /
 *       JpaRepository 既定の {@code findAll} — 導出クエリ(JPQL)にも Filter が乗るため別テナント行は除外される。
 *   <li><strong>bulk DML</strong>: {@code @Modifying} DELETE は規約上 {@code tenant_id} を明示絞り込みする
 *       (ADR-0010 §3)。別テナントの {@code tenant_id} を渡した bulk DML は 0 行で fail-closed。
 * </ul>
 *
 * <p>HTTP 層の越境応答(参照=404 / 更新=403)は {@code TaskCrossTenantIT} および {@code
 * CrossTenantWriteForbiddenIT} で検証する。本 IT は永続化層の遮断プリミティブに集中する。
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, MockJwtDecoderConfiguration.class})
@Transactional
class TenantFilterFailClosedIT {

  @Autowired EntityManager em;
  @Autowired TaskJpaRepository taskJpaRepository;
  @Autowired TaskStakeholderJpaRepository taskStakeholderJpaRepository;

  private Long tenantAId;
  private Long tenantBId;
  private Long taskAId;
  private Long taskBId;
  private Long stakeholderUserId;

  @BeforeEach
  void setUp() {
    // 監査列(created_by 等)の AuditorAware 解決のため SecurityContext を確立する。
    var auditor =
        new UserJpaEntity(
            "sub-fc-auditor", "fc-auditor@example.com", "遮断テスト監査", "シャダンテストカンサ", null);
    em.persist(auditor);

    var stakeholderUser =
        new UserJpaEntity("sub-fc-stakeholder", "fc-stake@example.com", "関係者", "カンケイシャ", null);
    em.persist(stakeholderUser);
    em.flush();
    stakeholderUserId = stakeholderUser.getId();

    var principal =
        new TasksPrincipal(
            auditor.getId(),
            "sub-fc-auditor",
            "fc-auditor@example.com",
            "遮断テスト監査",
            "シャダンテストカンサ",
            null);
    SecurityContextHolder.getContext()
        .setAuthentication(new TasksAuthenticationToken(principal, List.of()));

    var tenantA = new TenantJpaEntity("FC-A", "遮断テナントA");
    var tenantB = new TenantJpaEntity("FC-B", "遮断テナントB");
    em.persist(tenantA);
    em.persist(tenantB);
    em.flush();
    tenantAId = tenantA.getId();
    tenantBId = tenantB.getId();

    // 業務テーブルへの INSERT 中に StatementInspector が「TenantContext 未設定での tenant-filtered
    // テーブルアクセス」を誤検知しないよう、TenantContext を確立してから永続化する(本番と同条件)。
    TenantContext.set(tenantAId);

    var taskA = newTask(tenantAId, auditor.getId(), "テナントAのタスク");
    var taskB = newTask(tenantBId, auditor.getId(), "テナントBのタスク");
    em.persist(taskA);
    em.persist(taskB);
    em.flush();
    taskAId = taskA.getId();
    taskBId = taskB.getId();

    var now = LocalDateTime.of(2026, 1, 1, 0, 0);
    em.persist(
        new TaskStakeholderJpaEntity(taskAId, stakeholderUserId, tenantAId, auditor.getId(), now));
    em.persist(
        new TaskStakeholderJpaEntity(taskBId, stakeholderUserId, tenantBId, auditor.getId(), now));
    em.flush();
    em.clear();
  }

  @AfterEach
  void tearDown() {
    em.unwrap(Session.class).disableFilter("tenantFilter");
    TenantContext.clear();
    SecurityContextHolder.clearContext();
  }

  private TaskJpaEntity newTask(Long tenantId, Long ownerId, String title) {
    return new TaskJpaEntity(
        tenantId,
        ownerId,
        title,
        null,
        TaskStatus.NOT_STARTED,
        Priority.MEDIUM,
        LocalDate.of(2026, 12, 31));
  }

  private void enableFilterFor(Long tenantId) {
    em.unwrap(Session.class).enableFilter("tenantFilter").setParameter("tenantId", tenantId);
  }

  @Nested
  class PkLoadRoute {

    @Test
    void findByIdViaJpqlOverride_blocksCrossTenant() {
      enableFilterFor(tenantAId);

      // 別テナント(B)の PK ロードは空(参照 404 相当の fail-closed)
      assertThat(taskJpaRepository.findById(taskBId)).isEmpty();
      // 自テナント(A)の PK ロードは可視
      assertThat(taskJpaRepository.findById(taskAId)).isPresent();
    }

    @Test
    void rawEntityManagerFind_bypassesFilter_documentsWhyOverrideIsNeeded() {
      enableFilterFor(tenantAId);

      // Hibernate Filter は EntityManager#find(PK 直ロード)には適用されない。
      // このため TaskJpaRepository#findById は JPQL override で再定義され、Filter を乗せている(ADR-0010)。
      // 本アサーションはその限界(= JPQL override が fail-closed の前提であること)を文書化する。
      assertThat(em.find(TaskJpaEntity.class, taskBId)).isNotNull();
    }
  }

  @Nested
  class DerivedQueryRoute {

    @Test
    void findByTaskId_blocksCrossTenant() {
      enableFilterFor(tenantAId);

      // 別テナント(B)のタスクに紐づく関係者行は導出クエリでも一切返らない
      assertThat(taskStakeholderJpaRepository.findByTaskId(taskBId)).isEmpty();
      // 自テナント(A)の関係者行は取得できる
      assertThat(taskStakeholderJpaRepository.findByTaskId(taskAId))
          .singleElement()
          .satisfies(s -> assertThat(s.getUserId()).isEqualTo(stakeholderUserId));
    }

    @Test
    void jpaRepositoryFindAll_excludesCrossTenant() {
      enableFilterFor(tenantAId);

      // JpaRepository 既定の findAll(JPQL)にも Filter が乗り、別テナント行は除外される
      assertThat(taskJpaRepository.findAll())
          .extracting(TaskJpaEntity::getTenantId)
          .containsOnly(tenantAId);
    }
  }

  @Nested
  class BulkDmlRoute {

    @Test
    void bulkDelete_withForeignTenantId_affectsZeroRows() {
      enableFilterFor(tenantAId);

      // 別テナント(B)の tenant_id を渡した bulk DELETE は対象 0 行(明示絞り込みによる fail-closed)
      int deletedWithForeignTenant =
          taskStakeholderJpaRepository.deleteAllByTaskIdAndTenantId(taskAId, tenantBId);
      assertThat(deletedWithForeignTenant).isZero();

      // 正しい tenant_id を渡せば削除される
      int deletedWithOwnTenant =
          taskStakeholderJpaRepository.deleteAllByTaskIdAndTenantId(taskAId, tenantAId);
      assertThat(deletedWithOwnTenant).isEqualTo(1);
    }
  }
}
