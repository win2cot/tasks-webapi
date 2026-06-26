package xyz.dgz48.tasks.webapi.user.usecase;

import java.util.Optional;
import xyz.dgz48.tasks.webapi.user.domain.UserProfile;

/**
 * プロフィール取得ポート(クリーンアーキの out port)。
 *
 * <p>実装は adapter.persistence。{@code users} はプラットフォーム横断(テナント分離対象外)テーブルのため、{@code id} での単純読取とする。
 */
public interface UserProfilePort {

  /** ユーザー ID でプロフィールを取得する(未存在なら空)。 */
  Optional<UserProfile> findById(Long userId);
}
