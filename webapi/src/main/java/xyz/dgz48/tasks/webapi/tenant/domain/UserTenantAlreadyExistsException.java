package xyz.dgz48.tasks.webapi.tenant.domain;

import xyz.dgz48.tasks.webapi.shared.exception.DomainException;

/** 指定ユーザーが既にテナントメンバーとして登録済みの場合にスローする業務例外。HTTP 409 にマップする。 */
public class UserTenantAlreadyExistsException extends DomainException {

  public UserTenantAlreadyExistsException(Long userId, Long tenantId) {
    super("ユーザー %d はテナント %d に既に登録されています".formatted(userId, tenantId));
  }
}
