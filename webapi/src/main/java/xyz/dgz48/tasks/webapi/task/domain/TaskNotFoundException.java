package xyz.dgz48.tasks.webapi.task.domain;

import xyz.dgz48.tasks.webapi.shared.exception.DomainException;

public class TaskNotFoundException extends DomainException {
  public TaskNotFoundException(Long taskId) {
    super("Task not found: " + taskId);
  }
}
