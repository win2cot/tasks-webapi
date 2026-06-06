package xyz.dgz48.tasks.webapi.security.adapter.web;

import org.springframework.security.core.AuthenticationException;

/** JWT.sub に対応する users レコードが存在しない(Issue #305 / USER_NOT_REGISTERED)。 */
public class UserNotRegisteredException extends AuthenticationException {

  public UserNotRegisteredException() {
    super("JWT sub に対応するユーザーが登録されていません");
  }
}
