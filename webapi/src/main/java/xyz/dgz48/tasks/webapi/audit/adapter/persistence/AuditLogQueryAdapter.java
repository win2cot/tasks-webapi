package xyz.dgz48.tasks.webapi.audit.adapter.persistence;

import io.micrometer.observation.annotation.Observed;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import xyz.dgz48.tasks.webapi.audit.domain.AuditLogEntry;
import xyz.dgz48.tasks.webapi.audit.usecase.AuditLogPage;
import xyz.dgz48.tasks.webapi.audit.usecase.AuditLogQueryPort;
import xyz.dgz48.tasks.webapi.audit.usecase.AuditLogSearchCriteria;

/**
 * {@link AuditLogQueryPort} の JPA 実装。
 *
 * <p>{@code audit_logs} は {@code TenantFilteredEntity} 非適用のため、{@code tenant_id} をクエリで明示絞り込みする
 * (ADR-0010 §6.1 / ADR-0020 §3.4)。ソート(created_at 降順)は JPQL の {@code ORDER BY} で固定するため {@link
 * PageRequest} には sort を渡さない。
 */
@Observed(name = "audit.repository")
@Component
@RequiredArgsConstructor
class AuditLogQueryAdapter implements AuditLogQueryPort {

  private final AuditLogJpaRepository repository;

  @Override
  public AuditLogPage search(AuditLogSearchCriteria criteria) {
    Page<AuditLogJpaEntity> page =
        repository.search(
            criteria.tenantId(),
            criteria.from(),
            criteria.to(),
            criteria.action(),
            PageRequest.of(criteria.page(), criteria.size()));

    List<AuditLogEntry> content =
        page.getContent().stream().map(AuditLogQueryAdapter::toEntry).toList();
    return new AuditLogPage(content, page.getTotalElements());
  }

  private static AuditLogEntry toEntry(AuditLogJpaEntity e) {
    String detail = e.getDetail();
    return new AuditLogEntry(
        e.getId(),
        e.getUserId(),
        e.getAction(),
        e.getEntityType(),
        e.getEntityId(),
        detail == null || detail.isBlank() ? "{}" : detail,
        e.getIpAddress(),
        e.getCreatedAt());
  }
}
