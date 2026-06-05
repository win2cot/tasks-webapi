package xyz.dgz48.tasks.webapi.shared.web;

import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Objects;
import org.springframework.http.HttpStatus;
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
}
