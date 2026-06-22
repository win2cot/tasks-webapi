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
 * JDBC data-access for the tasks-webapi {@code users} table (ADR-0006 §3.5).
 *
 * <p>The SPI federates {@code users} as a read-only profile directory; reads always filter {@code
 * deleted_at IS NULL} so anonymized rows (§3.4) are never resolved (authentication for a deleted
 * user is therefore refused). Per the {@code NO_CACHE} cache policy each operation hits the DB
 * directly. A single {@link Connection} is opened lazily and reused for the lifetime of the owning
 * provider instance (one Keycloak request), then closed in {@link #close()}.
 *
 * <p>Writes are limited to ADR-0006 §3.1's writable scope: {@code email} write-back (Update-Email
 * reach verification), {@code full_name_kana} / {@code department_name} via the Custom User Profile
 * attribute path (§3.1 Console-create), the {@code addUser} insert (admin / recovery), and the
 * {@code removeUser} anonymization (§3.4). All updates use optimistic locking on the {@code
 * version} column (JPA {@code @Version} equivalent); a lost update surfaces as a {@link
 * ModelException}.
 */
final class UserRepository implements AutoCloseable {

  /**
   * Immutable carrier for a non-deleted {@code users} row.
   *
   * @param departmentName nullable per the {@code users.department_name} schema
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

  // --- reads (deleted_at IS NULL only) ---

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
   * Paginated search for the Admin Console user list. A blank or {@code "*"} term returns all
   * non-deleted users; otherwise matches {@code email} or {@code full_name} (case-insensitive
   * substring).
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

  // --- writes ---

  /**
   * Inserts a tenant-unassigned row for the admin / recovery create path (ADR-0006 §3.1). {@code
   * full_name_kana} starts blank and is filled via the Custom User Profile attribute write-back;
   * {@code oidc_sub} gets a unique {@code pending:} placeholder until first-login correlation
   * (§3.2) assigns the real Keycloak {@code sub}.
   *
   * @return the inserted row (re-read to obtain the generated id and version)
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

  /** Email write-back with optimistic lock (ADR-0006 §3.4); returns the new version. */
  long updateEmail(long id, String email, long expectedVersion) {
    return updateColumn("email", email, id, expectedVersion);
  }

  /** {@code full_name_kana} write-back (Custom User Profile path); returns the new version. */
  long updateFullNameKana(long id, String value, long expectedVersion) {
    return updateColumn("full_name_kana", value, id, expectedVersion);
  }

  /** {@code department_name} write-back (nullable); returns the new version. */
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
   * Logical-delete + PII anonymization (ADR-0006 §3.4 steps 1-7), mirroring webapi's {@code
   * UserAnonymizationDomainService} / {@code User#anonymize}. The cross-module boundary (the
   * keycloak SPI plugin builds without the webapi project, see {@code keycloak/Dockerfile})
   * prevents reusing that domain service directly, so the identical placeholder logic is applied
   * here in one SQL statement. Uses the DB clock ({@code NOW()}) for {@code deleted_at} to avoid an
   * ambient time-zone. Idempotent: re-deleting an already-anonymized row is a no-op.
   *
   * <p>TODO(#144): step 8 — record an {@code audit_logs} {@code action='ANONYMIZE'} entry (deferred
   * here exactly as in {@code UserAnonymizationDomainService}, pending the auditing mechanism).
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

  // --- helpers ---

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
        // best-effort close; nothing actionable on a failed close.
      } finally {
        connection = null;
      }
    }
  }
}
