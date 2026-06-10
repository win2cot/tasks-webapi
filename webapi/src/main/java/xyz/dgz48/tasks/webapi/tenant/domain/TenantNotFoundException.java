package xyz.dgz48.tasks.webapi.tenant.domain;

import xyz.dgz48.tasks.webapi.shared.exception.DomainException;

/** 指定 ID のテナントが存在しない、または参照権限がない場合にスローする例外(NIST AC-4 リソース存在秘匿)。 */
public class TenantNotFoundException extends DomainException {

  public TenantNotFoundException(Long tenantId) {
    super("テナントが見つかりません: id=" + tenantId);
  }
}
