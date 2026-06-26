package xyz.dgz48.tasks.webapi.user.adapter.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import xyz.dgz48.tasks.webapi.user.adapter.web.dto.UserProfileResponse;
import xyz.dgz48.tasks.webapi.user.usecase.GetMyProfileUseCase;

/**
 * プロフィール API(A-07、S-09)。
 *
 * <p>{@code GET /api/users/me} はログイン中ユーザー自身のプロフィールを返す。テナント選択状態に依存しない({@code X-Tenant-Id} 不要、{@code
 * TenantContextFilter} の免除パス)ため、テナント未所属・未選択でも参照できる。認証済みであれば全ロールで利用可。 所属テナント情報を含む {@code
 * /api/auth/me} とは用途が異なる。
 */
@RestController
@RequiredArgsConstructor
public class UserProfileController {

  private final GetMyProfileUseCase getMyProfileUseCase;

  @GetMapping("/api/users/me")
  public ResponseEntity<UserProfileResponse> getMyProfile(
      @AuthenticationPrincipal(expression = "id") Long userId) {
    return ResponseEntity.ok(UserProfileResponse.from(getMyProfileUseCase.execute(userId)));
  }
}
