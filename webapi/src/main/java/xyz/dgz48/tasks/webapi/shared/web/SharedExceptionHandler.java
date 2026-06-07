package xyz.dgz48.tasks.webapi.shared.web;

import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import xyz.dgz48.tasks.webapi.shared.exception.SaasAdminRequiredException;

/** 共通業務例外を HTTP レスポンスにマップするグローバル例外ハンドラ。 */
@RestControllerAdvice
class SharedExceptionHandler extends ResponseEntityExceptionHandler {

  private static final ZoneId JST = ZoneId.of("Asia/Tokyo");

  @Override
  protected ResponseEntity<Object> createResponseEntity(
      @Nullable Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
    if (body instanceof ProblemDetail) {
      return toErrorResponseEntity(headers, statusCode, request);
    }
    return super.createResponseEntity(body, headers, statusCode, request);
  }

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    String message =
        ex.getBindingResult().getFieldErrors().stream()
            .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
            .findFirst()
            .orElse("入力値が不正です");
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .headers(headers)
        .body(
            new ErrorResponse(
                OffsetDateTime.now(JST),
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                ErrorCode.E_VALIDATION,
                message,
                extractPath(request)));
  }

  private static ResponseEntity<Object> toErrorResponseEntity(
      HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
    HttpStatus status = HttpStatus.resolve(statusCode.value());
    String reasonPhrase = status != null ? status.getReasonPhrase() : statusCode.toString();
    String path = extractPath(request);
    ErrorCode code = resolveErrorCode(statusCode);
    return ResponseEntity.status(statusCode)
        .headers(headers)
        .body(
            new ErrorResponse(
                OffsetDateTime.now(JST),
                statusCode.value(),
                reasonPhrase,
                code,
                reasonPhrase,
                path));
  }

  private static String extractPath(WebRequest request) {
    if (request instanceof ServletWebRequest swr) {
      return swr.getRequest().getRequestURI();
    }
    return request.getDescription(false);
  }

  private static ErrorCode resolveErrorCode(HttpStatusCode statusCode) {
    return switch (statusCode.value()) {
      case 400 -> ErrorCode.E_VALIDATION;
      case 401 -> ErrorCode.E_UNAUTHORIZED;
      case 403 -> ErrorCode.E_FORBIDDEN;
      case 404 -> ErrorCode.E_NOT_FOUND;
      case 409 -> ErrorCode.E_CONFLICT;
      case 415, 422 -> ErrorCode.E_UNPROCESSABLE;
      default -> ErrorCode.E_INTERNAL;
    };
  }

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
