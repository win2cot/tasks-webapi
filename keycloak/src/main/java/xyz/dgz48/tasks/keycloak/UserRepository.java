package xyz.dgz48.tasks.keycloak;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.keycloak.models.ModelException;

/**
 * tasks-webapi の {@code users} テーブルへの JDBC データアクセス(ADR-0006 §3.5)。
 *
 * <p>SPI は {@code users} を read-only の profile ディレクトリとして federate する。read は常に {@code deleted_at IS
 * NULL} で絞るため、匿名化済み行(§3.4)は解決されない(=削除済み user の認証は拒否される)。{@code NO_CACHE} 方針に従い、各操作は都度 DB を hit
 * する。{@link Connection} は遅延生成し、所有元 provider インスタンスの生存期間(Keycloak の 1 リクエスト)中だけ再利用したのち {@link
 * #close()} で破棄する。
 *
 * <p>write は ADR-0006 §3.1 の writable 範囲に限定する: {@code email} の書き戻し(Update-Email の到達検証)、Custom User
 * Profile attribute 経路での {@code full_name_kana} / {@code department_name}(§3.1 Console 作成)、{@code
 * addUser} の insert(admin / リカバリ)、{@code removeUser} の匿名化(§3.4)。すべての update は {@code version} 列(JPA
 * {@code @Version} 相当)で楽観排他し、lost update は {@link ModelException} として表面化する。
 */
final class UserRepository implements AutoCloseable {

  /**
   * 削除されていない {@code users} 行の不変キャリア。
   *
   * @param departmentName {@code users.department_name} スキーマに合わせて null 許容
   */
  record UserRow(
      long id,
      String oidcSub,
      String email,
      String fullName,
      String fullNameKana,
      @Nullable String departmentName,
      String status,
      long version) {}

  private static final String SELECT_COLUMNS =
      "id, oidc_sub, email, full_name, full_name_kana, department_name, status, version";

  private final String jdbcUrl;
  private final String username;
  private final String password;

  private @Nullable Connection connection;

  UserRepository(String jdbcUrl, String username, String password) {
    this.jdbcUrl = jdbcUrl;
    this.username = username;
    this.password = password;
  }

  // --- read(deleted_at IS NULL のみ) ---

  Optional<UserRow> findById(long id) {
    String sql = "SELECT " + SELECT_COLUMNS + " FROM users WHERE id = ? AND deleted_at IS NULL";
    try (PreparedStatement ps = connection().prepareStatement(sql)) {
      ps.setLong(1, id);
      return single(ps);
    } catch (SQLException e) {
      throw new ModelException("tasks-webapi user store: findById failed", e);
    }
  }

  Optional<UserRow> findByEmail(String email) {
    String sql = "SELECT " + SELECT_COLUMNS + " FROM users WHERE email = ? AND deleted_at IS NULL";
    try (PreparedStatement ps = connection().prepareStatement(sql)) {
      ps.setString(1, email);
      return single(ps);
    } catch (SQLException e) {
      throw new ModelException("tasks-webapi user store: findByEmail failed", e);
    }
  }

  /**
   * Admin Console の user 一覧向けページング検索。空文字または {@code "*"} の場合は削除されていない全 user を返す。それ以外は {@code email}
   * または {@code full_name} の部分一致(大文字小文字区別なし)。
   */
  List<UserRow> search(@Nullable String term, int firstResult, int maxResults) {
    boolean matchAll = term == null || term.isBlank() || "*".equals(term);
    StringBuilder sql =
        new StringBuilder("SELECT ")
            .append(SELECT_COLUMNS)
            .append(" FROM users WHERE deleted_at IS NULL");
    if (!matchAll) {
      sql.append(" AND (email LIKE ? OR full_name LIKE ?)");
    }
    sql.append(" ORDER BY id LIMIT ? OFFSET ?");
    try (PreparedStatement ps = connection().prepareStatement(sql.toString())) {
      int idx = 1;
      if (!matchAll) {
        String like = "%" + term + "%";
        ps.setString(idx++, like);
        ps.setString(idx++, like);
      }
      ps.setInt(idx++, maxResults < 0 ? Integer.MAX_VALUE : maxResults);
      ps.setInt(idx, Math.max(firstResult, 0));
      List<UserRow> rows = new ArrayList<>();
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          rows.add(map(rs));
        }
      }
      return rows;
    } catch (SQLException e) {
      throw new ModelException("tasks-webapi user store: search failed", e);
    }
  }

  int count() {
    try (PreparedStatement ps =
            connection().prepareStatement("SELECT COUNT(*) FROM users WHERE deleted_at IS NULL");
        ResultSet rs = ps.executeQuery()) {
      return rs.next() ? rs.getInt(1) : 0;
    } catch (SQLException e) {
      throw new ModelException("tasks-webapi user store: count failed", e);
    }
  }

  // --- write ---

  /**
   * admin / リカバリの作成経路向けに tenant 未所属の行を insert する(ADR-0006 §3.1)。{@code full_name_kana} は空で開始し
   * Custom User Profile attribute の書き戻しで埋める。{@code oidc_sub} は初回ログインの correlation(§3.2)が本物の
   * Keycloak {@code sub} を割り当てるまで、一意な {@code pending:} placeholder を入れておく。
   *
   * @return insert した行(生成 id と version を取得するため再 read する)
   */
  UserRow insert(String email) {
    String sql =
        "INSERT INTO users (oidc_sub, email, full_name, full_name_kana, department_name, status,"
            + " version) VALUES (?, ?, ?, '', NULL, 'ACTIVE', 0)";
    try (PreparedStatement ps =
        connection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
      ps.setString(1, "pending:" + email);
      ps.setString(2, email);
      ps.setString(3, email);
      ps.executeUpdate();
      try (ResultSet keys = ps.getGeneratedKeys()) {
        if (!keys.next()) {
          throw new ModelException("tasks-webapi user store: insert returned no generated key");
        }
        return findById(keys.getLong(1))
            .orElseThrow(
                () -> new ModelException("tasks-webapi user store: inserted row not found"));
      }
    } catch (SQLException e) {
      throw new ModelException("tasks-webapi user store: insert failed", e);
    }
  }

  /** email の書き戻し(楽観排他、ADR-0006 §3.4)。更新後の version を返す。 */
  long updateEmail(long id, String email, long expectedVersion) {
    return updateColumn("email", email, id, expectedVersion);
  }

  /** {@code full_name_kana} の書き戻し(Custom User Profile 経路)。更新後の version を返す。 */
  long updateFullNameKana(long id, String value, long expectedVersion) {
    return updateColumn("full_name_kana", value, id, expectedVersion);
  }

  /** {@code department_name} の書き戻し(null 許容)。更新後の version を返す。 */
  long updateDepartmentName(long id, @Nullable String value, long expectedVersion) {
    return updateColumn("department_name", value, id, expectedVersion);
  }

  private long updateColumn(String column, @Nullable String value, long id, long expectedVersion) {
    String sql =
        "UPDATE users SET "
            + column
            + " = ?, version = version + 1 WHERE id = ? AND version = ? AND deleted_at IS NULL";
    try (PreparedStatement ps = connection().prepareStatement(sql)) {
      ps.setString(1, value);
      ps.setLong(2, id);
      ps.setLong(3, expectedVersion);
      if (ps.executeUpdate() != 1) {
        throw new ModelException(
            "tasks-webapi user store: optimistic lock conflict updating "
                + column
                + " for user "
                + id
                + " (expected version "
                + expectedVersion
                + ")");
      }
      return expectedVersion + 1;
    } catch (SQLException e) {
      throw new ModelException("tasks-webapi user store: update " + column + " failed", e);
    }
  }

  /**
   * 論理削除 + 個人情報匿名化(ADR-0006 §3.4 step 1〜7)。webapi の {@code UserAnonymizationDomainService} / {@code
   * User#anonymize} と同一ロジック。モジュール境界(keycloak SPI プラグインは webapi プロジェクト無しでビルドされる。{@code
   * keycloak/Dockerfile} 参照)のためその domain service を直接再利用できないので、同一の placeholder ロジックをここで 1 つの SQL
   * として適用する。{@code deleted_at} は ambient なタイムゾーンを避けるため DB クロック({@code NOW()})を使う。冪等: 匿名化済み行の再削除は
   * no-op。
   *
   * <p>TODO(#144): step 8 — {@code audit_logs} に {@code action='ANONYMIZE'} を記録する(監査機構の整備待ちで、{@code
   * UserAnonymizationDomainService} と同様にここでも保留)。
   */
  void anonymize(long id) {
    String sql =
        "UPDATE users SET deleted_at = NOW(),"
            + " email = CONCAT('__deleted__', id, '@deleted.invalid'),"
            + " oidc_sub = CONCAT('__deleted__', id),"
            + " full_name = '__deleted__',"
            + " full_name_kana = '__deleted__',"
            + " department_name = NULL,"
            + " version = version + 1"
            + " WHERE id = ? AND deleted_at IS NULL";
    try (PreparedStatement ps = connection().prepareStatement(sql)) {
      ps.setLong(1, id);
      ps.executeUpdate();
    } catch (SQLException e) {
      throw new ModelException("tasks-webapi user store: anonymize failed for user " + id, e);
    }
  }

  // --- ヘルパー ---

  private Connection connection() {
    Connection c = connection;
    if (c == null) {
      try {
        c = DriverManager.getConnection(jdbcUrl, username, password);
      } catch (SQLException e) {
        throw new ModelException("tasks-webapi user store: failed to open JDBC connection", e);
      }
      connection = c;
    }
    return c;
  }

  private static Optional<UserRow> single(PreparedStatement ps) throws SQLException {
    try (ResultSet rs = ps.executeQuery()) {
      return rs.next() ? Optional.of(map(rs)) : Optional.empty();
    }
  }

  private static UserRow map(ResultSet rs) throws SQLException {
    return new UserRow(
        rs.getLong("id"),
        rs.getString("oidc_sub"),
        rs.getString("email"),
        rs.getString("full_name"),
        rs.getString("full_name_kana"),
        rs.getString("department_name"),
        rs.getString("status"),
        rs.getLong("version"));
  }

  @Override
  public void close() {
    Connection c = connection;
    if (c != null) {
      try {
        c.close();
      } catch (SQLException e) {
        // close 失敗時に取れる対処はないためベストエフォートで握りつぶす。
      } finally {
        connection = null;
      }
    }
  }
}
