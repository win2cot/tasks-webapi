package xyz.dgz48.tasks.webapi.task.domain;

import xyz.dgz48.tasks.webapi.shared.exception.DomainException;

public class TaskNotFoundException extends DomainException {
  public TaskNotFoundException(Long taskId) {
    super("タスクが見つかりません: " + taskId);
  }
}
