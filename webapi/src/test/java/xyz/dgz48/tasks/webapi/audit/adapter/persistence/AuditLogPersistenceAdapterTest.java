package xyz.dgz48.tasks.webapi.audit.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/** {@link AuditLogPersistenceAdapter#computeChainHash} のユニットテスト。 */
class AuditLogPersistenceAdapterTest {

  @Test
  void computeChainHash_whenNoPreviousRecord_returnsGenesisHash() {
    String hash = AuditLogPersistenceAdapter.computeChainHash(null);
    assertThat(hash).isEqualTo("0".repeat(64));
  }

  @Test
  void computeChainHash_withPreviousRecord_computesSha256OfIdDetailCreatedAt() {
    LocalDateTime createdAt = LocalDateTime.of(2026, 6, 6, 11, 0, 0);
    String detail = "{\"table\":\"tasks\",\"sqlType\":\"SELECT\"}";
    var prev =
        new AuditLogJpaEntity(
            null, null, "CROSS_TENANT_VIOLATION_ATTEMPT", detail, createdAt, "0".repeat(64));

    String hash = AuditLogPersistenceAdapter.computeChainHash(prev);

    // prev.getId() は null(未永続化エンティティ) — same behavior as null|detail|createdAt input
    String expectedInput = prev.getId() + "|" + prev.getDetail() + "|" + prev.getCreatedAt();
    assertThat(hash).isEqualTo(sha256Hex(expectedInput));
    assertThat(hash).matches("[0-9a-f]{64}");
    assertThat(hash).isNotEqualTo("0".repeat(64));
  }

  private static String sha256Hex(String input) {
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
