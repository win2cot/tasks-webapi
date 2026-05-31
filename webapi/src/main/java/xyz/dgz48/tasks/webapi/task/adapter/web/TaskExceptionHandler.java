package xyz.dgz48.tasks.webapi.task.adapter.web;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import xyz.dgz48.tasks.webapi.shared.exception.DomainException;
import xyz.dgz48.tasks.webapi.task.domain.TaskNotFoundException;
import xyz.dgz48.tasks.webapi.task.domain.TaskNotViewableException;

@RestControllerAdvice(assignableTypes = TaskController.class)
public class TaskExceptionHandler {

  @ExceptionHandler({TaskNotFoundException.class, TaskNotViewableException.class})
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public Map<String, String> handleNotFound(DomainException ex) {
    return Map.of("error", ex.getMessage());
  }
}
