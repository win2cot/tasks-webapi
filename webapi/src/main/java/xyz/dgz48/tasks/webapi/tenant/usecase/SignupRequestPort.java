package xyz.dgz48.tasks.webapi.tenant.usecase;

import java.time.LocalDateTime;
import java.util.Optional;
import xyz.dgz48.tasks.webapi.tenant.domain.SignupRequestStatus;

/** サインアップ要求(signup_requests)の永続化・照会ポート(ADR-0040 §3.3)。 */
public interface SignupRequestPort {

  /**
   * 当該 email の PENDING を REVOKED にしてから新しい PENDING を 1 件作成する(再要求時の旧トークン失効 + 発行を原子的に)。email
   * 存在有無で分岐しないため列挙耐性を損なわない。
   */
  void replacePending(
      String email, String tokenHash, LocalDateTime expiresAt, LocalDateTime createdAt);

  /** token_hash で 1 件引く(確認画面・complete 用)。 */
  Optional<SignupRequestView> findByTokenHash(String tokenHash);

  /** サインアップ要求を USED に遷移させ consumed_at を記録する(complete 確定)。 */
  void markUsed(Long signupRequestId, LocalDateTime consumedAt);

  /** 受諾/確認フローで参照するサインアップ要求ビュー(token_hash 引きの結果)。 */
  record SignupRequestView(
      Long id, String email, SignupRequestStatus status, LocalDateTime expiresAt) {}
}
