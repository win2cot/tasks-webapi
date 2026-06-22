package xyz.dgz48.tasks.keycloak;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.keycloak.component.ComponentModel;
import org.keycloak.models.GroupModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.ModelException;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.provider.Provider;
import org.keycloak.storage.StorageId;
import org.keycloak.storage.user.UserLookupProvider;
import org.keycloak.storage.user.UserQueryProvider;
import org.keycloak.storage.user.UserRegistrationProvider;

/**
 * Keycloak User Storage provider (ADR-0006).
 *
 * <p>Federates the tasks-webapi {@code users} table as a read-only profile directory. Email is the
 * only writable profile attribute (via Keycloak Update-Email); {@code full_name_kana} / {@code
 * department_name} are writable solely through the Custom User Profile attribute path on the admin
 * / recovery create flow (§3.1). Credentials and MFA are owned by Keycloak's native store, so
 * {@code CredentialInputValidator} is intentionally not implemented (§3.3).
 *
 * <p>JDBC access goes through a per-provider {@link UserRepository}; the connection is configured
 * by the federation component (see {@link TasksWebApiUserStorageProviderFactory}) and closed in
 * {@link #close()}.
 */
public class TasksWebApiUserStorageProvider
    implements Provider, UserLookupProvider, UserQueryProvider, UserRegistrationProvider {

  private final KeycloakSession session;
  private final ComponentModel model;
  private final UserRepository repository;

  public TasksWebApiUserStorageProvider(
      KeycloakSession session, ComponentModel model, UserRepository repository) {
    this.session = session;
    this.model = model;
    this.repository = repository;
  }

  // --- Provider ---

  @Override
  public void close() {
    repository.close();
  }

  // --- UserLookupProvider (read-only federation) ---

  @Override
  public @Nullable UserModel getUserById(RealmModel realm, String id) {
    long userId;
    try {
      userId = Long.parseLong(StorageId.externalId(id));
    } catch (NumberFormatException e) {
      return null;
    }
    return repository.findById(userId).map(row -> adapt(realm, row)).orElse(null);
  }

  @Override
  public @Nullable UserModel getUserByUsername(RealmModel realm, String username) {
    // email is the login-ID in Keycloak (username == email per ADR-0006 §3.1)
    return repository.findByEmail(username).map(row -> adapt(realm, row)).orElse(null);
  }

  @Override
  public @Nullable UserModel getUserByEmail(RealmModel realm, String email) {
    return repository.findByEmail(email).map(row -> adapt(realm, row)).orElse(null);
  }

  // --- UserQueryProvider (read-only) ---

  @Override
  public Stream<UserModel> searchForUserStream(
      RealmModel realm,
      Map<String, String> params,
      @Nullable Integer firstResult,
      @Nullable Integer maxResults) {
    String term = params.get(UserModel.SEARCH);
    if (term == null) {
      term = params.get(UserModel.USERNAME);
    }
    if (term == null) {
      term = params.get(UserModel.EMAIL);
    }
    List<UserRepository.UserRow> rows =
        repository.search(
            term, firstResult == null ? 0 : firstResult, maxResults == null ? -1 : maxResults);
    return rows.stream().map(row -> adapt(realm, row));
  }

  @Override
  public int getUsersCount(RealmModel realm) {
    return repository.count();
  }

  @Override
  public Stream<UserModel> getGroupMembersStream(
      RealmModel realm,
      GroupModel group,
      @Nullable Integer firstResult,
      @Nullable Integer maxResults) {
    // Group membership is not federated; the SPI returns only users attributes (ADR-0006 §3.7).
    return Stream.empty();
  }

  @Override
  public Stream<UserModel> searchForUserByUserAttributeStream(
      RealmModel realm, String attrName, String attrValue) {
    // Custom attribute search is not federated; users is keyed by id / email only.
    return Stream.empty();
  }

  // --- UserRegistrationProvider (admin / disaster-recovery path only; main path is tasks API) ---

  @Override
  public UserModel addUser(RealmModel realm, String username) {
    // Admin Console / recovery path (ADR-0006 §3.1): insert a tenant-unassigned users row.
    // username is the email/login-ID; full_name_kana and other Custom User Profile attributes are
    // written back afterwards via UserAdapter#setSingleAttribute. Tenant assignment is NOT done
    // here.
    return adapt(realm, repository.insert(username));
  }

  @Override
  public boolean removeUser(RealmModel realm, UserModel user) {
    // Convert delete to logical-delete + anonymization (ADR-0006 §3.4) and return true so Keycloak
    // performs its standard cleanup (credentials / sessions). No-op if already anonymized.
    long userId;
    try {
      userId = Long.parseLong(StorageId.externalId(user.getId()));
    } catch (NumberFormatException e) {
      throw new ModelException(
          "tasks-webapi user store: cannot remove user with id " + user.getId());
    }
    repository.anonymize(userId);
    return true;
  }

  private UserAdapter adapt(RealmModel realm, UserRepository.UserRow row) {
    return new UserAdapter(session, realm, model, repository, row);
  }
}
