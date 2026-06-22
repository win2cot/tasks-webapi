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
 * Keycloak User Storage provider(ADR-0006)。
 *
 * <p>tasks-webapi の {@code users} テーブルを read-only の profile ディレクトリとして federate する。writable な
 * profile 属性は email のみ(Keycloak Update-Email 経由)。{@code full_name_kana} / {@code department_name} は
 * admin / リカバリの作成フローにおける Custom User Profile attribute 経路でのみ writable(§3.1)。資格情報・MFA は Keycloak の
 * native store が所有するため、{@code CredentialInputValidator} は意図的に実装しない(§3.3)。
 *
 * <p>JDBC アクセスは provider ごとの {@link UserRepository} を経由する。接続は federation コンポーネント({@link
 * TasksWebApiUserStorageProviderFactory} 参照)で設定し、{@link #close()} で破棄する。
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

  // --- UserLookupProvider(read-only federation) ---

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
    // Keycloak では email が login-ID(ADR-0006 §3.1 より username == email)
    return repository.findByEmail(username).map(row -> adapt(realm, row)).orElse(null);
  }

  @Override
  public @Nullable UserModel getUserByEmail(RealmModel realm, String email) {
    return repository.findByEmail(email).map(row -> adapt(realm, row)).orElse(null);
  }

  // --- UserQueryProvider(read-only) ---

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
    // group membership は federate しない。SPI は users 属性のみを返す(ADR-0006 §3.7)。
    return Stream.empty();
  }

  @Override
  public Stream<UserModel> searchForUserByUserAttributeStream(
      RealmModel realm, String attrName, String attrValue) {
    // カスタム属性検索は federate しない。users は id / email のみで引く。
    return Stream.empty();
  }

  // --- UserRegistrationProvider(admin / 障害時リカバリ経路のみ。主経路は tasks API) ---

  @Override
  public UserModel addUser(RealmModel realm, String username) {
    // Admin Console / リカバリ経路(ADR-0006 §3.1): tenant 未所属の users 行を insert する。
    // username は email/login-ID。full_name_kana 等の Custom User Profile attribute は後続の
    // UserAdapter#setSingleAttribute で書き戻す。tenant 割当てはここでは行わない。
    return adapt(realm, repository.insert(username));
  }

  @Override
  public boolean removeUser(RealmModel realm, UserModel user) {
    // delete を論理削除 + 匿名化(ADR-0006 §3.4)に変換し、Keycloak 標準の cleanup(資格情報 / セッション)に
    // 委ねるため true を返す。匿名化済みなら no-op。
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
