package xyz.dgz48.tasks.webapi.security.adapter.web;

import org.springframework.security.core.AuthenticationException;

/** users.status = INACTIVE のユーザーが認証試行した(Issue #305 / USER_INACTIVE)。 */
public class UserInactiveException extends AuthenticationException {

  public UserInactiveException() {
    super("ユーザーアカウントが無効化されています");
  }
}
