package xyz.dgz48.tasks.webapi.user.usecase;

/**
 * Keycloak への credential プロビジョニング(パスワード設定 / emailVerified 更新)に失敗したことを表す例外(ADR-0040 §3.1)。
 *
 * <p>会員登録は「project DB 先 → Keycloak 後」の順で行うため、本例外が発生した時点で users 行は既に書かれている(未完了状態)。呼び出し側は招待 / signup
 * トークンを消費せず、再試行 / 補償に委ねる(ADR-0006 §3.3 / ADR-0040 §3.5)。
 */
public class CredentialProvisioningException extends RuntimeException {

  public CredentialProvisioningException(String message) {
    super(message);
  }
}
