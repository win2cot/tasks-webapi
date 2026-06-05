package xyz.dgz48.tasks.webapi.tenant.domain;

import xyz.dgz48.tasks.webapi.shared.exception.DomainException;

/** 指定テナントの ACTIVE メンバーでない場合にスローする業務例外。HTTP 403 にマップする。 */
public class TenantNotMemberException extends DomainException {

  public TenantNotMemberException(Long tenantId) {
    super("テナント %d のアクティブメンバーではありません".formatted(tenantId));
  }
}
