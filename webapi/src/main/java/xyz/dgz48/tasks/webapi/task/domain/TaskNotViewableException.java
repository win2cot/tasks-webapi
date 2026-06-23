package xyz.dgz48.tasks.webapi.task.domain;

import xyz.dgz48.tasks.webapi.shared.exception.DomainException;

/** タスク参照拒否(可視性フィルタ不通過)。404 + 監査ログ {@code VIEW_DENIED}(基本設計書 §6.2.3)。 */
public class TaskNotViewableException extends DomainException {

  private final Long taskId;

  public TaskNotViewableException(Long taskId) {
    super("タスクを参照できません: " + taskId);
    this.taskId = taskId;
  }

  public Long getTaskId() {
    return taskId;
  }
}
