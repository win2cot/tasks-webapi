package xyz.dgz48.tasks.webapi.shared.infra;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@Configuration
@EnableJpaAuditing(
    dateTimeProviderRef = "clockDateTimeProvider",
    auditorAwareRef = "jpaAuditorAware")
class ClockConfig {

  @Bean
  @ConditionalOnMissingBean
  Clock clock() {
    return Clock.system(ZoneId.of("Asia/Tokyo"));
  }

  @Bean
  @ConditionalOnMissingBean(name = "clockDateTimeProvider")
  DateTimeProvider clockDateTimeProvider(Clock clock) {
    return () -> Optional.of(LocalDateTime.now(clock));
  }
}
