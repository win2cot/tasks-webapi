package xyz.dgz48.tasks.webapi.task.adapter.web;

import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import xyz.dgz48.tasks.webapi.shared.exception.DomainException;
import xyz.dgz48.tasks.webapi.shared.web.ErrorCode;
import xyz.dgz48.tasks.webapi.shared.web.ErrorResponse;
import xyz.dgz48.tasks.webapi.task.domain.TaskNotFoundException;
import xyz.dgz48.tasks.webapi.task.domain.TaskNotViewableException;
import xyz.dgz48.tasks.webapi.task.domain.TaskOwnershipException;

@RestControllerAdvice(assignableTypes = TaskController.class)
public class TaskExceptionHandler {

  private static final ZoneId JST = ZoneId.of("Asia/Tokyo");

  @ExceptionHandler({TaskNotFoundException.class, TaskNotViewableException.class})
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public ErrorResponse handleNotFound(DomainException ex, HttpServletRequest request) {
    return new ErrorResponse(
        OffsetDateTime.now(JST),
        HttpStatus.NOT_FOUND.value(),
        HttpStatus.NOT_FOUND.getReasonPhrase(),
        ErrorCode.E_NOT_FOUND,
        Objects.requireNonNullElse(ex.getMessage(), "リソースが見つかりません"),
        request.getRequestURI());
  }

  @ExceptionHandler(TaskOwnershipException.class)
  @ResponseStatus(HttpStatus.FORBIDDEN)
  public ErrorResponse handleForbidden(DomainException ex, HttpServletRequest request) {
    return new ErrorResponse(
        OffsetDateTime.now(JST),
        HttpStatus.FORBIDDEN.value(),
        HttpStatus.FORBIDDEN.getReasonPhrase(),
        ErrorCode.E_FORBIDDEN,
        Objects.requireNonNullElse(ex.getMessage(), "操作権限がありません"),
        request.getRequestURI());
  }

  @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
  @ResponseStatus(HttpStatus.PRECONDITION_FAILED)
  public ErrorResponse handlePreconditionFailed(
      ObjectOptimisticLockingFailureException ex, HttpServletRequest request) {
    return new ErrorResponse(
        OffsetDateTime.now(JST),
        HttpStatus.PRECONDITION_FAILED.value(),
        HttpStatus.PRECONDITION_FAILED.getReasonPhrase(),
        ErrorCode.E_PRECONDITION_FAILED,
        "リソースが競合により更新できません。最新バージョンを取得し直してください",
        request.getRequestURI());
  }

  @ExceptionHandler(MissingRequestHeaderException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ErrorResponse handleMissingHeader(
      MissingRequestHeaderException ex, HttpServletRequest request) {
    return new ErrorResponse(
        OffsetDateTime.now(JST),
        HttpStatus.BAD_REQUEST.value(),
        HttpStatus.BAD_REQUEST.getReasonPhrase(),
        ErrorCode.E_VALIDATION,
        "必須ヘッダが指定されていません: " + ex.getHeaderName(),
        request.getRequestURI());
  }

  @ExceptionHandler(IllegalArgumentException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ErrorResponse handleIllegalArgument(
      IllegalArgumentException ex, HttpServletRequest request) {
    return new ErrorResponse(
        OffsetDateTime.now(JST),
        HttpStatus.BAD_REQUEST.value(),
        HttpStatus.BAD_REQUEST.getReasonPhrase(),
        ErrorCode.E_VALIDATION,
        Objects.requireNonNullElse(ex.getMessage(), "リクエストが不正です"),
        request.getRequestURI());
  }
}
