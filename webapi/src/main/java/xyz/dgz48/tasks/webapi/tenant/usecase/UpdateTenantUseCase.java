package xyz.dgz48.tasks.webapi.tenant.usecase;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.dgz48.tasks.webapi.audit.domain.AuditEventType;
import xyz.dgz48.tasks.webapi.audit.usecase.AuditLogPort;
import xyz.dgz48.tasks.webapi.shared.usecase.TenantFilterBypassService;
import xyz.dgz48.tasks.webapi.tenant.domain.FieldChange;
import xyz.dgz48.tasks.webapi.tenant.domain.Tenant;
import xyz.dgz48.tasks.webapi.tenant.domain.TenantAuditDiffDomainService;
import xyz.dgz48.tasks.webapi.tenant.domain.TenantNotFoundException;
import xyz.dgz48.tasks.webapi.tenant.domain.TenantUpdateCommand;

/**
 * A-06: テナント名更新ユースケース(SaaS Admin 専用)。
 *
 * <p>成功時に {@code TENANT_UPDATED} として {@code tenant_id=対象テナント id} で監査ログに記録する(ADR-0020 §3.1)。 差分は
 * ADR-0013 の field-by-field diff 形式で {@code detail} に記録する。
 */
@Service
@RequiredArgsConstructor
public class UpdateTenantUseCase {

  private final AdminTenantRepository adminTenantRepository;
  private final TenantFilterBypassService tenantFilterBypassService;
  private final TenantAuditDiffDomainService tenantAuditDiffDomainService;
  private final AuditLogPort auditLogPort;

  @Transactional
  public Tenant execute(Long tenantId, Long userId, TenantUpdateCommand cmd) {
    Tenant previous =
        tenantFilterBypassService
            .runAsSaaSAdmin(() -> adminTenantRepository.findById(tenantId))
            .orElseThrow(() -> new TenantNotFoundException(tenantId));

    List<FieldChange> changes = tenantAuditDiffDomainService.diff(previous, cmd);
    if (changes.isEmpty()) {
      return previous;
    }

    Tenant updated =
        tenantFilterBypassService.runAsSaaSAdmin(
            () -> adminTenantRepository.updateName(tenantId, cmd.name()));

    auditLogPort.record(AuditEventType.TENANT_UPDATED, tenantId, userId, buildDiffDetail(changes));

    return updated;
  }

  static String buildDiffDetail(List<FieldChange> changes) {
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < changes.size(); i++) {
      FieldChange c = changes.get(i);
      if (i > 0) sb.append(",");
      sb.append("{\"field\":\"")
          .append(c.field())
          .append("\",\"old\":")
          .append(toJsonValue(c.oldValue()))
          .append(",\"new\":")
          .append(toJsonValue(c.newValue()))
          .append("}");
    }
    sb.append("]");
    return sb.toString();
  }

  private static String toJsonValue(@Nullable Object value) {
    if (value == null) return "null";
    if (value instanceof String s) return "\"" + escapeJsonString(s) + "\"";
    if (value instanceof Enum<?> e) return "\"" + escapeJsonString(e.name()) + "\"";
    return String.valueOf(value);
  }

  private static String escapeJsonString(String s) {
    StringBuilder sb = new StringBuilder(s.length() + 16);
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      switch (c) {
        case '"' -> sb.append("\\\"");
        case '\\' -> sb.append("\\\\");
        case '\n' -> sb.append("\\n");
        case '\r' -> sb.append("\\r");
        case '\t' -> sb.append("\\t");
        case '\b' -> sb.append("\\b");
        case '\f' -> sb.append("\\f");
        default -> {
          if (c < 0x20) {
            sb.append(String.format("\\u%04X", (int) c));
          } else {
            sb.append(c);
          }
        }
      }
    }
    return sb.toString();
  }
}
