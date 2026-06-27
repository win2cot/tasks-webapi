package xyz.dgz48.tasks.webapi.tenant.adapter.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import xyz.dgz48.tasks.webapi.tenant.domain.TenantSummaryInfo;
import xyz.dgz48.tasks.webapi.tenant.domain.UserTenantStatus;

interface UserTenantJpaRepository extends JpaRepository<UserTenantJpaEntity, UserTenantId> {

  Optional<UserTenantJpaEntity> findByIdUserIdAndIdTenantIdAndStatus(
      Long userId, Long tenantId, UserTenantStatus status);

  /** status 問わず、当該ユーザー×テナントの user_tenants 行が存在するか(招待の重複検出に使用)。 */
  boolean existsByIdUserIdAndIdTenantId(Long userId, Long tenantId);

  List<UserTenantJpaEntity> findByIdUserIdAndStatusOrderByJoinedAtAsc(
      Long userId, UserTenantStatus status);

  @Query(
      """
      SELECT new xyz.dgz48.tasks.webapi.tenant.domain.TenantSummaryInfo(
          ut.id.tenantId, t.code, t.name, ut.role)
      FROM UserTenantJpaEntity ut
      JOIN TenantJpaEntity t ON t.id = ut.id.tenantId
      WHERE ut.id.userId = :userId AND ut.status = :status
      ORDER BY ut.joinedAt ASC
      """)
  List<TenantSummaryInfo> findActiveMembershipsWithTenantDetail(
      @Param("userId") Long userId, @Param("status") UserTenantStatus status);

  List<UserTenantJpaEntity> findByIdTenantIdOrderByJoinedAtAsc(Long tenantId);
}
