package xyz.dgz48.tasks.webapi.tenant.domain;

import xyz.dgz48.tasks.webapi.shared.exception.DomainException;

/** 確認トークンに対応するサインアップ要求が存在しない場合にスローする業務例外。HTTP 404 にマップする(存在有無を漏らさない。ADR-0040 §3.3)。 */
public class SignupNotFoundException extends DomainException {

  public SignupNotFoundException() {
    super("確認リンクが無効です");
  }
}
