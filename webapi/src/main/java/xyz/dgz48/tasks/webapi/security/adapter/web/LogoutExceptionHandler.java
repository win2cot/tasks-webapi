package xyz.dgz48.tasks.webapi.security.adapter.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.OffsetDateTime;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import xyz.dgz48.tasks.webapi.shared.infra.AppZones;
import xyz.dgz48.tasks.webapi.shared.web.ErrorCode;
import xyz.dgz48.tasks.webapi.shared.web.ErrorResponse;

@RestControllerAdvice(assignableTypes = LogoutController.class)
class LogoutExceptionHandler {

  @ExceptionHandler({ConstraintViolationException.class, HandlerMethodValidationException.class})
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ErrorResponse handleValidation(Exception ex, HttpServletRequest request) {
    return new ErrorResponse(
        OffsetDateTime.now(AppZones.JST),
        HttpStatus.BAD_REQUEST.value(),
        HttpStatus.BAD_REQUEST.getReasonPhrase(),
        ErrorCode.E_VALIDATION,
        Objects.requireNonNullElse(ex.getMessage(), "入力値が不正です"),
        request.getRequestURI());
  }
}
