package xyz.dgz48.tasks.webapi.security.adapter.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * ログアウト endpoint。
 *
 * <p>JWT は stateless のためサーバ側セッションは存在しない。クライアントはトークンを破棄し、Keycloak の end_session_endpoint
 * へ遷移して SSO セッションを終了する。
 */
@RestController
@RequestMapping("/api/auth/logout")
public class LogoutController {

  @PostMapping
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void logout() {
    // stateless JWT — サーバ側に保持するセッション状態なし
  }
}
