package xyz.dgz48.tasks.webapi.tenant.adapter.persistence;

import java.time.LocalDateTime;
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

  @Query(
      value =
          """
          SELECT COUNT(*) FROM tasks
          WHERE tenant_id = :tenantId AND deleted_at IS NULL
          """,
      nativeQuery = true)
  long countTasksByTenantId(@Param("tenantId") Long tenantId);

  @Query(
      value =
          """
          SELECT COUNT(*) FROM tenants
          WHERE status != 'DELETED'
          """,
      nativeQuery = true)
  long countNonDeletedTenants();

  @Query(
      value =
          """
          SELECT COUNT(*) FROM tenants
          WHERE status = 'ACTIVE'
          """,
      nativeQuery = true)
  long countActiveTenants();

  @Query(
      value =
          """
          SELECT COUNT(*) FROM tenants
          WHERE status = 'SUSPENDED'
          """,
      nativeQuery = true)
  long countSuspendedTenants();

  @Query(
      value =
          """
          SELECT COUNT(*) FROM users
          WHERE deleted_at IS NULL
          """,
      nativeQuery = true)
  long countTotalUsers();

  @Query(
      value =
          """
          SELECT COUNT(*) FROM tasks
          WHERE deleted_at IS NULL
          """,
      nativeQuery = true)
  long countTotalTasks();

  @Query(
      value =
          """
          SELECT COUNT(*) FROM tenants
          WHERE created_at >= :since
          """,
      nativeQuery = true)
  long countTenantsCreatedSince(@Param("since") LocalDateTime since);
}
