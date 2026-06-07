package xyz.dgz48.tasks.webapi.task.adapter.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface TaskStakeholderJpaRepository
    extends JpaRepository<TaskStakeholderJpaEntity, TaskStakeholderJpaEntityId> {

  /** ユーザー情報を JOIN した関係者一覧。テナントフィルタは native なので明示指定。 */
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

  @Query(
      value =
          "SELECT ts.user_id FROM task_stakeholders ts"
              + " WHERE ts.task_id = :taskId AND ts.tenant_id = :tenantId",
      nativeQuery = true)
  List<Long> findUserIdsByTaskIdAndTenantId(
      @Param("taskId") Long taskId, @Param("tenantId") Long tenantId);

  @Override
  boolean existsById(TaskStakeholderJpaEntityId id);

  @Modifying
  @Query(
      value =
          "DELETE FROM task_stakeholders"
              + " WHERE task_id = :taskId AND user_id = :userId AND tenant_id = :tenantId",
      nativeQuery = true)
  void deleteByTaskIdAndUserId(
      @Param("taskId") Long taskId, @Param("userId") Long userId, @Param("tenantId") Long tenantId);
}
