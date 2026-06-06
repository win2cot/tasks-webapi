package xyz.dgz48.tasks.webapi.tenant.domain;

import xyz.dgz48.tasks.webapi.shared.exception.DomainException;

/** 自分自身のロール変更・削除を試みた場合にスローする業務例外。HTTP 403 にマップする。 */
public class UserTenantSelfOperationException extends DomainException {

  public UserTenantSelfOperationException() {
    super("自分自身のロール変更・削除はできません");
  }
}
