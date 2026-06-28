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
import xyz.dgz48.tasks.webapi.tenant.domain.InvitationLoginRequiredException;
import xyz.dgz48.tasks.webapi.tenant.domain.InvitationNotAcceptableException;
import xyz.dgz48.tasks.webapi.tenant.domain.InvitationNotFoundException;

/** 招待受諾フロー({@link InvitationController})の業務例外マッピング(ADR-0040 §3.3 / HTTP ステータス方針)。 */
@RestControllerAdvice(assignableTypes = InvitationController.class)
public class InvitationExceptionHandler {

  /** トークンに対応する招待が無い → 404(存在有無を漏らさない)。 */
  @ExceptionHandler(InvitationNotFoundException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public ErrorResponse handleNotFound(InvitationNotFoundException ex, HttpServletRequest request) {
    return error(
        HttpStatus.NOT_FOUND,
        ErrorCode.E_NOT_FOUND,
        Objects.requireNonNullElse(ex.getMessage(), "招待が見つかりません"),
        request);
  }

  /** 期限切れ / 使用済み / 失効 → 409。 */
  @ExceptionHandler(InvitationNotAcceptableException.class)
  @ResponseStatus(HttpStatus.CONFLICT)
  public ErrorResponse handleNotAcceptable(
      InvitationNotAcceptableException ex, HttpServletRequest request) {
    return error(
        HttpStatus.CONFLICT,
        ErrorCode.E_CONFLICT,
        Objects.requireNonNullElse(ex.getMessage(), "招待は受諾できない状態です"),
        request);
  }

  /** 登録済みアカウント → 409(ログインして参加へ誘導)。 */
  @ExceptionHandler(InvitationLoginRequiredException.class)
  @ResponseStatus(HttpStatus.CONFLICT)
  public ErrorResponse handleLoginRequired(
      InvitationLoginRequiredException ex, HttpServletRequest request) {
    return error(
        HttpStatus.CONFLICT,
        ErrorCode.E_CONFLICT,
        Objects.requireNonNullElse(ex.getMessage(), "ログインしてから参加してください"),
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
