package xyz.dgz48.tasks.webapi;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.aot.DisabledInAotMode;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import software.amazon.awssdk.services.rds.RdsUtilities;
import software.amazon.awssdk.services.rds.model.GenerateAuthenticationTokenRequest;

/**
 * Verifies that when {@code rds.iam-auth.enabled=true} the full stack — RdsIamDataSourceConfig →
 * RdsIamDriverDataSource → HikariCP → Flyway — loads and migrates without error on JVM.
 *
 * <p>RdsUtilities is mocked to return the Testcontainer MySQL password as the "IAM token". This
 * makes MySQL accept the token as a plain password, exercising the entire DataSource wiring path
 * without requiring a real RDS endpoint.
 *
 * <p>{@code @DisabledInAotMode} prevents processTestAot from trying to evaluate
 * {@code @DynamicPropertySource} expressions (which reference container ports) before the
 * Testcontainer is started.
 *
 * <p>GraalVM native image reflection issues for the AWS SDK are caught separately by the smoke test
 * script ({@code scripts/smoke-test-native.sh}).
 */
@SpringBootTest
@Testcontainers
@DisabledInAotMode
@Import({MockJwtDecoderConfiguration.class, RdsIamAuthIntegrationTest.MockRdsUtilitiesConfig.class})
@org.springframework.test.context.TestPropertySource(
    properties = {
      "rds.iam-auth.enabled=true",
      // Allow MockRdsUtilitiesConfig.rdsUtilities() to override
      // RdsIamDataSourceConfig.rdsUtilities().
      // RdsIamDataSourceConfig is scanned first (classpath), so the test mock registers second and
      // wins when overriding is enabled.
      "spring.main.allow-bean-definition-overriding=true"
    })
class RdsIamAuthIntegrationTest {

  // Use tasks_webapi / tasks_webapi so the mock token equals the actual MySQL password.
  // @Container manages the container lifecycle (start/stop), so it is never explicitly closed here;
  // suppress the false-positive resource-leak diagnostic.
  @SuppressWarnings("resource")
  @Container
  static MySQLContainer mysql =
      new MySQLContainer("mysql:8.4")
          .withDatabaseName("tasks")
          .withUsername("tasks_webapi")
          .withPassword("tasks_webapi")
          .withCommand("--default-time-zone=Asia/Tokyo");

  @DynamicPropertySource
  static void datasourceProps(DynamicPropertyRegistry registry) {
    // RdsIamDataSourceConfig reads DB_HOST / DB_PORT directly and builds the IAM JDBC URL
    // (sslMode=REQUIRED) internally. MySQL 8.4 in Docker supports TLS by default, so
    // sslMode=REQUIRED works without additional certificate configuration.
    registry.add("DB_HOST", mysql::getHost);
    registry.add("DB_PORT", () -> mysql.getMappedPort(3306));
    registry.add("spring.datasource.username", mysql::getUsername);
    // spring.datasource.password is intentionally omitted — RdsIamDataSourceConfig does not read
    // it. The mock RdsUtilities returns the password directly as the IAM token.
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class MockRdsUtilitiesConfig {
    @Bean
    @Primary
    RdsUtilities rdsUtilities() {
      var mockUtils = mock(RdsUtilities.class);
      when(mockUtils.generateAuthenticationToken(any(GenerateAuthenticationTokenRequest.class)))
          .thenReturn("tasks_webapi");
      return mockUtils;
    }
  }

  @Test
  void contextLoads_withRdsIamAuthEnabled() {
    // Reaching here proves:
    // 1. RdsIamDataSourceConfig activated (rds.iam-auth.enabled=true)
    // 2. HikariCP pool initialized via RdsIamDriverDataSource
    // 3. Mock RdsUtilities returned the token and MySQL accepted it as the password
    // 4. Flyway migrations completed successfully
  }
}
