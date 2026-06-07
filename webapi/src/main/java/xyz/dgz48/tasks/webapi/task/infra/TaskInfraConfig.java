package xyz.dgz48.tasks.webapi.task.infra;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import xyz.dgz48.tasks.webapi.task.domain.TaskAuditDiffDomainService;
import xyz.dgz48.tasks.webapi.task.domain.TaskAuthorizationDomainService;

@Configuration
class TaskInfraConfig {

  @Bean
  TaskAuthorizationDomainService taskAuthorizationDomainService() {
    return new TaskAuthorizationDomainService();
  }

  @Bean
  TaskAuditDiffDomainService taskAuditDiffDomainService() {
    return new TaskAuditDiffDomainService();
  }
}
