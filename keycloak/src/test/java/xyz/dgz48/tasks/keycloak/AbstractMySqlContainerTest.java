package xyz.dgz48.tasks.keycloak;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import org.testcontainers.containers.Network;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * MySQL 8.4(本番 webapi の {@code users} スキーマ)を共有 singleton コンテナとして起動するテスト基盤。Keycloak を必要としない
 * component テスト(UserRepository / UserAdapter / Provider を JVM 内で直接駆動)と、Keycloak を載せる {@link
 * AbstractSpiContainerTest} の双方の土台になる。
 *
 * <p>component テストは SPI クラスをテスト JVM 内で実行するため JaCoCo の計測対象になる(Keycloak コンテナ内で動く統合テストは計測されない)。
 */
abstract class AbstractMySqlContainerTest {

  static final String DB_NAME = "tasks";
  static final String DB_USER = "tasks";
  static final String DB_PASS = "tasks";

  // Keycloak コンテナから alias "mysql" で参照できるよう共有 network に載せる(統合テスト用)。
  static final Network NETWORK = Network.newNetwork();

  @SuppressWarnings("resource") // singleton: Ryuk が JVM 終了時に停止する
  static final MySQLContainer MYSQL =
      new MySQLContainer(DockerImageName.parse("mysql:8.4"))
          .withNetwork(NETWORK)
          .withNetworkAliases("mysql")
          .withDatabaseName(DB_NAME)
          .withUsername(DB_USER)
          .withPassword(DB_PASS)
          .withInitScript("users-schema.sql");

  static {
    MYSQL.start();
  }

  /**
   * JVM/コンテナ横断で一意な識別子を返す。全テストクラスが同一 MySQL を共有するため、{@code oidc_sub} / {@code email} の UNIQUE
   * 制約衝突を避ける(クラスごとのカウンタはクラス間でリセットされ衝突する)。
   */
  static String unique(String base) {
    return base + "-" + java.util.UUID.randomUUID();
  }

  static Connection dbConnection() throws SQLException {
    return DriverManager.getConnection(MYSQL.getJdbcUrl(), DB_USER, DB_PASS);
  }

  static UserRepository newRepository() {
    return new UserRepository(MYSQL.getJdbcUrl(), DB_USER, DB_PASS);
  }

  /** {@code users} に有効な行を 1 件 seed し、生成された id を返す。 */
  static long seedUser(
      String oidcSub,
      String email,
      String fullName,
      String fullNameKana,
      String departmentName,
      String status)
      throws SQLException {
    String sql =
        "INSERT INTO users (oidc_sub, email, full_name, full_name_kana, department_name, status,"
            + " version, deleted_at) VALUES (?, ?, ?, ?, ?, ?, 0, NULL)";
    try (Connection c = dbConnection();
        PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
      ps.setString(1, oidcSub);
      ps.setString(2, email);
      ps.setString(3, fullName);
      ps.setString(4, fullNameKana);
      if (departmentName == null) {
        ps.setNull(5, java.sql.Types.VARCHAR);
      } else {
        ps.setString(5, departmentName);
      }
      ps.setString(6, status);
      ps.executeUpdate();
      try (ResultSet keys = ps.getGeneratedKeys()) {
        keys.next();
        return keys.getLong(1);
      }
    }
  }

  /** {@code users} の 1 行を email で取得する(削除済みも含めるため deleted_at は絞らない)。 */
  static UserRow fetchByEmail(String email) throws SQLException {
    return fetch("email = ?", email);
  }

  static UserRow fetchById(long id) throws SQLException {
    return fetch("id = ?", String.valueOf(id));
  }

  private static UserRow fetch(String where, String arg) throws SQLException {
    String sql =
        "SELECT id, oidc_sub, email, full_name, full_name_kana, department_name, status, version,"
            + " deleted_at FROM users WHERE "
            + where;
    try (Connection c = dbConnection();
        PreparedStatement ps = c.prepareStatement(sql)) {
      ps.setString(1, arg);
      try (ResultSet rs = ps.executeQuery()) {
        if (!rs.next()) {
          return null;
        }
        return new UserRow(
            rs.getLong("id"),
            rs.getString("oidc_sub"),
            rs.getString("email"),
            rs.getString("full_name"),
            rs.getString("full_name_kana"),
            rs.getString("department_name"),
            rs.getString("status"),
            rs.getLong("version"),
            rs.getTimestamp("deleted_at") != null);
      }
    }
  }

  /** 検証用の users 行スナップショット(deleted_at は存在有無を boolean で保持)。 */
  record UserRow(
      long id,
      String oidcSub,
      String email,
      String fullName,
      String fullNameKana,
      String departmentName,
      String status,
      long version,
      boolean deleted) {}

  /** 検証用の audit_logs 行スナップショット(#734)。 */
  record AuditRow(
      long id,
      Long tenantId,
      Long userId,
      String action,
      String entityType,
      Long entityId,
      String detail,
      String hashChain,
      LocalDateTime createdAt) {}

  /** 指定 entity_id の ANONYMIZE audit 行を取得する(無ければ null)。 */
  static AuditRow fetchAnonymizeAudit(long entityId) throws SQLException {
    String sql =
        "SELECT id, tenant_id, user_id, action, entity_type, entity_id, detail, hash_chain,"
            + " created_at FROM audit_logs WHERE action = 'ANONYMIZE' AND entity_id = ?";
    try (Connection c = dbConnection();
        PreparedStatement ps = c.prepareStatement(sql)) {
      ps.setLong(1, entityId);
      try (ResultSet rs = ps.executeQuery()) {
        if (!rs.next()) {
          return null;
        }
        Timestamp createdAt = rs.getTimestamp("created_at");
        return new AuditRow(
            rs.getLong("id"),
            (Long) rs.getObject("tenant_id"),
            (Long) rs.getObject("user_id"),
            rs.getString("action"),
            rs.getString("entity_type"),
            (Long) rs.getObject("entity_id"),
            rs.getString("detail"),
            rs.getString("hash_chain"),
            createdAt == null ? null : createdAt.toLocalDateTime());
      }
    }
  }

  /** 指定 entity_id の ANONYMIZE audit 行数を返す(冪等性検証用)。 */
  static int countAnonymizeAudit(long entityId) throws SQLException {
    String sql = "SELECT COUNT(*) FROM audit_logs WHERE action = 'ANONYMIZE' AND entity_id = ?";
    try (Connection c = dbConnection();
        PreparedStatement ps = c.prepareStatement(sql)) {
      ps.setLong(1, entityId);
      try (ResultSet rs = ps.executeQuery()) {
        rs.next();
        return rs.getInt(1);
      }
    }
  }
}
