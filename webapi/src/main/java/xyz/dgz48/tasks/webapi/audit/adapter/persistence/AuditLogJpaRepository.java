package xyz.dgz48.tasks.webapi.audit.adapter.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface AuditLogJpaRepository extends JpaRepository<AuditLogJpaEntity, Long> {

  /** テナント連鎖(chain_key = tenant_id)の生存行を chain_seq 昇順で返す(B-05 検証用)。 */
  List<AuditLogJpaEntity> findByTenantIdOrderByChainSeqAsc(long tenantId);

  /** プラットフォーム連鎖(chain_key = 0、tenant_id IS NULL)の生存行を chain_seq 昇順で返す(B-05 検証用)。 */
  List<AuditLogJpaEntity> findByTenantIdIsNullOrderByChainSeqAsc();
}
