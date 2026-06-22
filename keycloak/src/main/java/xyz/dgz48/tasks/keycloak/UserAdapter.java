package xyz.dgz48.tasks.keycloak;

import org.keycloak.component.ComponentModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.SubjectCredentialManager;
import org.keycloak.storage.StorageId;
import org.keycloak.storage.adapter.AbstractInMemoryUserAdapter;

/**
 * Adapts a {@code users} table row as a Keycloak {@link org.keycloak.models.UserModel}.
 *
 * <p>Profile attributes (full_name, department_name, etc.) are read-only — tasks-webapi is the SoT.
 * Email is the only writable profile attribute; it is written back to {@code users.email} by the
 * Keycloak Update-Email flow (ADR-0006 §3.1). {@code full_name_kana} and {@code department_name}
 * are additionally writable through {@link #setSingleAttribute} for the admin / recovery
 * Console-create Custom User Profile path (§3.1); first/last name remain read-only no-ops. All
 * write-backs use the {@code version} column for optimistic locking (§3.4).
 */
public final class UserAdapter extends AbstractInMemoryUserAdapter {

  /** Custom User Profile attribute keys backed by {@code users} columns (ADR-0006 §3.1). */
  static final String FULL_NAME = "full_name";

  static final String FULL_NAME_KANA = "full_name_kana";
  static final String DEPARTMENT_NAME = "department_name";

  /** {@code users.status} value for an enabled account (ADR-0006 §3.3 有効/無効). */
  private static final String STATUS_ACTIVE = "ACTIVE";

  private final UserRepository repository;
  private final long userId;

  /** Tracks the persisted {@code users.version}; advanced after each successful write-back. */
  private long version;

  /** True only while the constructor hydrates in-memory state, suppressing write-back. */
  private boolean hydrating;

  public UserAdapter(
      KeycloakSession session,
      RealmModel realm,
      ComponentModel storageProviderModel,
      UserRepository repository,
      UserRepository.UserRow row) {
    super(session, realm, StorageId.keycloakId(storageProviderModel, String.valueOf(row.id())));
    this.repository = repository;
    this.userId = row.id();
    this.version = row.version();
    this.hydrating = true;
    setUsername(row.email());
    // Map users.status onto Keycloak enablement so an INACTIVE account cannot authenticate.
    setEnabled(STATUS_ACTIVE.equals(row.status()));
    setEmailVerified(true);
    super.setEmail(row.email());
    setSingleAttribute(FIRST_NAME, row.fullName());
    setSingleAttribute(FULL_NAME, row.fullName());
    setSingleAttribute(FULL_NAME_KANA, row.fullNameKana());
    if (row.departmentName() != null) {
      setSingleAttribute(DEPARTMENT_NAME, row.departmentName());
    }
    this.hydrating = false;
  }

  /**
   * Credentials are owned by Keycloak's native (federated) credential store (ADR-0006 §3.3).
   *
   * <p>Delegates to Keycloak's own credential manager for this federated user so password / MFA are
   * stored and validated by Keycloak rather than the SPI ({@code CredentialInputValidator} is not
   * implemented).
   */
  @Override
  public SubjectCredentialManager credentialManager() {
    return session.users().getUserCredentialManager(this);
  }

  /** Email is the only writable profile attribute; writes back to {@code users.email} (§3.1). */
  @Override
  public void setEmail(String email) {
    if (!hydrating) {
      this.version = repository.updateEmail(userId, email, version);
    }
    super.setEmail(email);
  }

  /**
   * Routes the Custom User Profile attributes {@code full_name_kana} / {@code department_name} to
   * their {@code users} columns (ADR-0006 §3.1 Console-create); all other attributes stay
   * in-memory.
   */
  @Override
  public void setSingleAttribute(String name, String value) {
    if (!hydrating) {
      if (FULL_NAME_KANA.equals(name)) {
        this.version = repository.updateFullNameKana(userId, value, version);
      } else if (DEPARTMENT_NAME.equals(name)) {
        this.version = repository.updateDepartmentName(userId, value, version);
      }
    }
    super.setSingleAttribute(name, value);
  }

  /** Ignored — full_name is read-only; SoT is the tasks-webapi profile update API (§3.1). */
  @Override
  public void setFirstName(String firstName) {}

  /** Ignored — not stored in the users table. */
  @Override
  public void setLastName(String lastName) {}
}
