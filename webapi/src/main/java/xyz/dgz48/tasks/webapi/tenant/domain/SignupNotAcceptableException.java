package xyz.dgz48.tasks.webapi.tenant.domain;

import xyz.dgz48.tasks.webapi.shared.exception.DomainException;

/**
 * サインアップ要求が完了できない状態(期限切れ / 使用済み / 失効)の場合にスローする業務例外。HTTP 409 にマップする(ADR-0040 §3.3)。
 *
 * <p>{@code reason} は機微情報を含まない状態コード(EXPIRED / USED / REVOKED)。
 */
public class SignupNotAcceptableException extends DomainException {

  public SignupNotAcceptableException(String reason) {
    super("確認リンクは使用できない状態です(%s)".formatted(reason));
  }
}
