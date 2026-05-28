package xyz.dgz48.tasks.webapi.task.adapter.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import xyz.dgz48.tasks.webapi.task.domain.TaskNotFoundException;
import xyz.dgz48.tasks.webapi.task.domain.TaskNotViewableException;

@RestControllerAdvice
public class TaskExceptionHandler {

  @ExceptionHandler(TaskNotFoundException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public void handleTaskNotFound() {}

  @ExceptionHandler(TaskNotViewableException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public void handleTaskNotViewable() {}
}
