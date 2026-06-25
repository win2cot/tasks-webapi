package xyz.dgz48.tasks.webapi.audit.adapter.persistence;

import io.micrometer.observation.annotation.Observed;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import xyz.dgz48.tasks.webapi.audit.domain.AuditCanonicalizer;
import xyz.dgz48.tasks.webapi.audit.domain.AuditChainHasher;
import xyz.dgz48.tasks.webapi.audit.domain.AuditEventType;
import xyz.dgz48.tasks.webapi.audit.domain.CanonicalAuditRow;
import xyz.dgz48.tasks.webapi.audit.usecase.AuditLogPort;
import xyz.dgz48.tasks.webapi.audit.usecase.HmacKeyProvider;

/**
 * 監査ログを {@code audit_logs} に追記する {@link AuditLogPort} 実装(ADR-0038)。
 *
 * <p>各行に自レコードハッシュ {@code HMAC(canonical(自行) ‖ 前ハッシュ)} を格納する。並行 INSERT が連鎖を分岐させないよう、{@code
 * chain_key}(テナント行は {@code tenant_id}、横断行は予約値 {@code 0})単位で {@code chain_heads} 行を悲観ロックして直列化する。
 * {@code chain_heads} の upsert・{@code FOR UPDATE}・{@code audit_logs} INSERT・末尾更新は単一トランザクションで原子的に
 * 行う必要があるため {@link Propagation#REQUIRED} とする(呼出側に tx があれば join、無ければ本メソッドが境界となる)。これにより
 * ロックは末尾更新まで保持され、直列化が成立する。{@code MANDATORY} にしない理由は、{@code LOGIN_FAILED} のように tx 外の
 * セキュリティ経路から直接呼ばれる正当な呼出元が存在するためである。
 */
@Observed(name = "audit.repository")
@Component
class AuditLogPersistenceAdapter implements AuditLogPort {

  /** 横断行(tenant_id IS NULL)を 1 本のプラットフォーム連鎖にまとめる予約 chain_key。 */
  private static final long PLATFORM_CHAIN_KEY = 0L;

  private final AuditLogJpaRepository repository;
  private final ChainHeadJpaRepository chainHeadRepository;
  private final Clock clock;
  private final JsonMapper jsonMapper;
  private final HmacKeyProvider hmacKeyProvider;

  AuditLogPersistenceAdapter(
      AuditLogJpaRepository repository,
      ChainHeadJpaRepository chainHeadRepository,
      Clock clock,
      JsonMapper jsonMapper,
      HmacKeyProvider hmacKeyProvider) {
    this.repository = repository;
    this.chainHeadRepository = chainHeadRepository;
    this.clock = clock;
    this.jsonMapper = jsonMapper;
    this.hmacKeyProvider = hmacKeyProvider;
  }

  @Override
  @Transactional(propagation = Propagation.REQUIRED)
  public void record(
      AuditEventType eventType,
      @Nullable Long tenantId,
      @Nullable Long userId,
      @Nullable Object detail) {
    long chainKey = tenantId != null ? tenantId : PLATFORM_CHAIN_KEY;
    // DATETIME 列は秒精度のため、書込・正準化・再読込の三者が一致するよう秒精度に正規化する。
    LocalDateTime createdAt = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
    String detailJson = serializeDetail(detail);
    String keyId = hmacKeyProvider.currentKeyId();

    // chain_heads 行の存在を保証(初回はジェネシス相当)してから FOR UPDATE で末尾を掴み直列化する。
    chainHeadRepository.ensureExists(chainKey, AuditChainHasher.GENESIS_HASH, createdAt);
    ChainHeadJpaEntity head =
        chainHeadRepository
            .lockByChainKey(chainKey)
            .orElseThrow(() -> new IllegalStateException("chain_heads 行の取得に失敗しました: " + chainKey));

    long chainSeq = head.getLastSeq() + 1;
    var canonicalRow =
        new CanonicalAuditRow(
            chainKey,
            chainSeq,
            userId,
            eventType.name(),
            null,
            null,
            detailJson,
            null,
            createdAt,
            keyId);
    String hashChain =
        AuditChainHasher.hmacHex(
            AuditCanonicalizer.canonicalBytes(canonicalRow),
            head.getLastHash(),
            hmacKeyProvider.keyFor(keyId));

    repository.save(
        new AuditLogJpaEntity(
            tenantId, userId, eventType.name(), detailJson, chainSeq, hashChain, keyId, createdAt));
    head.update(hashChain, chainSeq, createdAt);
    chainHeadRepository.save(head);
  }

  /** シリアライズ失敗は {@link JacksonException}(unchecked)をそのまま伝播させ fail-closed とする。 */
  String serializeDetail(@Nullable Object detail) {
    if (detail == null) return "{}";
    try {
      return jsonMapper.writeValueAsString(detail);
    } catch (JacksonException e) {
      throw new IllegalStateException("監査 detail のシリアライズに失敗しました", e);
    }
  }
}
