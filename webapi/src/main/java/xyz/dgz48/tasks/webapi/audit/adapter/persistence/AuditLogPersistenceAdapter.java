package xyz.dgz48.tasks.webapi.audit.adapter.persistence;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import xyz.dgz48.tasks.webapi.audit.domain.AuditEventType;
import xyz.dgz48.tasks.webapi.audit.usecase.AuditLogPort;

@Component
class AuditLogPersistenceAdapter implements AuditLogPort {

  static final String GENESIS_HASH = "0".repeat(64);

  private final AuditLogJpaRepository repository;
  private final Clock clock;

  AuditLogPersistenceAdapter(AuditLogJpaRepository repository, Clock clock) {
    this.repository = repository;
    this.clock = clock;
  }

  @Override
  public void record(
      AuditEventType eventType, @Nullable Long tenantId, @Nullable Long userId, String detail) {
    LocalDateTime createdAt = LocalDateTime.now(clock);
    String hashChain = computeChainHash(repository.findFirstByOrderByIdDesc().orElse(null));
    var entity =
        new AuditLogJpaEntity(tenantId, userId, eventType.name(), detail, createdAt, hashChain);
    repository.save(entity);
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
