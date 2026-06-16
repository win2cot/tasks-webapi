package xyz.dgz48.tasks.webapi.shared.infra;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.springframework.jdbc.datasource.AbstractDataSource;
import software.amazon.awssdk.services.rds.RdsUtilities;
import software.amazon.awssdk.services.rds.model.GenerateAuthenticationTokenRequest;

/**
 * HikariCP underlying DataSource that generates a fresh AWS RDS IAM auth token on each physical
 * connection request. Tokens expire in 15 minutes; callers must set HikariCP maxLifetime < 900000
 * ms so the pool recycles connections before their token expires.
 *
 * <p>{@link RdsUtilities} is injected so the class is testable without AWS credentials.
 */
class RdsIamDriverDataSource extends AbstractDataSource {

  private final String jdbcUrl;
  private final String username;
  private final String host;
  private final int port;
  private final RdsUtilities rdsUtilities;

  RdsIamDriverDataSource(
      String jdbcUrl, String username, String host, int port, RdsUtilities rdsUtilities) {
    this.jdbcUrl = jdbcUrl;
    this.username = username;
    this.host = host;
    this.port = port;
    this.rdsUtilities = rdsUtilities;
  }

  @Override
  public Connection getConnection() throws SQLException {
    String token =
        rdsUtilities.generateAuthenticationToken(
            GenerateAuthenticationTokenRequest.builder()
                .hostname(host)
                .port(port)
                .username(username)
                .build());
    return DriverManager.getConnection(jdbcUrl, username, token);
  }

  @Override
  public Connection getConnection(String username, String password) throws SQLException {
    return getConnection();
  }
}
