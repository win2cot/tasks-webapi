package xyz.dgz48.tasks.webapi.audit.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import xyz.dgz48.tasks.webapi.audit.usecase.AuditFieldChange;

/** {@link AuditLogPersistenceAdapter} のユニットテスト。 */
class AuditLogPersistenceAdapterTest {

  private static JsonMapper defaultJsonMapper() {
    // Jackson 3.x では DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS がデフォルト false のため
    // LocalDate は設定なしで "2026-08-01" のような ISO 文字列にシリアライズされる
    return JsonMapper.builder().build();
  }

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

  @Test
  void serializeDetail_null_returnsEmptyObject() {
    var adapter = newAdapter();
    assertThat(adapter.serializeDetail(null)).isEqualTo("{}");
  }

  @Test
  void serializeDetail_emptyMap_returnsEmptyObject() {
    var adapter = newAdapter();
    assertThat(adapter.serializeDetail(Map.of())).isEqualTo("{}");
  }

  @Test
  void serializeDetail_simpleMap_returnsValidJson() {
    var adapter = newAdapter();
    String json = adapter.serializeDetail(Map.of("taskId", 42L));
    assertThat(json).isEqualTo("{\"taskId\":42}");
  }

  @Test
  void serializeDetail_localDate_isQuotedAsIsoString() {
    // LocalDate.of(2026, 8, 1) → "2026-08-01"(引用符あり)で出力される回帰テスト(PR #703 再発防止)
    var adapter = newAdapter();
    List<AuditFieldChange> changes =
        List.of(new AuditFieldChange("dueDate", null, LocalDate.of(2026, 8, 1)));
    String json = adapter.serializeDetail(changes);
    assertThat(json).contains("\"2026-08-01\"");
    assertThat(json).doesNotContain("2026-08-01\"\"");
  }

  @Test
  void serializeDetail_auditFieldChange_usesOldNewKeys() {
    var adapter = newAdapter();
    List<AuditFieldChange> changes = List.of(new AuditFieldChange("title", "旧タイトル", "新タイトル"));
    String json = adapter.serializeDetail(changes);
    assertThat(json).contains("\"field\":\"title\"");
    assertThat(json).contains("\"old\":\"旧タイトル\"");
    assertThat(json).contains("\"new\":\"新タイトル\"");
  }

  private static AuditLogPersistenceAdapter newAdapter() {
    return new AuditLogPersistenceAdapter(null, null, defaultJsonMapper());
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
