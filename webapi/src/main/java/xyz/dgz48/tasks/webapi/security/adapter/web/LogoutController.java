package xyz.dgz48.tasks.webapi.security.adapter.web;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import xyz.dgz48.tasks.webapi.security.usecase.LogoutUseCase;

/**
 * ログアウト endpoint。
 *
 * <p>Keycloak の end_session_endpoint URL を構築して返す。クライアントはこの URL へリダイレクトして SSO セッションを終了する。
 */
@RestController
@RequestMapping("/api/auth/logout")
public class LogoutController {

  private final LogoutUseCase logoutUseCase;

  public LogoutController(LogoutUseCase logoutUseCase) {
    this.logoutUseCase = logoutUseCase;
  }

  public record LogoutResponse(String endSessionUrl) {}

  @PostMapping
  public LogoutResponse logout(
      @RequestParam String idTokenHint, @RequestParam String postLogoutRedirectUri) {
    return new LogoutResponse(logoutUseCase.buildEndSessionUrl(idTokenHint, postLogoutRedirectUri));
  }
}
