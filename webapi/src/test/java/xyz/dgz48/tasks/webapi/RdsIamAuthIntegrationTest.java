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
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
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
  @Container
  static MySQLContainer<?> mysql =
      new MySQLContainer<>("mysql:8.4")
          .withDatabaseName("tasks")
          .withUsername("tasks_webapi")
          .withPassword("tasks_webapi")
          .withCommand("--default-time-zone=Asia/Tokyo");

  @DynamicPropertySource
  static void datasourceProps(DynamicPropertyRegistry registry) {
    // Prepend sslMode=DISABLED so RdsIamDataSourceConfig.withSsl() leaves the URL unchanged.
    // Local Testcontainer MySQL uses self-signed certs; adding sslMode=REQUIRED causes cert-verify
    // failures that are irrelevant to what this test validates.
    String baseUrl = mysql.getJdbcUrl();
    String separator = baseUrl.contains("?") ? "&" : "?";
    registry.add("spring.datasource.url", () -> baseUrl + separator + "sslMode=DISABLED");
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
