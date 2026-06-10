package xyz.dgz48.tasks.webapi.tenant.domain;

import xyz.dgz48.tasks.webapi.shared.exception.DomainException;

/** SUSPEND / REACTIVATE 以外のステータス(DELETED など)を A-26 エンドポイントに送信した場合にスローする例外。 */
public class TenantStatusNotAllowedException extends DomainException {

  public TenantStatusNotAllowedException(TenantStatus status) {
    super("このエンドポイントでは指定できないステータスです: " + status.name());
  }
}
