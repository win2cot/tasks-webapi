package xyz.dgz48.tasks.webapi.audit.adapter.external;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import xyz.dgz48.tasks.webapi.audit.domain.AuditChainMismatch;
import xyz.dgz48.tasks.webapi.audit.usecase.AuditChainAlertPort;

/**
 * 連鎖検証の不整合を構造化 ERROR ログで通知する {@link AuditChainAlertPort} 実装(ADR-0019 / ADR-0038 §3.7)。
 *
 * <p>ERROR ログが CloudWatch アラームの実体となる(ADR-0029)。出力は非 PII の連鎖メタデータ(chain_key / chain_seq /
 * 不整合種別)のみで、監査行そのものは記録しない(fail-open のため例外は投げない)。
 */
@Component
class LoggingAuditChainAlertPort implements AuditChainAlertPort {

  private static final Logger log = LoggerFactory.getLogger(LoggingAuditChainAlertPort.class);

  @Override
  public void alert(AuditChainMismatch mismatch) {
    log.error(
        "監査ログ ハッシュチェーンの不整合を検知 chainKey={} atSeq={} reason={}",
        mismatch.chainKey(),
        mismatch.atSeq(),
        mismatch.reason());
  }
}
