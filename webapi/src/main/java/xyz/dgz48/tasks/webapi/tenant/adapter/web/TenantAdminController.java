package xyz.dgz48.tasks.webapi.tenant.adapter.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import xyz.dgz48.tasks.webapi.tenant.adapter.web.dto.PlatformMetricsResponse;
import xyz.dgz48.tasks.webapi.tenant.adapter.web.dto.TenantPageResponse;
import xyz.dgz48.tasks.webapi.tenant.adapter.web.dto.TenantResponse;
import xyz.dgz48.tasks.webapi.tenant.adapter.web.dto.TenantStatusUpdateRequest;
import xyz.dgz48.tasks.webapi.tenant.adapter.web.dto.TenantUpdateRequest;
import xyz.dgz48.tasks.webapi.tenant.domain.TenantStatus;
import xyz.dgz48.tasks.webapi.tenant.domain.TenantStatusUpdateCommand;
import xyz.dgz48.tasks.webapi.tenant.domain.TenantUpdateCommand;
import xyz.dgz48.tasks.webapi.tenant.usecase.GetPlatformMetricsUseCase;
import xyz.dgz48.tasks.webapi.tenant.usecase.GetTenantUseCase;
import xyz.dgz48.tasks.webapi.tenant.usecase.ListTenantsUseCase;
import xyz.dgz48.tasks.webapi.tenant.usecase.UpdateTenantStatusUseCase;
import xyz.dgz48.tasks.webapi.tenant.usecase.UpdateTenantUseCase;

/**
 * SaaS Admin 向けテナント管理 API + プラットフォームメトリクス API。
 *
 * <ul>
 *   <li>A-04: {@code GET /api/tenants} — テナント一覧
 *   <li>A-25: {@code GET /api/tenants/{id}} — テナント単体取得(SaaS Admin: 監査あり / 一般ユーザー: 監査なし)
 *   <li>A-06: {@code PUT /api/tenants/{id}} — テナント名更新
 *   <li>A-26: {@code PATCH /api/tenants/{id}/status} — テナント状態切替
 *   <li>A-27: {@code GET /api/platform/metrics} — プラットフォームメトリクス
 * </ul>
 */
@RestController
@RequiredArgsConstructor
public class TenantAdminController {

  private final ListTenantsUseCase listTenantsUseCase;
  private final GetTenantUseCase getTenantUseCase;
  private final UpdateTenantUseCase updateTenantUseCase;
  private final UpdateTenantStatusUseCase updateTenantStatusUseCase;
  private final GetPlatformMetricsUseCase getPlatformMetricsUseCase;

  /** A-04: テナント一覧(SaaS Admin 専用)。 */
  @GetMapping("/api/tenants")
  @PreAuthorize("hasRole('APP_ADMIN')")
  public TenantPageResponse listTenants(
      @AuthenticationPrincipal(expression = "id") Long userId,
      @RequestParam @Nullable TenantStatus status,
      @RequestParam @Nullable String keyword,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size) {
    int clampedSize = Math.min(size, 100);
    PageRequest pageable =
        PageRequest.of(page, clampedSize, Sort.by(Sort.Direction.DESC, "createdAt"));
    return TenantPageResponse.from(listTenantsUseCase.execute(userId, status, keyword, pageable));
  }

  /** A-25: テナント単体取得。SaaS Admin は監査あり、一般ユーザーは自テナントのみ参照可(監査なし)。 */
  @GetMapping("/api/tenants/{id}")
  public TenantResponse getTenant(
      @PathVariable Long id,
      @AuthenticationPrincipal(expression = "id") Long userId,
      Authentication authentication) {
    boolean isSaasAdmin =
        authentication.getAuthorities().stream()
            .anyMatch(a -> "ROLE_APP_ADMIN".equals(a.getAuthority()));
    if (isSaasAdmin) {
      return TenantResponse.from(getTenantUseCase.executeAsSaasAdmin(id, userId));
    }
    return TenantResponse.from(getTenantUseCase.executeAsUser(id, userId));
  }

  /** A-06: テナント名更新(SaaS Admin 専用)。 */
  @PutMapping("/api/tenants/{id}")
  @PreAuthorize("hasRole('APP_ADMIN')")
  public TenantResponse updateTenant(
      @PathVariable Long id,
      @AuthenticationPrincipal(expression = "id") Long userId,
      @RequestBody @Valid TenantUpdateRequest request) {
    return TenantResponse.from(
        updateTenantUseCase.execute(id, userId, new TenantUpdateCommand(request.name())));
  }

  /** A-26: テナント状態切替(SaaS Admin 専用)。 */
  @PatchMapping("/api/tenants/{id}/status")
  @PreAuthorize("hasRole('APP_ADMIN')")
  public TenantResponse updateTenantStatus(
      @PathVariable Long id,
      @AuthenticationPrincipal(expression = "id") Long userId,
      @RequestBody @Valid TenantStatusUpdateRequest request) {
    return TenantResponse.from(
        updateTenantStatusUseCase.execute(
            id, userId, new TenantStatusUpdateCommand(request.status())));
  }

  /** A-27: プラットフォームメトリクス(SaaS Admin 専用)。 */
  @GetMapping("/api/platform/metrics")
  @PreAuthorize("hasRole('APP_ADMIN')")
  public PlatformMetricsResponse getPlatformMetrics(
      @AuthenticationPrincipal(expression = "id") Long userId) {
    return PlatformMetricsResponse.from(getPlatformMetricsUseCase.execute(userId));
  }
}
