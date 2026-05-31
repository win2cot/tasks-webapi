package xyz.dgz48.tasks.webapi.task.domain;

import xyz.dgz48.tasks.webapi.shared.exception.DomainException;

public class TaskNotViewableException extends DomainException {
  public TaskNotViewableException(Long taskId) {
    super("タスクを参照できません: " + taskId);
  }
}
