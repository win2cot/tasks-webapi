package xyz.dgz48.tasks.webapi.tenant.domain;

import xyz.dgz48.tasks.webapi.shared.exception.DomainException;

/** 受諾トークンに対応する招待が存在しない場合にスローする業務例外。HTTP 404 にマップする(NIST AC-4 — トークンの存在有無を漏らさない。ADR-0040 §3.3)。 */
public class InvitationNotFoundException extends DomainException {

  public InvitationNotFoundException() {
    super("招待が見つかりません");
  }
}
