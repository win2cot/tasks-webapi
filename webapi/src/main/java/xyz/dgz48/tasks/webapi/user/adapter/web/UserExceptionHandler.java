package xyz.dgz48.tasks.webapi.user.adapter.web;

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
import xyz.dgz48.tasks.webapi.user.domain.UserAlreadyRegisteredException;

/**
 * user feature の業務例外を HTTP レスポンスにマップする例外ハンドラ。
 *
 * <p>会員登録(ADR-0040)で email が既に登録済みの場合の {@link UserAlreadyRegisteredException} を 409 / E_CONFLICT
 * にマップする。会員登録の呼び出し経路(招待受諾 / セルフサインアップの Controller)は後続 PR で追加されるが、業務例外が 500 に化けないようマッピングを先行して用意する。
 */
@RestControllerAdvice
public class UserExceptionHandler {

  @ExceptionHandler(UserAlreadyRegisteredException.class)
  @ResponseStatus(HttpStatus.CONFLICT)
  public ErrorResponse handleAlreadyRegistered(
      UserAlreadyRegisteredException ex, HttpServletRequest request) {
    return new ErrorResponse(
        OffsetDateTime.now(AppZones.JST),
        HttpStatus.CONFLICT.value(),
        HttpStatus.CONFLICT.getReasonPhrase(),
        ErrorCode.E_CONFLICT,
        Objects.requireNonNullElse(ex.getMessage(), "指定された email は既に登録済みです"),
        request.getRequestURI());
  }
}
