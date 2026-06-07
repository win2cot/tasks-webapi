package xyz.dgz48.tasks.webapi.task.domain;

import xyz.dgz48.tasks.webapi.shared.exception.DomainException;

/** 指定ユーザーが関係者として登録されていない場合にスローする(404)。 */
public class StakeholderNotFoundException extends DomainException {

  public StakeholderNotFoundException(Long taskId, Long userId) {
    super("ユーザー " + userId + " はタスク " + taskId + " の関係者として登録されていません");
  }
}
