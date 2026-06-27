package xyz.dgz48.tasks.webapi.tenant.infra;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import xyz.dgz48.tasks.webapi.notification.usecase.EmailSenderPort;
import xyz.dgz48.tasks.webapi.tenant.adapter.external.InviteMailAdapter;
import xyz.dgz48.tasks.webapi.tenant.domain.TenantAuditDiffDomainService;
import xyz.dgz48.tasks.webapi.tenant.domain.TenantCodeGenerator;
import xyz.dgz48.tasks.webapi.tenant.usecase.InviteMailPort;
import xyz.dgz48.tasks.webapi.tenant.usecase.InviteTokenGenerator;

@Configuration
@EnableConfigurationProperties(InviteProperties.class)
class TenantInfraConfig {

  @Bean
  TenantAuditDiffDomainService tenantAuditDiffDomainService() {
    return new TenantAuditDiffDomainService();
  }

  @Bean
  TenantCodeGenerator tenantCodeGenerator() {
    return new TenantCodeGenerator();
  }

  @Bean
  InviteTokenGenerator inviteTokenGenerator() {
    return new SecureRandomInviteTokenGenerator();
  }

  @Bean
  InviteMailPort inviteMailPort(EmailSenderPort emailSenderPort, InviteProperties properties) {
    return new InviteMailAdapter(emailSenderPort, properties.acceptUrlBase());
  }
}
