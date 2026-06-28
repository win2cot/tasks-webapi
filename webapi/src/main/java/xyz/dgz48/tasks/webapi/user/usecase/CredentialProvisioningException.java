package xyz.dgz48.tasks.webapi.user.usecase;

import org.jspecify.annotations.Nullable;
import xyz.dgz48.tasks.webapi.shared.exception.DomainException;

/**
 * Keycloak への credential プロビジョニング(パスワード設定 / emailVerified 更新)に失敗したことを表す業務例外(ADR-0040 §3.1、コーディング規約
 * §6.1 に従い {@link DomainException} を基底とする)。
 *
 * <p>会員登録は「project DB 先 → Keycloak 後」の順で行うため、本例外が発生した時点で users 行は既に書かれている(未完了状態)。呼び出し側は招待 / signup
 * トークンを消費せず、再試行 / 補償に委ねる(ADR-0006 §3.3 / ADR-0040 §3.5)。
 */
public class CredentialProvisioningException extends DomainException {

  public CredentialProvisioningException(String message) {
    super(message);
  }

  public CredentialProvisioningException(String message, @Nullable Throwable cause) {
    super(message, cause);
  }
}
