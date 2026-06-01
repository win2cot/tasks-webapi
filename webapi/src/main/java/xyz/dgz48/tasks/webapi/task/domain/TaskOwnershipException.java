package xyz.dgz48.tasks.webapi.task.domain;

import xyz.dgz48.tasks.webapi.shared.exception.DomainException;

public class TaskOwnershipException extends DomainException {
  public TaskOwnershipException(Long taskId) {
    super("タスクの操作権限がありません: " + taskId);
  }
}
