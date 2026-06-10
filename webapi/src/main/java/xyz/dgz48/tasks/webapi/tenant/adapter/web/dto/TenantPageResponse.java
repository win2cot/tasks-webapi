package xyz.dgz48.tasks.webapi.tenant.adapter.web.dto;

import java.util.List;
import org.springframework.data.domain.Page;
import xyz.dgz48.tasks.webapi.tenant.domain.Tenant;

/** テナント一覧ページングレスポンス(OpenAPI TenantPage スキーマ)。 */
public record TenantPageResponse(
    List<TenantResponse> content, long totalElements, int totalPages, int number, int size) {

  public static TenantPageResponse from(Page<Tenant> page) {
    return new TenantPageResponse(
        page.getContent().stream().map(TenantResponse::from).toList(),
        page.getTotalElements(),
        page.getTotalPages(),
        page.getNumber(),
        page.getSize());
  }
}
