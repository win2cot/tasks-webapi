package xyz.dgz48.tasks.webapi.user.usecase;

/**
 * Keycloak への credential プロビジョニングの out-port(ADR-0040 §3.1 / §3.4)。
 *
 * <p>会員登録プリミティブ({@link RegisterMemberUseCase})から、project DB への users 行書込の後に呼ばれる。実装は Keycloak Admin
 * REST API でパスワードを設定し {@code emailVerified=true} にする(ADR-0006 §3.3「credential は Keycloak が
 * SoT」を維持)。dev/test は no-op のログ実装にフォールバックする。
 */
public interface CredentialProvisioningPort {

  /**
   * 指定 email の Keycloak ユーザーにパスワードを設定し、email を検証済みにする。
   *
   * @param email 対象ユーザーの email(Keycloak username)
   * @param rawPassword 設定するパスワード(平文。ログ出力禁止)
   * @throws CredentialProvisioningException Keycloak への設定に失敗した場合
   */
  void provisionCredential(String email, String rawPassword);
}
