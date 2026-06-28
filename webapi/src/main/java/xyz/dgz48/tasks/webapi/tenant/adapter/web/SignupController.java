package xyz.dgz48.tasks.webapi.tenant.adapter.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import xyz.dgz48.tasks.webapi.tenant.adapter.web.dto.SignupCompleteRequest;
import xyz.dgz48.tasks.webapi.tenant.adapter.web.dto.SignupCompleteResponse;
import xyz.dgz48.tasks.webapi.tenant.adapter.web.dto.SignupDetailResponse;
import xyz.dgz48.tasks.webapi.tenant.adapter.web.dto.SignupRequestRequest;
import xyz.dgz48.tasks.webapi.tenant.adapter.web.dto.SignupRequestResponse;
import xyz.dgz48.tasks.webapi.tenant.usecase.CompleteSignupUseCase;
import xyz.dgz48.tasks.webapi.tenant.usecase.CompleteSignupUseCase.CompleteSignupCommand;
import xyz.dgz48.tasks.webapi.tenant.usecase.RequestSignupUseCase;
import xyz.dgz48.tasks.webapi.tenant.usecase.ViewSignupUseCase;

/**
 * セルフサインアップ(Flow B、double opt-in、ADR-0040 §3.3)Controller。
 *
 * <p>すべて公開エンドポイント({@code SecurityConfig} で permitAll、{@code TenantContextFilter}
 * の免除パス)。サインアップは未認証・テナント 未所属で到達するため {@code X-Tenant-Id} は不要。完了後はログイン → 既存 {@code POST /api/tenants}
 * でテナント作成へ続く。
 */
@RestController
@RequiredArgsConstructor
public class SignupController {

  private final RequestSignupUseCase requestSignupUseCase;
  private final ViewSignupUseCase viewSignupUseCase;
  private final CompleteSignupUseCase completeSignupUseCase;

  /** サインアップ要求(email のみ)。確認メールを送る。email 存在有無に関わらず常に同一レスポンス(列挙耐性)。 */
  @PostMapping("/api/signup/request")
  public SignupRequestResponse requestSignup(@Valid @RequestBody SignupRequestRequest request) {
    requestSignupUseCase.request(request.email());
    return SignupRequestResponse.accepted();
  }

  /** 確認画面用の照会(トークン非消費)。 */
  @GetMapping("/api/signup/{token}")
  public SignupDetailResponse getSignup(@PathVariable String token) {
    return SignupDetailResponse.from(viewSignupUseCase.view(token));
  }

  /** サインアップ確定(トークン消費)。会員登録(profile + Keycloak credential)を行う。 */
  @PostMapping("/api/signup/{token}/complete")
  public SignupCompleteResponse completeSignup(
      @PathVariable String token, @Valid @RequestBody SignupCompleteRequest request) {
    CompleteSignupCommand command =
        new CompleteSignupCommand(
            request.fullName(),
            request.fullNameKana(),
            request.departmentName(),
            request.password());
    return new SignupCompleteResponse(completeSignupUseCase.complete(token, command));
  }
}
