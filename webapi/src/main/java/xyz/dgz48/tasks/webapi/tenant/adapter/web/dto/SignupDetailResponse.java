package xyz.dgz48.tasks.webapi.tenant.adapter.web.dto;

import xyz.dgz48.tasks.webapi.tenant.usecase.ViewSignupUseCase.DisplayStatus;
import xyz.dgz48.tasks.webapi.tenant.usecase.ViewSignupUseCase.SignupDetail;

/**
 * GET /api/signup/{token} レスポンス(確認画面用、ADR-0040 §3.3)。
 *
 * @param email 確認対象メールアドレス
 * @param status 完了可否状態(PENDING のみ完了可能。EXPIRED/USED/REVOKED は案内表示)
 */
public record SignupDetailResponse(String email, DisplayStatus status) {

  public static SignupDetailResponse from(SignupDetail detail) {
    return new SignupDetailResponse(detail.email(), detail.status());
  }
}
