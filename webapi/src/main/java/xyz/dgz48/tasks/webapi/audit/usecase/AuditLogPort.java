package xyz.dgz48.tasks.webapi.audit.usecase;

import org.jspecify.annotations.Nullable;
import xyz.dgz48.tasks.webapi.audit.domain.AuditEventType;

/** audit_logs テーブルへの書き込み Port。 */
public interface AuditLogPort {

  /**
   * 監査ログを記録する。
   *
   * @param eventType イベント種別
   * @param tenantId テナント ID(システム横断イベントの場合は {@code null})
   * @param userId 操作ユーザー ID(不明な場合は {@code null})
   * @param detail JSON 形式の詳細情報
   */
  void record(
      AuditEventType eventType, @Nullable Long tenantId, @Nullable Long userId, String detail);
}
