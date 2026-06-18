package xyz.dgz48.tasks.webapi.security.adapter.web;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import xyz.dgz48.tasks.webapi.security.adapter.web.dto.MeResponse;
import xyz.dgz48.tasks.webapi.security.adapter.web.dto.TenantSummaryDto;
import xyz.dgz48.tasks.webapi.security.adapter.web.dto.UserProfileDto;
import xyz.dgz48.tasks.webapi.security.domain.TasksPrincipal;
import xyz.dgz48.tasks.webapi.security.usecase.GetMeUseCase;

/**
 * GET /api/auth/me — ログインユーザー情報・所属テナント一覧。
 *
 * <p>TenantContextFilter の免除パス({@code /api/auth/**})に該当するため X-Tenant-Id ヘッダは不要。 activeTenantId は
 * X-Tenant-Id 指定時にそれを返し、未指定時は null とする(自動解決 ADR-0016 は /me では行わない)。
 */
@RestController
@RequiredArgsConstructor
public class MeController {

  private final GetMeUseCase getMeUseCase;

  @GetMapping("/api/auth/me")
  public MeResponse me(
      @AuthenticationPrincipal TasksPrincipal principal,
      @RequestHeader(name = "X-Tenant-Id", required = false) @Nullable Long activeTenantId) {

    List<TenantSummaryDto> tenants =
        getMeUseCase.findTenants(principal.getId()).stream().map(TenantSummaryDto::from).toList();

    UserProfileDto userProfile =
        new UserProfileDto(
            principal.getId(),
            principal.getEmail(),
            principal.getFullName(),
            principal.getFullNameKana(),
            principal.getDepartmentName());

    return new MeResponse(userProfile, tenants, activeTenantId);
  }
}
