package xyz.dgz48.tasks.webapi.tenant.adapter.web.dto;

import xyz.dgz48.tasks.webapi.tenant.domain.TenantRole;
import xyz.dgz48.tasks.webapi.tenant.usecase.ViewInvitationUseCase.DisplayStatus;
import xyz.dgz48.tasks.webapi.tenant.usecase.ViewInvitationUseCase.InvitationDetail;

/**
 * GET /api/invitations/{token} レスポンス(受諾画面用、ADR-0040 §3.3)。
 *
 * @param email 招待先メールアドレス
 * @param tenantName 参加先テナント名
 * @param role 付与されるロール
 * @param status 受諾可否状態(PENDING のみ受諾可能。EXPIRED/USED/REVOKED は案内表示)
 * @param alreadyRegistered 既に登録済みアカウントか(true ならログインして参加へ誘導)
 */
public record InvitationDetailResponse(
    String email,
    String tenantName,
    TenantRole role,
    DisplayStatus status,
    boolean alreadyRegistered) {

  public static InvitationDetailResponse from(InvitationDetail detail) {
    return new InvitationDetailResponse(
        detail.email(),
        detail.tenantName(),
        detail.role(),
        detail.status(),
        detail.alreadyRegistered());
  }
}
