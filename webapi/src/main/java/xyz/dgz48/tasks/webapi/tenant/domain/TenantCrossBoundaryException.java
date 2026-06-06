package xyz.dgz48.tasks.webapi.tenant.domain;

import xyz.dgz48.tasks.webapi.shared.exception.DomainException;

/** Tenant Admin が所属テナント外のリソースを操作しようとした場合にスローする業務例外。HTTP 403 にマップする。 */
public class TenantCrossBoundaryException extends DomainException {

  public TenantCrossBoundaryException(Long tenantId) {
    super("テナント %d への操作権限がありません".formatted(tenantId));
  }
}
