package xyz.dgz48.tasks.webapi.shared.infra;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.rds.RdsUtilities;

/**
 * Activates when {@code rds.iam-auth.enabled=true} (set via {@code RDS_IAM_AUTH_ENABLED} env var in
 * ECS). Replaces Spring Boot's auto-configured DataSource with a HikariCP pool whose physical
 * connections are opened with a fresh IAM auth token on each reconnect.
 *
 * <p>IAM tokens expire in 15 minutes. {@code maxLifetime=840000} (14 min) ensures HikariCP discards
 * connections before their token expires, causing {@link RdsIamDriverDataSource} to generate a new
 * token for each replacement connection.
 *
 * <p>{@code processAot} must set {@code RDS_IAM_AUTH_ENABLED=true} so this condition resolves to
 * {@code true} during AOT processing and the bean is included in the GraalVM native image.
 */
@Configuration
@ConditionalOnProperty(name = "rds.iam-auth.enabled", havingValue = "true")
class RdsIamDataSourceConfig {

  @Value("${DB_HOST}")
  private String dbHost;

  @Value("${DB_PORT:3306}")
  private int dbPort;

  @Value("${spring.datasource.username}")
  private String username;

  @Value("${rds.iam-auth.region:ap-northeast-1}")
  private String region;

  @Bean
  RdsUtilities rdsUtilities() {
    return RdsUtilities.builder()
        .region(Region.of(region))
        .credentialsProvider(DefaultCredentialsProvider.create())
        .build();
  }

  @Bean
  DataSource dataSource(RdsUtilities rdsUtilities) {
    return new HikariDataSource(buildHikariConfig(rdsUtilities));
  }

  HikariConfig buildHikariConfig(RdsUtilities rdsUtilities) {
    String jdbcUrl =
        "jdbc:mysql://"
            + dbHost
            + ":"
            + dbPort
            + "/tasks?sslMode=REQUIRED&connectionTimeZone=SERVER&forceConnectionTimeZoneToSession=true";

    HikariConfig config = new HikariConfig();
    config.setDataSource(
        new RdsIamDriverDataSource(jdbcUrl, username, dbHost, dbPort, rdsUtilities));
    config.setMaxLifetime(840_000L);
    return config;
  }
}
