package xyz.dgz48.tasks.webapi.audit.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import xyz.dgz48.tasks.webapi.audit.usecase.AuditFieldChange;

/**
 * {@link AuditLogPersistenceAdapter} の {@code serializeDetail} のユニットテスト。
 *
 * <p>連鎖計算({@code canonical} / HMAC)は {@code AuditCanonicalizerTest} / {@code AuditChainHasherTest}
 * で、 連鎖の連結・直列化は Testcontainers IT で検証する。
 */
class AuditLogPersistenceAdapterTest {

  private static JsonMapper defaultJsonMapper() {
    // Jackson 3.x では DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS がデフォルト false のため
    // LocalDate は設定なしで "2026-08-01" のような ISO 文字列にシリアライズされる
    return JsonMapper.builder().build();
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
    // serializeDetail は jsonMapper のみを使うため、他の協調オブジェクトは不要。
    return new AuditLogPersistenceAdapter(null, null, null, defaultJsonMapper(), null);
  }
}
