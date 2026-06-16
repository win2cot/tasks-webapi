package xyz.dgz48.tasks.webapi.shared.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zaxxer.hikari.HikariConfig;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.rds.RdsUtilities;
import software.amazon.awssdk.services.rds.model.GenerateAuthenticationTokenRequest;

class RdsIamDataSourceConfigTest {

  private static final String JDBC_URL =
      "jdbc:mysql://db.tasks.internal:3306/tasks?sslMode=REQUIRED";
  private static final String USERNAME = "tasks_webapi";
  private static final String REGION = "ap-northeast-1";

  @Test
  void buildHikariConfig_maxLifetimeIsLessThan15Minutes() {
    RdsUtilities mockUtils = mock(RdsUtilities.class);

    HikariConfig config = buildConfig(JDBC_URL, USERNAME, REGION).buildHikariConfig(mockUtils);

    // IAM tokens expire in 15 min; maxLifetime must be below 900000 ms to ensure reconnects
    assertThat(config.getMaxLifetime()).isLessThan(900_000L);
  }

  @Test
  void buildHikariConfig_hasUnderlyingDataSource() {
    RdsUtilities mockUtils = mock(RdsUtilities.class);

    HikariConfig config = buildConfig(JDBC_URL, USERNAME, REGION).buildHikariConfig(mockUtils);

    assertThat(config.getDataSource()).isInstanceOf(RdsIamDriverDataSource.class);
  }

  @Test
  void buildHikariConfig_extractsHostAndPortFromJdbcUrl() throws Exception {
    RdsUtilities mockUtils = mock(RdsUtilities.class);
    when(mockUtils.generateAuthenticationToken(any(GenerateAuthenticationTokenRequest.class)))
        .thenReturn("mock-iam-token");

    var ds =
        (RdsIamDriverDataSource)
            buildConfig(JDBC_URL, USERNAME, REGION).buildHikariConfig(mockUtils).getDataSource();

    // Trigger token generation by attempting a connection (will fail at TCP level)
    assertThatThrownBy(ds::getConnection).isInstanceOf(SQLException.class);

    var captor = ArgumentCaptor.forClass(GenerateAuthenticationTokenRequest.class);
    verify(mockUtils).generateAuthenticationToken(captor.capture());
    assertThat(captor.getValue().hostname()).isEqualTo("db.tasks.internal");
    assertThat(captor.getValue().port()).isEqualTo(3306);
    assertThat(captor.getValue().username()).isEqualTo(USERNAME);
  }

  @Test
  void rdsIamDriverDataSource_generatesTokenBeforeConnection() {
    RdsUtilities mockUtils = mock(RdsUtilities.class);
    when(mockUtils.generateAuthenticationToken(any(GenerateAuthenticationTokenRequest.class)))
        .thenReturn("mock-iam-token");

    var driverDs =
        new RdsIamDriverDataSource(
            "jdbc:mysql://127.0.0.1:19999/tasks?connectTimeout=100",
            USERNAME,
            "127.0.0.1",
            19999,
            mockUtils);

    // Token IS generated before the connection attempt even though JDBC itself fails
    assertThatThrownBy(driverDs::getConnection).isInstanceOf(SQLException.class);
    verify(mockUtils).generateAuthenticationToken(any(GenerateAuthenticationTokenRequest.class));
  }

  @Test
  void buildHikariConfig_appendsSslModeWhenAbsent() throws Exception {
    RdsUtilities mockUtils = mock(RdsUtilities.class);

    var ds =
        (RdsIamDriverDataSource)
            buildConfig("jdbc:mysql://db.tasks.internal:3306/db", USERNAME, REGION)
                .buildHikariConfig(mockUtils)
                .getDataSource();

    var field = RdsIamDriverDataSource.class.getDeclaredField("jdbcUrl");
    field.setAccessible(true);
    assertThat((String) field.get(ds)).contains("sslMode=REQUIRED");
  }

  @Test
  void buildHikariConfig_doesNotDuplicateSslMode() throws Exception {
    RdsUtilities mockUtils = mock(RdsUtilities.class);

    var ds =
        (RdsIamDriverDataSource)
            buildConfig(JDBC_URL, USERNAME, REGION).buildHikariConfig(mockUtils).getDataSource();

    var field = RdsIamDriverDataSource.class.getDeclaredField("jdbcUrl");
    field.setAccessible(true);
    String url = (String) field.get(ds);
    assertThat(url.indexOf("sslMode")).isEqualTo(url.lastIndexOf("sslMode"));
  }

  // ── helpers ─────────────────────────────────────────────────────────────────

  private static RdsIamDataSourceConfig buildConfig(
      String jdbcUrl, String username, String region) {
    try {
      var config = new RdsIamDataSourceConfig();
      setField(config, "jdbcUrl", jdbcUrl);
      setField(config, "username", username);
      setField(config, "region", region);
      return config;
    } catch (ReflectiveOperationException e) {
      throw new AssertionError("Test setup failed", e);
    }
  }

  private static void setField(Object target, String name, String value)
      throws ReflectiveOperationException {
    var field = target.getClass().getDeclaredField(name);
    field.setAccessible(true);
    field.set(target, value);
  }
}
