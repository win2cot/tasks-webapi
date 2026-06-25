package xyz.dgz48.tasks.webapi.audit.usecase;

import xyz.dgz48.tasks.webapi.audit.domain.AuditChainMismatch;

/**
 * 連鎖検証で検出した不整合を通知する Port(ADR-0038 §3.7)。
 *
 * <p>改ざん検知は fail-open(監査書込をブロックしない)。実装は構造化 ERROR ログ(ADR-0019、CloudWatch アラームの実体)を主経路とする。
 */
public interface AuditChainAlertPort {

  /** 不整合を 1 件通知する。 */
  void alert(AuditChainMismatch mismatch);
}
