package xyz.dgz48.tasks.webapi.security.adapter.web.dto;

import xyz.dgz48.tasks.webapi.tenant.domain.TenantRole;
import xyz.dgz48.tasks.webapi.tenant.domain.TenantSummaryInfo;

public record TenantSummaryDto(Long id, String code, String name, TenantRole role) {

  public static TenantSummaryDto from(TenantSummaryInfo src) {
    return new TenantSummaryDto(src.id(), src.code(), src.name(), src.role());
  }
}
