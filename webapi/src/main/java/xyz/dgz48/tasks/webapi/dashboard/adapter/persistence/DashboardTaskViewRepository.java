package xyz.dgz48.tasks.webapi.dashboard.adapter.persistence;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * ダッシュボード集計の JPQL リポジトリ(読み取り専用)。
 *
 * <p>テナント分離は Hibernate Filter "tenantFilter"(ADR-0010)が JPQL に自動付与する({@link DashboardTaskView} /
 * {@link DashboardStakeholderView} がいずれも {@code TenantFilteredEntity} を継承)。本リポジトリは visibility 認可と
 * 当日スコープの絞り込みのみを担う。
 *
 * <p>4 セクションは抽出条件が排他で、いずれも <b>所有者または担当者</b>(owner OR assignee)に限定される(screen-flow.md §5.1)。 priority
 * の降順は CASE 式で HIGH&gt;MEDIUM&gt;LOW を表現する。
 */
interface DashboardTaskViewRepository extends Repository<DashboardTaskView, Long> {

  /** 期限超過: dueDate &lt; today AND status ≠ DONE。ソート: dueDate ASC → priority DESC。 */
  @Query(
      """
      SELECT t FROM DashboardTaskView t
      WHERE t.deletedAt IS NULL
        AND (t.ownerId = :userId OR t.assigneeId = :userId)
        AND t.dueDate < :today
        AND t.status <> 'DONE'
      ORDER BY t.dueDate ASC,
               CASE t.priority WHEN 'HIGH' THEN 3 WHEN 'MEDIUM' THEN 2 ELSE 1 END DESC
      """)
  List<DashboardTaskView> findOverdue(
      @Param("userId") Long userId, @Param("today") LocalDate today);

  /** 今日やること: dueDate = today AND status ≠ DONE。ソート: priority DESC → createdAt ASC。 */
  @Query(
      """
      SELECT t FROM DashboardTaskView t
      WHERE t.deletedAt IS NULL
        AND (t.ownerId = :userId OR t.assigneeId = :userId)
        AND t.dueDate = :today
        AND t.status <> 'DONE'
      ORDER BY CASE t.priority WHEN 'HIGH' THEN 3 WHEN 'MEDIUM' THEN 2 ELSE 1 END DESC,
               t.createdAt ASC
      """)
  List<DashboardTaskView> findToday(@Param("userId") Long userId, @Param("today") LocalDate today);

  /** これから: today &lt; dueDate ≤ upperBound AND status ≠ DONE。ソート: dueDate ASC → priority DESC。 */
  @Query(
      """
      SELECT t FROM DashboardTaskView t
      WHERE t.deletedAt IS NULL
        AND (t.ownerId = :userId OR t.assigneeId = :userId)
        AND t.dueDate > :today
        AND t.dueDate <= :upperBound
        AND t.status <> 'DONE'
      ORDER BY t.dueDate ASC,
               CASE t.priority WHEN 'HIGH' THEN 3 WHEN 'MEDIUM' THEN 2 ELSE 1 END DESC
      """)
  List<DashboardTaskView> findUpcoming(
      @Param("userId") Long userId,
      @Param("today") LocalDate today,
      @Param("upperBound") LocalDate upperBound);

  /** 今日やったこと: status = DONE AND completedAt が当日(JST)。ソート: completedAt DESC。 */
  @Query(
      """
      SELECT t FROM DashboardTaskView t
      WHERE t.deletedAt IS NULL
        AND (t.ownerId = :userId OR t.assigneeId = :userId)
        AND t.status = 'DONE'
        AND t.completedAt >= :startOfDay
        AND t.completedAt < :endOfDay
      ORDER BY t.completedAt DESC
      """)
  List<DashboardTaskView> findCompletedToday(
      @Param("userId") Long userId,
      @Param("startOfDay") LocalDateTime startOfDay,
      @Param("endOfDay") LocalDateTime endOfDay);

  /**
   * summary 集計対象(§6.2.1 の 3 役割評価で参照可能なタスク集合、ADR-0005)を軽量射影で取得する。
   *
   * <p>認可述語(visibility 3 役割評価)を 1 箇所に集約し、件数系・ステータス別・優先度別の各指標は adapter 側で算出する。 PRIVATE
   * は所有者・担当者のみ、STAKEHOLDERS は所有者・担当者・関係者のみ、TENANT はテナント全員(= Filter 通過全員)が対象。
   */
  @Query(
      """
      SELECT new xyz.dgz48.tasks.webapi.dashboard.adapter.persistence.DashboardSummaryRow(
        t.status, t.priority, t.dueDate, t.completedAt, t.ownerId)
      FROM DashboardTaskView t
      WHERE t.deletedAt IS NULL
        AND (
          t.visibility = 'TENANT'
          OR t.ownerId = :userId
          OR t.assigneeId = :userId
          OR (t.visibility = 'STAKEHOLDERS'
              AND EXISTS (SELECT 1 FROM DashboardStakeholderView s
                          WHERE s.taskId = t.id AND s.userId = :userId))
        )
      """)
  List<DashboardSummaryRow> findVisibleSummaryRows(@Param("userId") Long userId);
}
