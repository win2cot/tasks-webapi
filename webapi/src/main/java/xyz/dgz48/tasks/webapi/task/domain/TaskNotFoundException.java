package xyz.dgz48.tasks.webapi.task.domain;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** 指定テナント内に対象タスクが存在しない、または参照不可の場合に投げる例外。 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class TaskNotFoundException extends RuntimeException {

  public TaskNotFoundException(Long taskId) {
    super("タスクが見つかりません: " + taskId);
  }
}
