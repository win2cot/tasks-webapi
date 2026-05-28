package xyz.dgz48.tasks.webapi.task.domain;

import xyz.dgz48.tasks.webapi.shared.exception.DomainException;

/** visibility 制約により参照不可の場合に投げる例外。NIST AC-4 に従い 404 にマッピングする。 */
public class TaskNotViewableException extends DomainException {

  public TaskNotViewableException(Long taskId) {
    super("タスクが見つかりません: " + taskId);
  }
}
