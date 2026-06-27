package xyz.dgz48.tasks.webapi.tenant.domain;

import xyz.dgz48.tasks.webapi.shared.exception.DomainException;

/**
 * 招待対象の email が既にテナントの user_tenants に登録済み(重複招待)の場合にスローする業務例外。HTTP 409 にマップする(基本設計書 §5.4.1 /
 * ADR-0017 §3.3)。
 */
public class UserAlreadyMemberException extends DomainException {

  public UserAlreadyMemberException(String email, Long tenantId) {
    super("メールアドレス %s は既にテナント %d のメンバーとして登録されています".formatted(email, tenantId));
  }
}
