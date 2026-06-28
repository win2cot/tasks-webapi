package xyz.dgz48.tasks.webapi.tenant.domain;

import xyz.dgz48.tasks.webapi.shared.exception.DomainException;

/**
 * 招待先 email が既に登録済みアカウントの場合にスローする業務例外。会員登録は不要で「ログインして参加」へ誘導する(ADR-0040 §3.3)。HTTP 409
 * にマップし、画面はログイン後に同トークンで再受諾(認証済み)させる。
 */
public class InvitationLoginRequiredException extends DomainException {

  public InvitationLoginRequiredException() {
    super("このメールアドレスは既に登録済みです。ログインしてから参加してください");
  }
}
