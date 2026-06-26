package xyz.dgz48.tasks.webapi.audit.adapter.persistence;

import java.time.LocalDateTime;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface AuditLogJpaRepository extends JpaRepository<AuditLogJpaEntity, Long> {

  /** テナント連鎖(chain_key = tenant_id)の生存行を chain_seq 昇順で返す(B-05 検証用)。 */
  List<AuditLogJpaEntity> findByTenantIdOrderByChainSeqAsc(long tenantId);

  /** プラットフォーム連鎖(chain_key = 0、tenant_id IS NULL)の生存行を chain_seq 昇順で返す(B-05 検証用)。 */
  List<AuditLogJpaEntity> findByTenantIdIsNullOrderByChainSeqAsc();

  /**
   * 監査ログ参照(A-22)。{@code tenant_id = :tenantId} を明示絞り込みし({@code TenantFilteredEntity} 非適用)、
   * created_at 降順・id 降順でページングする。{@code from} / {@code to} / {@code action} は null のとき絞り込みなし。
   */
  @Query(
      """
      SELECT a FROM AuditLogJpaEntity a
      WHERE a.tenantId = :tenantId
        AND (:from IS NULL OR a.createdAt >= :from)
        AND (:to IS NULL OR a.createdAt < :to)
        AND (:action IS NULL OR a.action = :action)
      ORDER BY a.createdAt DESC, a.id DESC
      """)
  Page<AuditLogJpaEntity> search(
      @Param("tenantId") long tenantId,
      @Param("from") @Nullable LocalDateTime from,
      @Param("to") @Nullable LocalDateTime to,
      @Param("action") @Nullable String action,
      Pageable pageable);
}
