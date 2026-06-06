package xyz.dgz48.tasks.keycloak;

import java.util.Map;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.keycloak.component.ComponentModel;
import org.keycloak.models.GroupModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.provider.Provider;
import org.keycloak.storage.user.UserLookupProvider;
import org.keycloak.storage.user.UserQueryProvider;
import org.keycloak.storage.user.UserRegistrationProvider;

/**
 * Keycloak User Storage provider (ADR-0006).
 *
 * <p>Federates tasks-webapi {@code users} table as a read-only profile directory. Email is the only
 * writable attribute (via Keycloak Update-Email flow). Credentials and MFA are owned by Keycloak
 * ({@code CredentialInputValidator} is intentionally not implemented).
 */
public class TasksWebApiUserStorageProvider
    implements Provider, UserLookupProvider, UserQueryProvider, UserRegistrationProvider {

  @SuppressWarnings("unused")
  private final KeycloakSession session;

  @SuppressWarnings("unused")
  private final ComponentModel model;

  public TasksWebApiUserStorageProvider(KeycloakSession session, ComponentModel model) {
    this.session = session;
    this.model = model;
  }

  // --- Provider ---

  @Override
  public void close() {}

  // --- UserLookupProvider (read-only federation) ---

  @Override
  public @Nullable UserModel getUserById(RealmModel realm, String id) {
    // TODO: extract external ID via StorageId.externalId(id), look up users.id, return UserAdapter
    return null;
  }

  @Override
  public @Nullable UserModel getUserByUsername(RealmModel realm, String username) {
    // email is the login-ID in Keycloak (username == email per ADR-0006 §3.1)
    // TODO: SELECT * FROM users WHERE email = :username AND deleted_at IS NULL
    return null;
  }

  @Override
  public @Nullable UserModel getUserByEmail(RealmModel realm, String email) {
    // TODO: SELECT * FROM users WHERE email = :email AND deleted_at IS NULL
    return null;
  }

  // --- UserQueryProvider (read-only) ---

  @Override
  public Stream<UserModel> searchForUserStream(
      RealmModel realm,
      Map<String, String> params,
      @Nullable Integer firstResult,
      @Nullable Integer maxResults) {
    // TODO: query users table with pagination; used by Admin Console user list
    return Stream.empty();
  }

  @Override
  public Stream<UserModel> getGroupMembersStream(
      RealmModel realm,
      GroupModel group,
      @Nullable Integer firstResult,
      @Nullable Integer maxResults) {
    return Stream.empty();
  }

  @Override
  public Stream<UserModel> searchForUserByUserAttributeStream(
      RealmModel realm, String attrName, String attrValue) {
    return Stream.empty();
  }

  // --- UserRegistrationProvider (admin / disaster-recovery path only; main path is tasks API) ---

  @Override
  public @Nullable UserModel addUser(RealmModel realm, String username) {
    // Admin Console / recovery path: insert into users (email, full_name, full_name_kana).
    // full_name_kana and other required fields are provided via Custom User Profile attributes.
    // Tenant assignment is NOT performed here; created user is tenant-unassigned.
    // TODO: implement insert + return UserAdapter
    return null;
  }

  @Override
  public boolean removeUser(RealmModel realm, UserModel user) {
    // Anonymize per ADR-0006 §3.4: set deleted_at + placeholder email/oidc_sub/full_name.
    // TODO: call UserAnonymizationDomainService in 1 transaction; return true on success
    return false;
  }
}
