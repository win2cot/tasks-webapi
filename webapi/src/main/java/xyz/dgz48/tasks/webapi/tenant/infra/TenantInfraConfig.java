package xyz.dgz48.tasks.webapi.tenant.infra;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import xyz.dgz48.tasks.webapi.tenant.domain.TenantAuditDiffDomainService;

@Configuration
class TenantInfraConfig {

  @Bean
  TenantAuditDiffDomainService tenantAuditDiffDomainService() {
    return new TenantAuditDiffDomainService();
  }
}
