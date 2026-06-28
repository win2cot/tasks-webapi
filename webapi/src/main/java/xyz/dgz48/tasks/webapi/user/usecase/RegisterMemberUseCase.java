package xyz.dgz48.tasks.webapi.user.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 会員登録プリミティブ(ADR-0040 §3.3)。招待受諾(Flow A)/ セルフサインアップ(Flow B)の両フローが共有する。
 *
 * <p>処理順は <b>project DB 先 → Keycloak 後</b>(ADR-0006 §3.3):① {@code users} 行を upsert(profile、{@code
 * oidc_sub} は pending placeholder)→ ② Keycloak に credential 設定 + {@code emailVerified=true}。②
 * が失敗した場合は {@link CredentialProvisioningException} を伝播する。このとき ① の行は残る(未完了状態)が、再登録は email で
 * idempotent に upsert され credential を再試行できる(ADR-0040 §3.5)。呼び出し側は ② 失敗時に招待 / signup トークンを消費しないこと。
 *
 * <p>本ユースケースは remote 呼び出し(②)を跨ぐためクラスレベルの {@code @Transactional} を持たない。① の DB tx 境界は {@link
 * UserRegistrationPort} 実装側にある。
 */
@Service
@RequiredArgsConstructor
public class RegisterMemberUseCase {

  private final UserRegistrationPort userRegistrationPort;
  private final CredentialProvisioningPort credentialProvisioningPort;

  /**
   * 会員登録を実行し、登録した {@code users} 行の id を返す。
   *
   * @throws CredentialProvisioningException Keycloak への credential 設定に失敗した場合(① の行は残る)
   * @throws IllegalStateException email が既に correlation 済みの登録ユーザーに一致した場合
   */
  public Long register(RegisterMemberCommand cmd) {
    Long userId =
        userRegistrationPort.upsertPendingMember(
            cmd.email(), cmd.fullName(), cmd.fullNameKana(), cmd.departmentName());
    credentialProvisioningPort.provisionCredential(cmd.email(), cmd.rawPassword());
    return userId;
  }
}
