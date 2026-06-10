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
import xyz.dgz48.tasks.webapi.tenant.domain.TenantNotFoundException;

@RestControllerAdvice(assignableTypes = TenantAdminController.class)
class TenantAdminExceptionHandler {

  @ExceptionHandler(TenantNotFoundException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public ErrorResponse handleNotFound(TenantNotFoundException ex, HttpServletRequest request) {
    return new ErrorResponse(
        OffsetDateTime.now(AppZones.JST),
        HttpStatus.NOT_FOUND.value(),
        HttpStatus.NOT_FOUND.getReasonPhrase(),
        ErrorCode.E_NOT_FOUND,
        Objects.requireNonNullElse(ex.getMessage(), "テナントが見つかりません"),
        request.getRequestURI());
  }
}
