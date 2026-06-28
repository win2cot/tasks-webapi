package xyz.dgz48.tasks.webapi.user.usecase;

import org.jspecify.annotations.Nullable;

/**
 * 会員登録時の {@code users} 行書込の out-port(ADR-0040 §3.3 ①)。
 *
 * <p>{@code oidc_sub} は {@code pending:<email>} placeholder で書き込み、初回ログイン時に本物の Keycloak {@code sub}
 * へ correlation される(ADR-0006 §3.2、{@code OidcSubCorrelationService})。
 */
public interface UserRegistrationPort {

  /**
   * email で {@code users} 行を upsert する。行が無ければ {@code pending:<email>} で insert し、未
   * correlation(pending)の行が あれば profile を更新する。
   *
   * @return 対象 {@code users} 行の id
   * @throws xyz.dgz48.tasks.webapi.user.domain.UserAlreadyRegisteredException email が既に correlation
   *     済み(本物の sub に紐付く登録済み)の行に一致した場合
   */
  Long upsertPendingMember(
      String email, String fullName, String fullNameKana, @Nullable String departmentName);
}
