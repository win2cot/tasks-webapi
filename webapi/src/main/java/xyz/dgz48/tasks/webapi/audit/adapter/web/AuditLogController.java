package xyz.dgz48.tasks.webapi.audit.adapter.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.json.JsonMapper;
import xyz.dgz48.tasks.webapi.audit.adapter.web.dto.AuditLogPageResponse;
import xyz.dgz48.tasks.webapi.audit.adapter.web.dto.AuditLogResponse;
import xyz.dgz48.tasks.webapi.audit.usecase.AuditLogPage;
import xyz.dgz48.tasks.webapi.audit.usecase.AuditLogSearchCriteria;
import xyz.dgz48.tasks.webapi.audit.usecase.ListAuditLogsUseCase;
import xyz.dgz48.tasks.webapi.shared.domain.TenantContext;
import xyz.dgz48.tasks.webapi.shared.infra.AppZones;

/**
 * 監査ログ参照 API(A-22、operationId: listAuditLogs)。
 *
 * <p>認可は {@code hasRole('TENANT_ADMIN')} のみ(Member / SaaS Admin は 403)。{@code X-Tenant-Id} 必須。
 * 参照スコープは {@code audit_logs.tenant_id = 現在のテナント}(SaaS Admin の対象テナント操作も含む。横断操作 {@code
 * tenant_id=NULL} は対象外。ADR-0020 §3.4)。画面なし(API のみ)。
 */
@RestController
@RequestMapping("/api/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

  private final ListAuditLogsUseCase listAuditLogsUseCase;
  private final JsonMapper jsonMapper;

  @GetMapping
  @PreAuthorize("hasRole('TENANT_ADMIN')")
  public ResponseEntity<AuditLogPageResponse> listAuditLogs(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          @Nullable OffsetDateTime from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          @Nullable OffsetDateTime to,
      @RequestParam(required = false) @Pattern(regexp = "^[A-Z][A-Z0-9_]*$")
          @Nullable String action,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
    // TenantContextFilter が業務 API 到達前に X-Tenant-Id + ACTIVE メンバーシップを検証済み(未確立なら 403)。
    Long tenantId =
        Objects.requireNonNull(TenantContext.get(), "TenantContext must be set by filter");

    AuditLogSearchCriteria criteria =
        new AuditLogSearchCriteria(tenantId, toJst(from), toJst(to), action, page, size);
    AuditLogPage result = listAuditLogsUseCase.execute(criteria);

    List<AuditLogResponse> content =
        result.content().stream().map(entry -> AuditLogResponse.from(entry, jsonMapper)).toList();
    return ResponseEntity.ok(new AuditLogPageResponse(content, result.totalElements()));
  }

  /**
   * クエリの OffsetDateTime を、created_at(JST の LocalDateTime、ADR-0009)と比較するため同一インスタントの JST 壁時計へ変換する。
   */
  private static @Nullable LocalDateTime toJst(@Nullable OffsetDateTime value) {
    return value == null ? null : value.atZoneSameInstant(AppZones.JST).toLocalDateTime();
  }
}
