package xyz.dgz48.tasks.webapi.task.adapter.web;

import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import xyz.dgz48.tasks.webapi.audit.domain.AuditEventType;
import xyz.dgz48.tasks.webapi.audit.usecase.AuthorizationDeniedAuditService;
import xyz.dgz48.tasks.webapi.security.adapter.web.TasksAuthenticationToken;
import xyz.dgz48.tasks.webapi.shared.domain.TenantContext;
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

  private static final Logger log = LoggerFactory.getLogger(TaskExceptionHandler.class);

  private final AuthorizationDeniedAuditService authorizationDeniedAuditService;

  public TaskExceptionHandler(AuthorizationDeniedAuditService authorizationDeniedAuditService) {
    this.authorizationDeniedAuditService = authorizationDeniedAuditService;
  }

  @ExceptionHandler({TaskNotFoundException.class, StakeholderNotFoundException.class})
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

  /** タスク参照拒否(可視性フィルタ不通過): 404 + 監査ログ {@code VIEW_DENIED}(§6.2.3)。 */
  @ExceptionHandler(TaskNotViewableException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public ErrorResponse handleNotViewable(TaskNotViewableException ex, HttpServletRequest request) {
    recordDenied(AuditEventType.VIEW_DENIED, ex.getTaskId());
    return new ErrorResponse(
        OffsetDateTime.now(AppZones.JST),
        HttpStatus.NOT_FOUND.value(),
        HttpStatus.NOT_FOUND.getReasonPhrase(),
        ErrorCode.E_NOT_FOUND,
        Objects.requireNonNullElse(ex.getMessage(), "リソースが見つかりません"),
        request.getRequestURI());
  }

  /** タスク操作権限違反: 403 +(該当時)監査ログ {@code *_DENIED}(§6.2.3)。 */
  @ExceptionHandler(TaskOwnershipException.class)
  @ResponseStatus(HttpStatus.FORBIDDEN)
  public ErrorResponse handleForbidden(TaskOwnershipException ex, HttpServletRequest request) {
    AuditEventType deniedAction = ex.getDeniedAction();
    if (deniedAction != null) {
      recordDenied(deniedAction, ex.getTaskId());
    }
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

  /** 認可違反を {@code audit_logs} に記録する。記録失敗で本来の認可応答(403/404)を 500 に化けさせないよう、 例外は握りつぶしてログ出力に留める。 */
  private void recordDenied(AuditEventType action, Long taskId) {
    try {
      authorizationDeniedAuditService.record(
          action, TenantContext.get(), currentUserId(), Map.of("taskId", taskId));
    } catch (RuntimeException e) {
      log.warn("認可違反の監査記録に失敗しました: action={}", action, e);
    }
  }

  private static @Nullable Long currentUserId() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth instanceof TasksAuthenticationToken token) {
      return token.getPrincipal().getId();
    }
    return null;
  }
}
