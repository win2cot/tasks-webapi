package xyz.dgz48.tasks.webapi.task.adapter.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface TaskStakeholderJpaRepository
    extends JpaRepository<TaskStakeholderJpaEntity, TaskStakeholderJpaEntityId> {

  // native 理由: モジュール境界越え（users テーブル JOIN — task モジュールから users 列を取得）
  @Query(
      value =
          """
          SELECT ts.user_id    AS userId,
                 u.full_name   AS fullName,
                 u.email       AS email,
                 ts.added_by   AS addedBy,
                 ab.full_name  AS addedByFullName,
                 ts.added_at   AS addedAt
          FROM task_stakeholders ts
          JOIN users u  ON ts.user_id  = u.id
          JOIN users ab ON ts.added_by = ab.id
          WHERE ts.task_id = :taskId AND ts.tenant_id = :tenantId
          ORDER BY ts.added_at ASC
          """,
      nativeQuery = true)
  List<StakeholderProjection> findWithUserInfoByTaskIdAndTenantId(
      @Param("taskId") Long taskId, @Param("tenantId") Long tenantId);

  List<TaskStakeholderJpaEntity> findByTaskId(Long taskId);

  @Override
  boolean existsById(TaskStakeholderJpaEntityId id);

  // tenant_id 明示: bulk DML は Filter 不適用(ADR-0010 §3)
  @Modifying
  @Query(
      "DELETE FROM TaskStakeholderJpaEntity ts"
          + " WHERE ts.taskId = :taskId AND ts.userId = :userId AND ts.tenantId = :tenantId")
  void deleteByTaskIdAndUserId(
      @Param("taskId") Long taskId, @Param("userId") Long userId, @Param("tenantId") Long tenantId);

  // tenant_id 明示: bulk DML は Filter 不適用(ADR-0010 §3)
  @Modifying
  @Query(
      "DELETE FROM TaskStakeholderJpaEntity ts"
          + " WHERE ts.taskId = :taskId AND ts.tenantId = :tenantId")
  int deleteAllByTaskIdAndTenantId(@Param("taskId") Long taskId, @Param("tenantId") Long tenantId);
}
