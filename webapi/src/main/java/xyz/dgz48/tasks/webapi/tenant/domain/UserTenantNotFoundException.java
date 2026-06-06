package xyz.dgz48.tasks.webapi.tenant.domain;

import xyz.dgz48.tasks.webapi.shared.exception.DomainException;

/** 指定ユーザーがテナントのアクティブメンバーでない場合にスローする業務例外。HTTP 404 にマップする。 */
public class UserTenantNotFoundException extends DomainException {

  public UserTenantNotFoundException(Long userId, Long tenantId) {
    super("ユーザー %d はテナント %d のアクティブメンバーではありません".formatted(userId, tenantId));
  }
}
