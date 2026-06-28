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
import xyz.dgz48.tasks.webapi.tenant.domain.SignupNotAcceptableException;
import xyz.dgz48.tasks.webapi.tenant.domain.SignupNotFoundException;

/**
 * セルフサインアップ({@link SignupController})の業務例外マッピング(ADR-0040 §3.3 / HTTP ステータス方針)。
 *
 * <p>email 既登録({@code UserAlreadyRegisteredException} → 409)はグローバルな {@code UserExceptionHandler}
 * が処理する。
 */
@RestControllerAdvice(assignableTypes = SignupController.class)
public class SignupExceptionHandler {

  /** トークンに対応するサインアップ要求が無い → 404(存在有無を漏らさない)。 */
  @ExceptionHandler(SignupNotFoundException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public ErrorResponse handleNotFound(SignupNotFoundException ex, HttpServletRequest request) {
    return error(
        HttpStatus.NOT_FOUND,
        ErrorCode.E_NOT_FOUND,
        Objects.requireNonNullElse(ex.getMessage(), "確認リンクが無効です"),
        request);
  }

  /** 期限切れ / 使用済み / 失効 → 409。 */
  @ExceptionHandler(SignupNotAcceptableException.class)
  @ResponseStatus(HttpStatus.CONFLICT)
  public ErrorResponse handleNotAcceptable(
      SignupNotAcceptableException ex, HttpServletRequest request) {
    return error(
        HttpStatus.CONFLICT,
        ErrorCode.E_CONFLICT,
        Objects.requireNonNullElse(ex.getMessage(), "確認リンクは使用できない状態です"),
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
