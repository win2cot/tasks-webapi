package xyz.dgz48.tasks.webapi.user.adapter.persistence;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import xyz.dgz48.tasks.webapi.user.domain.UserAlreadyRegisteredException;
import xyz.dgz48.tasks.webapi.user.usecase.UserRegistrationPort;

/**
 * {@link UserRegistrationPort} の永続化アダプタ(ADR-0040 §3.3 ①)。
 *
 * <p>会員登録は remote 呼び出し(Keycloak credential 設定)を跨ぐため呼び出し元ユースケースを {@code @Transactional} にできない。本書込の
 * tx 境界はこのメソッドに置き、credential 設定の前に commit する。
 */
@Component
@RequiredArgsConstructor
public class UserRegistrationPersistenceAdapter implements UserRegistrationPort {

  private final UserRepository userRepository;

  @Override
  @Transactional(readOnly = false)
  public Long upsertPendingMember(
      String email, String fullName, String fullNameKana, @Nullable String departmentName) {
    UserJpaEntity entity =
        userRepository
            .findByEmail(email)
            .map(existing -> updateExisting(existing, fullName, fullNameKana, departmentName))
            .orElseGet(
                () ->
                    new UserJpaEntity(
                        "pending:" + email, email, fullName, fullNameKana, departmentName));
    return userRepository.save(entity).getId();
  }

  private UserJpaEntity updateExisting(
      UserJpaEntity existing,
      String fullName,
      String fullNameKana,
      @Nullable String departmentName) {
    if (!existing.isPendingCorrelation()) {
      throw new UserAlreadyRegisteredException();
    }
    existing.updateProfile(fullName, fullNameKana, departmentName);
    return existing;
  }
}
