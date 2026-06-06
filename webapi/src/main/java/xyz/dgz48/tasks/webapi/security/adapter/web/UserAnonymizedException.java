package xyz.dgz48.tasks.webapi.security.adapter.web;

import org.springframework.security.core.AuthenticationException;

/** users.deleted_at IS NOT NULL のユーザーが認証試行した(Issue #305 / USER_ANONYMIZED)。 */
public class UserAnonymizedException extends AuthenticationException {

  public UserAnonymizedException() {
    super("ユーザーアカウントは削除済みです");
  }
}
