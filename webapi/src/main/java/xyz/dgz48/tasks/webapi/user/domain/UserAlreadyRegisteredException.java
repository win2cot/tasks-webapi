package xyz.dgz48.tasks.webapi.user.domain;

import xyz.dgz48.tasks.webapi.shared.exception.DomainException;

/**
 * 会員登録(ADR-0040 §3.3)で、対象 email が既に correlation 済み(本物の Keycloak {@code sub}
 * に紐付く登録済みユーザー)の行に一致した場合の業務例外。HTTP 409 にマップする。
 *
 * <p>コーディング規約 §7 に従い、PII(email)はメッセージに含めない。
 */
public class UserAlreadyRegisteredException extends DomainException {

  public UserAlreadyRegisteredException() {
    super("指定された email は既に登録済みです");
  }
}
