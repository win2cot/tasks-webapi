package xyz.dgz48.tasks.webapi.audit.adapter.persistence;

import io.micrometer.observation.annotation.Observed;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import xyz.dgz48.tasks.webapi.audit.domain.AuditEventType;
import xyz.dgz48.tasks.webapi.audit.usecase.AuditLogPort;

@Observed(name = "audit.repository")
@Component
class AuditLogPersistenceAdapter implements AuditLogPort {

  static final String GENESIS_HASH = "0".repeat(64);

  private final AuditLogJpaRepository repository;
  private final Clock clock;
  private final JsonMapper jsonMapper;

  AuditLogPersistenceAdapter(AuditLogJpaRepository repository, Clock clock, JsonMapper jsonMapper) {
    this.repository = repository;
    this.clock = clock;
    this.jsonMapper = jsonMapper;
  }

  @Override
  public void record(
      AuditEventType eventType,
      @Nullable Long tenantId,
      @Nullable Long userId,
      @Nullable Object detail) {
    LocalDateTime createdAt = LocalDateTime.now(clock);
    String hashChain = computeChainHash(repository.findFirstByOrderByIdDesc().orElse(null));
    String detailJson = serializeDetail(detail);
    var entity =
        new AuditLogJpaEntity(tenantId, userId, eventType.name(), detailJson, createdAt, hashChain);
    repository.save(entity);
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

  static String computeChainHash(@Nullable AuditLogJpaEntity prev) {
    if (prev == null) {
      return GENESIS_HASH;
    }
    String input = prev.getId() + "|" + prev.getDetail() + "|" + prev.getCreatedAt();
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder(64);
      for (byte b : hashBytes) {
        hex.append(String.format("%02x", b));
      }
      return hex.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }
}
