package xyz.dgz48.tasks.webapi.tenant.adapter.web;

import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import java.util.Objects;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import xyz.dgz48.tasks.webapi.shared.infra.AppZones;
import xyz.dgz48.tasks.webapi.shared.web.ErrorCode;
import xyz.dgz48.tasks.webapi.shared.web.ErrorResponse;
import xyz.dgz48.tasks.webapi.tenant.domain.TenantCodeConflictException;

/**
 * {@link TenantSignupController} 専用の例外ハンドラ。テナント {@code code} 一意化失敗({@link
 * TenantCodeConflictException})および並行作成による一意制約違反({@link DataIntegrityViolationException})を 409 /
 * E_CONFLICT にマップする。スコープを Controller に限定し、他経路の {@code DataIntegrityViolationException} 解釈に影響しない。
 */
@RestControllerAdvice(assignableTypes = TenantSignupController.class)
public class TenantSignupExceptionHandler {

  @ExceptionHandler(TenantCodeConflictException.class)
  @ResponseStatus(HttpStatus.CONFLICT)
  public ErrorResponse handleCodeConflict(
      TenantCodeConflictException ex, HttpServletRequest request) {
    return error(
        HttpStatus.CONFLICT,
        ErrorCode.E_CONFLICT,
        Objects.requireNonNullElse(ex.getMessage(), "テナントコードの一意化に失敗しました"),
        request);
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  @ResponseStatus(HttpStatus.CONFLICT)
  public ErrorResponse handleDataIntegrity(
      DataIntegrityViolationException ex, HttpServletRequest request) {
    return error(HttpStatus.CONFLICT, ErrorCode.E_CONFLICT, "同名テナントが既に存在します", request);
  }

  private static ErrorResponse error(
      HttpStatus status, ErrorCode code, String message, HttpServletRequest request) {
    return new ErrorResponse(
        OffsetDateTime.now(AppZones.JST),
        status.value(),
        status.getReasonPhrase(),
        code,
        message,
        request.getRequestURI());
  }
}
