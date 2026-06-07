package xyz.dgz48.tasks.webapi.task.domain;

import xyz.dgz48.tasks.webapi.shared.exception.DomainException;

/** 指定ユーザーが既に関係者として登録済みの場合にスローする(409 Conflict)。 */
public class StakeholderAlreadyExistsException extends DomainException {

  public StakeholderAlreadyExistsException(Long taskId, Long userId) {
    super("ユーザー " + userId + " はタスク " + taskId + " の関係者として既に登録済みです");
  }
}
