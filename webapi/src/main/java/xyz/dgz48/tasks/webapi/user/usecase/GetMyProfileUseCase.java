package xyz.dgz48.tasks.webapi.user.usecase;

import io.micrometer.observation.annotation.Observed;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.dgz48.tasks.webapi.user.domain.UserProfile;

/**
 * プロフィール取得(A-07、operationId: getMyProfile)のユースケース。
 *
 * <p>ログイン中ユーザー自身のプロフィールを {@code users} から取得する。テナント選択状態に依存しない。認証済みユーザーは必ず {@code users}
 * 行を持つ(ログイン時に作成・更新)ため、未存在は不変条件違反として例外とする。
 */
@Service
@RequiredArgsConstructor
public class GetMyProfileUseCase {

  private final UserProfilePort userProfilePort;

  @Observed(name = "user.profile.get")
  @Transactional(readOnly = true)
  public UserProfile execute(Long userId) {
    return userProfilePort
        .findById(userId)
        .orElseThrow(() -> new IllegalStateException("認証済みユーザーのプロフィールが見つかりません: userId=" + userId));
  }
}
