package xyz.dgz48.tasks.webapi.task.domain;

import xyz.dgz48.tasks.webapi.shared.exception.DomainException;

/** 指定テナント内に対象タスクが存在しない、または参照不可の場合に投げる例外。 */
public class TaskNotFoundException extends DomainException {

  public TaskNotFoundException(Long taskId) {
    super("タスクが見つかりません: " + taskId);
  }
}
