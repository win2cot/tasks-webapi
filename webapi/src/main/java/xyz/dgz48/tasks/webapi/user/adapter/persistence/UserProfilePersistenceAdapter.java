package xyz.dgz48.tasks.webapi.user.adapter.persistence;

import io.micrometer.observation.annotation.Observed;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import xyz.dgz48.tasks.webapi.user.domain.UserProfile;
import xyz.dgz48.tasks.webapi.user.usecase.UserProfilePort;

/** {@link UserProfilePort} の JPA 実装。{@code users} を ID で読み取り {@link UserProfile} へ射影する。 */
@Observed(name = "user.profile.repository")
@Component
@RequiredArgsConstructor
class UserProfilePersistenceAdapter implements UserProfilePort {

  private final UserRepository userRepository;

  @Override
  public Optional<UserProfile> findById(Long userId) {
    return userRepository.findById(userId).map(UserProfilePersistenceAdapter::toProfile);
  }

  private static UserProfile toProfile(UserJpaEntity e) {
    return new UserProfile(
        e.getId(), e.getEmail(), e.getFullName(), e.getFullNameKana(), e.getDepartmentName());
  }
}
