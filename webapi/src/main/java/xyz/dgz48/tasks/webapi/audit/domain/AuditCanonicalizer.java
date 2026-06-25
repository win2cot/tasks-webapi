package xyz.dgz48.tasks.webapi.audit.domain;

import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * 監査行を決定的な正準 JSON バイト列へ変換する(ADR-0038 §3.2)。
 *
 * <p>書込側・検証側の双方がこの 1 関数だけを通すことで HMAC 入力の byte 一致を保証する。決定性のため、リクエスト経路の {@code JsonMapper}
 * には依存せず、本クラス専用に <b>マップキーをキー順整列</b>({@link SerializationFeature#ORDER_MAP_ENTRIES_BY_KEYS})した
 * {@code JsonMapper} を構築する。これによりトップレベル列もネストした {@code detail} オブジェクトのキー順も、書込時の挿入順に依存せず一意に定まる。
 *
 * <p>{@code created_at} は Jackson の自動シリアライズに任せず、秒精度の固定フォーマットで文字列化する({@code DATETIME}
 * 列の精度と一致させ、再読込時の差異を防ぐ)。{@code detail} は格納済み JSON 文字列をツリーに復元してネスト object として埋め込み、文字列として再エスケープしない。
 */
public final class AuditCanonicalizer {

  /** {@code created_at} の正準フォーマット(JST 壁時計・秒精度。ADR-0009)。 */
  private static final DateTimeFormatter CREATED_AT_FORMAT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

  /** キー順整列を有効にした正準化専用 mapper(マップは全階層でキー昇順に整列される)。 */
  private static final JsonMapper CANONICAL_MAPPER =
      JsonMapper.builder().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).build();

  private AuditCanonicalizer() {}

  /**
   * 監査行を正準 JSON の UTF-8 バイト列へ変換する。
   *
   * @param row 監査行(不変列)
   * @return HMAC 入力に用いる正準バイト列
   */
  public static byte[] canonicalBytes(CanonicalAuditRow row) {
    Map<String, Object> canonical = new LinkedHashMap<>();
    canonical.put("chain_key", row.chainKey());
    canonical.put("chain_seq", row.chainSeq());
    canonical.put("user_id", row.userId());
    canonical.put("action", row.action());
    canonical.put("entity_type", row.entityType());
    canonical.put("entity_id", row.entityId());
    canonical.put("detail", CANONICAL_MAPPER.readValue(row.detailJson(), Object.class));
    canonical.put("ip_address", row.ipAddress());
    canonical.put("created_at", row.createdAt().format(CREATED_AT_FORMAT));
    canonical.put("hash_key_id", row.hashKeyId());
    return CANONICAL_MAPPER.writeValueAsString(canonical).getBytes(StandardCharsets.UTF_8);
  }
}
