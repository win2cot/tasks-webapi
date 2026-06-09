package xyz.dgz48.tasks.webapi.task.adapter.web;

import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import xyz.dgz48.tasks.webapi.shared.exception.DomainException;
import xyz.dgz48.tasks.webapi.shared.exception.PreconditionFailedException;
import xyz.dgz48.tasks.webapi.shared.infra.AppZones;
import xyz.dgz48.tasks.webapi.shared.web.ErrorCode;
import xyz.dgz48.tasks.webapi.shared.web.ErrorResponse;
import xyz.dgz48.tasks.webapi.task.domain.StakeholderAlreadyExistsException;
import xyz.dgz48.tasks.webapi.task.domain.StakeholderNotFoundException;
import xyz.dgz48.tasks.webapi.task.domain.TaskNotFoundException;
import xyz.dgz48.tasks.webapi.task.domain.TaskNotViewableException;
import xyz.dgz48.tasks.webapi.task.domain.TaskOwnershipException;

@RestControllerAdvice(assignableTypes = TaskController.class)
public class TaskExceptionHandler {

  @ExceptionHandler({
    TaskNotFoundException.class,
    TaskNotViewableException.class,
    StakeholderNotFoundException.class
  })
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public ErrorResponse handleNotFound(DomainException ex, HttpServletRequest request) {
    return new ErrorResponse(
        OffsetDateTime.now(AppZones.JST),
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
        OffsetDateTime.now(AppZones.JST),
        HttpStatus.FORBIDDEN.value(),
        HttpStatus.FORBIDDEN.getReasonPhrase(),
        ErrorCode.E_FORBIDDEN,
        Objects.requireNonNullElse(ex.getMessage(), "操作権限がありません"),
        request.getRequestURI());
  }

  @ExceptionHandler(PreconditionFailedException.class)
  @ResponseStatus(HttpStatus.PRECONDITION_FAILED)
  public ErrorResponse handlePreconditionFailed(
      PreconditionFailedException ex, HttpServletRequest request) {
    return new ErrorResponse(
        OffsetDateTime.now(AppZones.JST),
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
        OffsetDateTime.now(AppZones.JST),
        HttpStatus.BAD_REQUEST.value(),
        HttpStatus.BAD_REQUEST.getReasonPhrase(),
        ErrorCode.E_VALIDATION,
        "必須ヘッダが指定されていません: " + ex.getHeaderName(),
        request.getRequestURI());
  }

  @ExceptionHandler(StakeholderAlreadyExistsException.class)
  @ResponseStatus(HttpStatus.CONFLICT)
  public ErrorResponse handleConflict(DomainException ex, HttpServletRequest request) {
    return new ErrorResponse(
        OffsetDateTime.now(AppZones.JST),
        HttpStatus.CONFLICT.value(),
        HttpStatus.CONFLICT.getReasonPhrase(),
        ErrorCode.E_CONFLICT,
        Objects.requireNonNullElse(ex.getMessage(), "リソースが既に存在します"),
        request.getRequestURI());
  }

  @ExceptionHandler(InvalidIfMatchFormatException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ErrorResponse handleInvalidIfMatchFormat(
      InvalidIfMatchFormatException ex, HttpServletRequest request) {
    return new ErrorResponse(
        OffsetDateTime.now(AppZones.JST),
        HttpStatus.BAD_REQUEST.value(),
        HttpStatus.BAD_REQUEST.getReasonPhrase(),
        ErrorCode.E_VALIDATION,
        Objects.requireNonNullElse(ex.getMessage(), "If-Match ヘッダの形式が不正です"),
        request.getRequestURI());
  }
}
