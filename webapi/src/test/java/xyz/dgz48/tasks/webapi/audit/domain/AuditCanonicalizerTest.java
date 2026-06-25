package xyz.dgz48.tasks.webapi.audit.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/**
 * {@link AuditCanonicalizer} のユニットテスト。
 *
 * <p>正準バイト列がプロセス間で決定的(キー順固定・detail のキー順非依存・created_at の固定フォーマット・null の JSON null
 * 表現)であることを検証する。これは書込側と検証側で HMAC 入力が byte 一致することの回帰ロックである(ADR-0038 §3.2)。
 */
class AuditCanonicalizerTest {

  private static String canonical(CanonicalAuditRow row) {
    return new String(AuditCanonicalizer.canonicalBytes(row), StandardCharsets.UTF_8);
  }

  @Test
  void canonicalBytes_producesFixedKeyOrderJson_withNestedDetail() {
    var row =
        new CanonicalAuditRow(
            5L,
            3L,
            42L,
            "TASK_UPDATED",
            "tasks",
            100L,
            "{\"b\":2,\"a\":1}",
            "192.0.2.1",
            LocalDateTime.of(2026, 1, 15, 10, 30, 45),
            "v1");

    assertThat(canonical(row))
        .isEqualTo(
            "{\"action\":\"TASK_UPDATED\",\"chain_key\":5,\"chain_seq\":3,"
                + "\"created_at\":\"2026-01-15T10:30:45\",\"detail\":{\"a\":1,\"b\":2},"
                + "\"entity_id\":100,\"entity_type\":\"tasks\",\"hash_key_id\":\"v1\","
                + "\"ip_address\":\"192.0.2.1\",\"user_id\":42}");
  }

  @Test
  void canonicalBytes_representsNullColumnsAsJsonNull_andEmptyDetailAsEmptyObject() {
    var row =
        new CanonicalAuditRow(
            0L,
            1L,
            null,
            "TENANT_CROSSED",
            null,
            null,
            "{}",
            null,
            LocalDateTime.of(2026, 1, 15, 0, 0, 0),
            "v1");

    assertThat(canonical(row))
        .isEqualTo(
            "{\"action\":\"TENANT_CROSSED\",\"chain_key\":0,\"chain_seq\":1,"
                + "\"created_at\":\"2026-01-15T00:00:00\",\"detail\":{},"
                + "\"entity_id\":null,\"entity_type\":null,\"hash_key_id\":\"v1\","
                + "\"ip_address\":null,\"user_id\":null}");
  }

  @Test
  void canonicalBytes_isIndependentOfDetailKeyInsertionOrder() {
    var createdAt = LocalDateTime.of(2026, 1, 15, 10, 0, 0);
    var rowA =
        new CanonicalAuditRow(
            1L, 1L, 7L, "TASK_UPDATED", null, null, "{\"x\":1,\"y\":2}", null, createdAt, "v1");
    var rowB =
        new CanonicalAuditRow(
            1L, 1L, 7L, "TASK_UPDATED", null, null, "{\"y\":2,\"x\":1}", null, createdAt, "v1");

    assertThat(canonical(rowA)).isEqualTo(canonical(rowB));
  }

  @Test
  void canonicalBytes_truncatesNothingButFormatsSecondsPrecision() {
    var row =
        new CanonicalAuditRow(
            1L,
            1L,
            null,
            "LOGIN",
            null,
            null,
            "{}",
            null,
            LocalDateTime.of(2026, 12, 31, 23, 59, 59),
            "v1");

    assertThat(canonical(row)).contains("\"created_at\":\"2026-12-31T23:59:59\"");
  }
}
