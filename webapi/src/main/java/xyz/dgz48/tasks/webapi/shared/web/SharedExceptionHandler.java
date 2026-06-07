package xyz.dgz48.tasks.webapi.shared.web;

import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import xyz.dgz48.tasks.webapi.shared.exception.SaasAdminRequiredException;

/** 共通業務例外を HTTP レスポンスにマップするグローバル例外ハンドラ。 */
@RestControllerAdvice
class SharedExceptionHandler {

  private static final ZoneId JST = ZoneId.of("Asia/Tokyo");

  @ExceptionHandler(SaasAdminRequiredException.class)
  @ResponseStatus(HttpStatus.FORBIDDEN)
  public ErrorResponse handleSaasAdminRequired(
      SaasAdminRequiredException ex, HttpServletRequest request) {
    return new ErrorResponse(
        OffsetDateTime.now(JST),
        HttpStatus.FORBIDDEN.value(),
        HttpStatus.FORBIDDEN.getReasonPhrase(),
        ErrorCode.E_FORBIDDEN,
        Objects.requireNonNullElse(ex.getMessage(), "SaaS Admin ロールが必要です"),
        request.getRequestURI());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ErrorResponse handleValidation(
      MethodArgumentNotValidException ex, HttpServletRequest request) {
    String message =
        ex.getBindingResult().getFieldErrors().stream()
            .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
            .findFirst()
            .orElse("入力値が不正です");
    return new ErrorResponse(
        OffsetDateTime.now(JST),
        HttpStatus.BAD_REQUEST.value(),
        HttpStatus.BAD_REQUEST.getReasonPhrase(),
        ErrorCode.E_VALIDATION,
        message,
        request.getRequestURI());
  }
}
