package xyz.dgz48.tasks.webapi.security.usecase;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.dgz48.tasks.webapi.user.adapter.persistence.UserJpaEntity;
import xyz.dgz48.tasks.webapi.user.adapter.persistence.UserRepository;

/**
 * JWT の {@code sub} から登録ユーザーを解決し、必要なら初回ログインの oidc_sub correlation を行う(ADR-0040 §3.2 / ADR-0006
 * §3.2)。
 *
 * <p>{@code findByOidcSub} がヒットすれば correlation 済み(通常ログイン)。未ヒット時は {@code email} クレームで {@code
 * pending:<email>} placeholder 行を突合し、本物の {@code sub} を書き戻す。read-modify-write
 * は単一トランザクションで保護し、同一ユーザーの並行初回ログイン(同一 JWT の二重送信など)で発生し得る楽観ロック競合は、相手の commit 後に {@code sub} で再 lookup
 * することで透過的に解決する(認証経路に {@code OptimisticLockingFailureException} を素通りさせて HTTP 500 にしない)。
 *
 * <p>email が一致しても correlation 済み(別 Keycloak アカウントの {@code sub} に紐付く)/匿名化済みの行は突合しない(なりすまし防止)。
 */
@Service
@RequiredArgsConstructor
public class OidcSubCorrelationService {

  private final UserRepository userRepository;

  @Transactional
  public Optional<UserJpaEntity> resolve(String sub, @Nullable String email) {
    Optional<UserJpaEntity> bySub = userRepository.findByOidcSub(sub);
    if (bySub.isPresent()) {
      return bySub;
    }
    if (email == null || email.isBlank()) {
      return Optional.empty();
    }
    Optional<UserJpaEntity> byEmail = userRepository.findByEmail(email);
    if (byEmail.isEmpty() || !byEmail.get().isPendingCorrelation()) {
      return Optional.empty();
    }
    UserJpaEntity user = byEmail.get();
    user.correlateOidcSub(sub);
    try {
      // saveAndFlush でこのメソッド内に flush を強制し、競合をここで捕捉する(commit 時に逃がさない)。
      return Optional.of(userRepository.saveAndFlush(user));
    } catch (OptimisticLockingFailureException | DataIntegrityViolationException e) {
      // 並行初回ログイン: 競合相手が先に同じ sub へ correlation 済み。再 lookup で解決する。
      return userRepository.findByOidcSub(sub);
    }
  }
}
