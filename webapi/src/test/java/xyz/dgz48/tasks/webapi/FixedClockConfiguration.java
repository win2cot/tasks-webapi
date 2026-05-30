package xyz.dgz48.tasks.webapi;

import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.auditing.DateTimeProvider;

@TestConfiguration
public class FixedClockConfiguration {

  /** JST 2026-01-15T19:00:00 (= UTC 2026-01-15T10:00:00 + 9h) */
  public static final LocalDateTime FIXED_NOW = LocalDateTime.of(2026, 1, 15, 19, 0, 0);

  @Bean("clockDateTimeProvider")
  @Primary
  public DateTimeProvider clockDateTimeProvider() {
    return () -> Optional.of(FIXED_NOW);
  }
}
