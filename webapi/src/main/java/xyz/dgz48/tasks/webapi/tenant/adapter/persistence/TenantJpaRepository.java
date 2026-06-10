package xyz.dgz48.tasks.webapi.tenant.adapter.persistence;

import java.time.LocalDateTime;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import xyz.dgz48.tasks.webapi.tenant.domain.TenantStatus;

interface TenantJpaRepository extends JpaRepository<TenantJpaEntity, Long> {

  @Query(
      """
      SELECT t FROM TenantJpaEntity t
      WHERE (:status IS NULL OR t.status = :status)
        AND (:keyword IS NULL OR t.name LIKE %:keyword%)
      """)
  Page<TenantJpaEntity> findAllFiltered(
      @Param("status") @Nullable TenantStatus status,
      @Param("keyword") @Nullable String keyword,
      Pageable pageable);

  @Query(
      """
      SELECT COUNT(ut) FROM UserTenantJpaEntity ut
      WHERE ut.id.tenantId = :tenantId
      """)
  long countUsersByTenantId(@Param("tenantId") Long tenantId);

  // native 理由: モジュール境界越え（他モジュールのテーブル参照）
  @Query(
      value =
          """
          SELECT COUNT(*) FROM tasks
          WHERE tenant_id = :tenantId AND deleted_at IS NULL
          """,
      nativeQuery = true)
  long countTasksByTenantId(@Param("tenantId") Long tenantId);

  @Query(
      "SELECT COUNT(t) FROM TenantJpaEntity t"
          + " WHERE t.status <> xyz.dgz48.tasks.webapi.tenant.domain.TenantStatus.DELETED")
  long countNonDeletedTenants();

  @Query(
      "SELECT COUNT(t) FROM TenantJpaEntity t"
          + " WHERE t.status = xyz.dgz48.tasks.webapi.tenant.domain.TenantStatus.ACTIVE")
  long countActiveTenants();

  @Query(
      "SELECT COUNT(t) FROM TenantJpaEntity t"
          + " WHERE t.status = xyz.dgz48.tasks.webapi.tenant.domain.TenantStatus.SUSPENDED")
  long countSuspendedTenants();

  // native 理由: モジュール境界越え（他モジュールのテーブル参照）
  @Query(
      value =
          """
          SELECT COUNT(*) FROM users
          WHERE deleted_at IS NULL
          """,
      nativeQuery = true)
  long countTotalUsers();

  // native 理由: モジュール境界越え（他モジュールのテーブル参照）
  @Query(
      value =
          """
          SELECT COUNT(*) FROM tasks
          WHERE deleted_at IS NULL
          """,
      nativeQuery = true)
  long countTotalTasks();

  @Query(
      "SELECT COUNT(t) FROM TenantJpaEntity t"
          + " WHERE t.createdAt >= :since"
          + " AND t.status <> xyz.dgz48.tasks.webapi.tenant.domain.TenantStatus.DELETED")
  long countTenantsCreatedSince(@Param("since") LocalDateTime since);

  @Query(
      """
      SELECT ut.id.tenantId, COUNT(ut) FROM UserTenantJpaEntity ut
      WHERE ut.id.tenantId IN :tenantIds
      GROUP BY ut.id.tenantId
      """)
  List<Object[]> countUsersByTenantIds(@Param("tenantIds") List<Long> tenantIds);

  // native 理由: モジュール境界越え（他モジュールのテーブル参照）
  @Query(
      value =
          """
          SELECT tenant_id, COUNT(*) FROM tasks
          WHERE tenant_id IN :tenantIds AND deleted_at IS NULL
          GROUP BY tenant_id
          """,
      nativeQuery = true)
  List<Object[]> countTasksByTenantIds(@Param("tenantIds") List<Long> tenantIds);
}
