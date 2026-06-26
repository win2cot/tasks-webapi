package xyz.dgz48.tasks.webapi.audit.adapter.web.dto;

import java.time.OffsetDateTime;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import xyz.dgz48.tasks.webapi.audit.domain.AuditLogEntry;
import xyz.dgz48.tasks.webapi.shared.infra.AppZones;

/**
 * OpenAPI {@code AuditLog} に対応する監査ログ参照レスポンス(A-22)。
 *
 * <p>{@code detail} は DB の JSON 文字列を object として埋め込む。{@code createdAt} は JST オフセット付き(ADR-0009)。
 */
public record AuditLogResponse(
    Long id,
    @Nullable Long userId,
    String action,
    @Nullable String entityType,
    @Nullable Long entityId,
    Object detail,
    @Nullable String ipAddress,
    OffsetDateTime createdAt) {

  public static AuditLogResponse from(AuditLogEntry entry, JsonMapper jsonMapper) {
    return new AuditLogResponse(
        entry.id(),
        entry.userId(),
        entry.action(),
        entry.entityType(),
        entry.entityId(),
        parseDetail(entry.detailJson(), jsonMapper),
        entry.ipAddress(),
        entry.createdAt().atZone(AppZones.JST).toOffsetDateTime());
  }

  /** 保存済み JSON 文字列を object へ復元する。空・破損時は空 object を返す。 */
  private static Object parseDetail(String detailJson, JsonMapper jsonMapper) {
    try {
      return jsonMapper.readValue(detailJson, Map.class);
    } catch (JacksonException e) {
      return Map.of();
    }
  }
}
