package xyz.dgz48.tasks.webapi.audit.adapter.persistence;

import io.micrometer.observation.annotation.Observed;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import xyz.dgz48.tasks.webapi.audit.domain.AuditAnchor;
import xyz.dgz48.tasks.webapi.audit.domain.AuditChainRow;
import xyz.dgz48.tasks.webapi.audit.domain.CanonicalAuditRow;
import xyz.dgz48.tasks.webapi.audit.domain.ChainHead;
import xyz.dgz48.tasks.webapi.audit.usecase.AuditChainQueryPort;

/**
 * {@link AuditChainQueryPort} の JPA 実装(ADR-0038 §3.7)。
 *
 * <p>{@code chain_key} は {@code audit_logs} の列ではなく {@code tenant_id} から導出する(テナント行は {@code
 * tenant_id}、横断行 = 予約値 {@code 0} は {@code tenant_id IS NULL})。生存行の取得は {@code idx_al_chain
 * (tenant_id, chain_seq)} を用いる。
 */
@Observed(name = "audit.chainQuery")
@Component
class AuditChainQueryAdapter implements AuditChainQueryPort {

  private static final long PLATFORM_CHAIN_KEY = 0L;

  private final AuditLogJpaRepository auditLogRepository;
  private final ChainHeadJpaRepository chainHeadRepository;
  private final AuditAnchorJpaRepository anchorRepository;
  private final Clock clock;

  AuditChainQueryAdapter(
      AuditLogJpaRepository auditLogRepository,
      ChainHeadJpaRepository chainHeadRepository,
      AuditAnchorJpaRepository anchorRepository,
      Clock clock) {
    this.auditLogRepository = auditLogRepository;
    this.chainHeadRepository = chainHeadRepository;
    this.anchorRepository = anchorRepository;
    this.clock = clock;
  }

  @Override
  public List<Long> findActiveChainKeys() {
    return chainHeadRepository.findAllChainKeys();
  }

  @Override
  public List<AuditChainRow> findRows(long chainKey) {
    List<AuditLogJpaEntity> entities =
        chainKey == PLATFORM_CHAIN_KEY
            ? auditLogRepository.findByTenantIdIsNullOrderByChainSeqAsc()
            : auditLogRepository.findByTenantIdOrderByChainSeqAsc(chainKey);
    return entities.stream().map(e -> toChainRow(chainKey, e)).toList();
  }

  @Override
  public Optional<ChainHead> findHead(long chainKey) {
    return chainHeadRepository
        .findById(chainKey)
        .map(h -> new ChainHead(h.getChainKey(), h.getLastHash(), h.getLastSeq()));
  }

  @Override
  public Optional<AuditAnchor> findLatestAnchorBelow(long chainKey, long seqExclusive) {
    return anchorRepository
        .findFirstByChainKeyAndSeqAtCheckpointLessThanOrderBySeqAtCheckpointDesc(
            chainKey, seqExclusive)
        .map(
            a ->
                new AuditAnchor(
                    a.getChainKey(), a.getSeqAtCheckpoint(), a.getHeadHash(), a.getHashKeyId()));
  }

  @Override
  public void appendAnchor(long chainKey, long seqAtCheckpoint, String headHash, String hashKeyId) {
    LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
    anchorRepository.save(
        new AuditAnchorJpaEntity(chainKey, seqAtCheckpoint, headHash, hashKeyId, now));
  }

  private static AuditChainRow toChainRow(long chainKey, AuditLogJpaEntity e) {
    var canonical =
        new CanonicalAuditRow(
            chainKey,
            e.getChainSeq(),
            e.getUserId(),
            e.getAction(),
            e.getEntityType(),
            e.getEntityId(),
            e.getDetail() == null ? "{}" : e.getDetail(),
            e.getIpAddress(),
            e.getCreatedAt(),
            e.getHashKeyId());
    return new AuditChainRow(canonical, e.getHashChain());
  }
}
