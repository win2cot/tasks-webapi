package xyz.dgz48.tasks.webapi.user.adapter.web.dto;

import org.jspecify.annotations.Nullable;
import xyz.dgz48.tasks.webapi.user.domain.UserProfile;

/** OpenAPI {@code UserProfile} に対応するプロフィールレスポンス(A-07、S-09)。 */
public record UserProfileResponse(
    Long id, String email, String fullName, String fullNameKana, @Nullable String departmentName) {

  public static UserProfileResponse from(UserProfile profile) {
    return new UserProfileResponse(
        profile.id(),
        profile.email(),
        profile.fullName(),
        profile.fullNameKana(),
        profile.departmentName());
  }
}
