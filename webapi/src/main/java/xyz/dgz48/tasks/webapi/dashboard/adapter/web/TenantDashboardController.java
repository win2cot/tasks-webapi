package xyz.dgz48.tasks.webapi.dashboard.adapter.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import xyz.dgz48.tasks.webapi.dashboard.adapter.web.dto.TenantDashboardSummaryResponse;
import xyz.dgz48.tasks.webapi.dashboard.usecase.GetTenantDashboardSummaryUseCase;
import xyz.dgz48.tasks.webapi.shared.domain.TenantContext;

/**
 * S-15 テナント運営者向けダッシュボード API(A-28)。
 *
 * <p>認可は {@code hasRole('TENANT_ADMIN')} のみ。Member は 403、業務 API のため SaaS Admin(APP_ADMIN)も 403
 * (§6.2.1)。{@code X-Tenant-Id} ヘッダ必須。集計対象は {@code visibility ∈ {TENANT, STAKEHOLDERS}} のタスクのみで、
 * {@code PRIVATE} は件数も含めて除外する(ADR-0005 §3.5 / NIST AC-4)。S-03 個人視点({@link DashboardController})とは
 * 認可スコープと集計対象が異なる。
 */
@RestController
@RequestMapping("/api/tenant/dashboard")
@RequiredArgsConstructor
public class TenantDashboardController {

  private final GetTenantDashboardSummaryUseCase getTenantDashboardSummaryUseCase;

  @GetMapping("/summary")
  @PreAuthorize("hasRole('TENANT_ADMIN')")
  public ResponseEntity<TenantDashboardSummaryResponse> getTenantDashboardSummary() {
    Long tenantId = TenantContext.get();
    if (tenantId == null) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "テナントが選択されていません");
    }
    return ResponseEntity.ok(
        TenantDashboardSummaryResponse.from(getTenantDashboardSummaryUseCase.execute(tenantId)));
  }
}
