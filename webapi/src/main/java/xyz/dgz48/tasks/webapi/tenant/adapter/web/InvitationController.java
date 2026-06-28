package xyz.dgz48.tasks.webapi.tenant.adapter.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import xyz.dgz48.tasks.webapi.tenant.adapter.web.dto.AcceptInvitationRequest;
import xyz.dgz48.tasks.webapi.tenant.adapter.web.dto.AcceptInvitationResponse;
import xyz.dgz48.tasks.webapi.tenant.adapter.web.dto.InvitationDetailResponse;
import xyz.dgz48.tasks.webapi.tenant.usecase.AcceptInvitationUseCase;
import xyz.dgz48.tasks.webapi.tenant.usecase.AcceptInvitationUseCase.AcceptInvitationCommand;
import xyz.dgz48.tasks.webapi.tenant.usecase.ViewInvitationUseCase;

/**
 * 招待受諾フロー Controller(Flow A、ADR-0040 §3.3)。
 *
 * <p>受諾は未選択テナント・未認証で到達するため公開エンドポイント({@code SecurityConfig} で permitAll、{@code TenantContextFilter}
 * の免除パス)。 トークンが参加先テナントを保持するため {@code X-Tenant-Id} は不要。
 *
 * <p>未登録の招待先は会員登録 + 参加までを本フローで完了する。既に登録済みの email(別テナント所属など)は「ログインして参加」誘導(409)に留め、
 * 認証済みの参加紐付けは後続フローで実装する(本 Controller を security モジュールに依存させると security↔tenant が循環するため、principal
 * 読み取りは持たない)。
 */
@RestController
@RequiredArgsConstructor
public class InvitationController {

  private final ViewInvitationUseCase viewInvitationUseCase;
  private final AcceptInvitationUseCase acceptInvitationUseCase;

  /** 受諾画面用の招待照会(トークン非消費)。 */
  @GetMapping("/api/invitations/{token}")
  public InvitationDetailResponse getInvitation(@PathVariable String token) {
    return InvitationDetailResponse.from(viewInvitationUseCase.view(token));
  }

  /** 受諾確定(トークン消費)。未登録なら会員登録 + 参加。登録済み email は「ログインして参加」誘導(409)。 */
  @PostMapping("/api/invitations/{token}/accept")
  public AcceptInvitationResponse acceptInvitation(
      @PathVariable String token, @Valid @RequestBody AcceptInvitationRequest request) {
    AcceptInvitationCommand command =
        new AcceptInvitationCommand(
            request.fullName(),
            request.fullNameKana(),
            request.departmentName(),
            request.password());
    return AcceptInvitationResponse.from(acceptInvitationUseCase.accept(token, command));
  }
}
