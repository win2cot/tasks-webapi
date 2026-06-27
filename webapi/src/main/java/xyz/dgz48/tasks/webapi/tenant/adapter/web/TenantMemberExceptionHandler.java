package xyz.dgz48.tasks.webapi.tenant.adapter.web;

import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import xyz.dgz48.tasks.webapi.shared.infra.AppZones;
import xyz.dgz48.tasks.webapi.shared.web.ErrorCode;
import xyz.dgz48.tasks.webapi.shared.web.ErrorResponse;
import xyz.dgz48.tasks.webapi.tenant.domain.UserTenantNotFoundException;
import xyz.dgz48.tasks.webapi.tenant.domain.UserTenantSelfOperationException;

@RestControllerAdvice(assignableTypes = TenantMemberController.class)
public class TenantMemberExceptionHandler {

  @ExceptionHandler(UserTenantNotFoundException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public ErrorResponse handleNotFound(UserTenantNotFoundException ex, HttpServletRequest request) {
    return error(
        HttpStatus.NOT_FOUND,
        ErrorCode.E_NOT_FOUND,
        Objects.requireNonNullElse(ex.getMessage(), "メンバーが見つかりません"),
        request);
  }

  @ExceptionHandler(UserTenantSelfOperationException.class)
  @ResponseStatus(HttpStatus.FORBIDDEN)
  public ErrorResponse handleForbidden(
      UserTenantSelfOperationException ex, HttpServletRequest request) {
    return error(
        HttpStatus.FORBIDDEN,
        ErrorCode.E_FORBIDDEN,
        Objects.requireNonNullElse(ex.getMessage(), "操作権限がありません"),
        request);
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
