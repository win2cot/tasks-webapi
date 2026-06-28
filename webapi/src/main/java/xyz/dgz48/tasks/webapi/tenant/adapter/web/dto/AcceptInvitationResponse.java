package xyz.dgz48.tasks.webapi.tenant.adapter.web.dto;

import xyz.dgz48.tasks.webapi.tenant.usecase.AcceptInvitationUseCase.AcceptInvitationResult;

/**
 * POST /api/invitations/{token}/accept レスポンス(ADR-0040 §3.3)。受諾後はログインして参加先テナントを使う。
 *
 * @param userId 参加したユーザー id
 * @param tenantId 参加先テナント id
 */
public record AcceptInvitationResponse(Long userId, Long tenantId) {

  public static AcceptInvitationResponse from(AcceptInvitationResult result) {
    return new AcceptInvitationResponse(result.userId(), result.tenantId());
  }
}
