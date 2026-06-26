package xyz.dgz48.tasks.webapi.notification.adapter.web;

import jakarta.validation.Valid;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xyz.dgz48.tasks.webapi.notification.adapter.web.dto.NotificationSettingsResponse;
import xyz.dgz48.tasks.webapi.notification.adapter.web.dto.NotificationSettingsUpdateRequest;
import xyz.dgz48.tasks.webapi.notification.usecase.GetNotificationSettingsUseCase;
import xyz.dgz48.tasks.webapi.notification.usecase.UpdateNotificationSettingsCommand;
import xyz.dgz48.tasks.webapi.notification.usecase.UpdateNotificationSettingsUseCase;
import xyz.dgz48.tasks.webapi.shared.domain.TenantContext;

/**
 * 通知設定 API(A-23 取得 / A-24 更新、S-10)。
 *
 * <p>ログイン中ユーザー自身の、現在のテナント({@code X-Tenant-Id})における設定を扱う。業務 API のため {@code MEMBER} / {@code
 * TENANT_ADMIN} のみ許可し、SaaS Admin は 403(§6.2.1)。テナント分離は Hibernate Filter(ADR-0010)。
 */
@RestController
@RequestMapping("/api/users/me/notification-settings")
@RequiredArgsConstructor
public class NotificationSettingsController {

  private final GetNotificationSettingsUseCase getNotificationSettingsUseCase;
  private final UpdateNotificationSettingsUseCase updateNotificationSettingsUseCase;

  @GetMapping
  @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'MEMBER')")
  public ResponseEntity<NotificationSettingsResponse> get(
      @AuthenticationPrincipal(expression = "id") Long userId) {
    return ResponseEntity.ok(
        NotificationSettingsResponse.from(getNotificationSettingsUseCase.execute(userId)));
  }

  @PutMapping
  @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'MEMBER')")
  public ResponseEntity<NotificationSettingsResponse> update(
      @AuthenticationPrincipal(expression = "id") Long userId,
      @Valid @RequestBody NotificationSettingsUpdateRequest request) {
    // TenantContextFilter が業務 API 到達前に X-Tenant-Id + ACTIVE メンバーシップを検証済み(未確立なら 403)。
    Long tenantId =
        Objects.requireNonNull(TenantContext.get(), "TenantContext must be set by filter");
    UpdateNotificationSettingsCommand command =
        new UpdateNotificationSettingsCommand(
            userId,
            tenantId,
            request.emailDueToday(),
            request.emailOverdue(),
            request.emailStakeholder());
    return ResponseEntity.ok(
        NotificationSettingsResponse.from(updateNotificationSettingsUseCase.execute(command)));
  }
}
