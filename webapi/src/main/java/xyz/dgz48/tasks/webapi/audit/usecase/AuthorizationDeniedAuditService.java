package xyz.dgz48.tasks.webapi.audit.usecase;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import xyz.dgz48.tasks.webapi.audit.domain.AuditEventType;

/**
 * 認可違反({@code *_DENIED})を {@code audit_logs} に記録するサービス(基本設計書 §6.2.3)。
 *
 * <p>呼び出し元(例外ハンドラ / {@code AccessDeniedHandler})は、認可違反でリクエストの トランザクションがロールバックされた後に実行される。記録を確実に残すため
 * {@code REQUIRES_NEW} で独立したトランザクションにコミットする({@code CrossTenantViolationAuditService} と同方針)。
 */
@Service
public class AuthorizationDeniedAuditService {

  private final AuditLogPort auditLogPort;

  public AuthorizationDeniedAuditService(AuditLogPort auditLogPort) {
    this.auditLogPort = auditLogPort;
  }

  /**
   * 認可違反を記録する。
   *
   * @param action §6.2.3 の {@code *_DENIED} アクション
   * @param tenantId 現在テナント ID(未選択の場合は {@code null})
   * @param userId 操作ユーザー ID(不明な場合は {@code null})
   * @param detail 文脈情報(対象 entity 等)
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void record(
      AuditEventType action,
      @Nullable Long tenantId,
      @Nullable Long userId,
      @Nullable Object detail) {
    auditLogPort.record(action, tenantId, userId, detail);
  }
}
