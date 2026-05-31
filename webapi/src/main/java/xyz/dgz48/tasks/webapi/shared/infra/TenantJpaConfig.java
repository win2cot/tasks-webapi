package xyz.dgz48.tasks.webapi.shared.infra;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
class TenantJpaConfig {

  @Bean
  PlatformTransactionManager transactionManager(EntityManagerFactory emf) {
    return new TenantAwareJpaTransactionManager(emf);
  }
}
