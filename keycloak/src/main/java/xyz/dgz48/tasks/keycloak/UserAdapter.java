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
 * Email is the only writable attribute; updates are written back to {@code users.email} by the
 * Keycloak Update-Email flow (ADR-0006 §3.1).
 */
public final class UserAdapter extends AbstractInMemoryUserAdapter {

  public UserAdapter(
      KeycloakSession session,
      RealmModel realm,
      ComponentModel storageProviderModel,
      long userId,
      String email,
      String fullName) {
    super(session, realm, StorageId.keycloakId(storageProviderModel, String.valueOf(userId)));
    setUsername(email);
    setSingleAttribute(FIRST_NAME, fullName);
    setSingleAttribute(EMAIL, email);
  }

  /**
   * Credentials are owned by Keycloak's native credential store (ADR-0006 §3.3).
   *
   * <p>Returns the session-level credential manager for this user. TODO: wire to
   * session.userCredentialManager() when JDBC integration is added.
   */
  @Override
  public SubjectCredentialManager credentialManager() {
    throw new UnsupportedOperationException(
        "Credential management is delegated to Keycloak native store (ADR-0006 §3.3)");
  }

  /** Email is the only writable attribute; writes back to {@code users.email} via JDBC. */
  @Override
  public void setEmail(String email) {
    // TODO: UPDATE users SET email = :email, version = version + 1 WHERE id = :userId (ADR-0006
    // §3.4)
    super.setEmail(email);
  }

  /** Ignored — full_name is read-only; SoT is tasks-webapi profile update API. */
  @Override
  public void setFirstName(String firstName) {}

  /** Ignored — not stored in users table. */
  @Override
  public void setLastName(String lastName) {}
}
