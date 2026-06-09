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
import xyz.dgz48.tasks.webapi.tenant.domain.TenantNotMemberException;

@RestControllerAdvice(assignableTypes = SelectTenantController.class)
public class SelectTenantExceptionHandler {

  @ExceptionHandler(TenantNotMemberException.class)
  @ResponseStatus(HttpStatus.FORBIDDEN)
  public ErrorResponse handleNotMember(TenantNotMemberException ex, HttpServletRequest request) {
    return new ErrorResponse(
        OffsetDateTime.now(AppZones.JST),
        HttpStatus.FORBIDDEN.value(),
        HttpStatus.FORBIDDEN.getReasonPhrase(),
        ErrorCode.E_FORBIDDEN,
        Objects.requireNonNullElse(ex.getMessage(), "操作権限がありません"),
        request.getRequestURI());
  }
}
