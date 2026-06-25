package xyz.dgz48.tasks.webapi.audit.adapter.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface AuditAnchorJpaRepository extends JpaRepository<AuditAnchorJpaEntity, Long> {

  /** 指定 seq 未満で最大の {@code seq_at_checkpoint} を持つアンカー(保管削除後の検証起点)。 */
  Optional<AuditAnchorJpaEntity>
      findFirstByChainKeyAndSeqAtCheckpointLessThanOrderBySeqAtCheckpointDesc(
          long chainKey, long seqExclusive);
}
