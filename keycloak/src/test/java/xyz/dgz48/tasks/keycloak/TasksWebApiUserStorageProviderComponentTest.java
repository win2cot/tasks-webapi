package xyz.dgz48.tasks.keycloak;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.component.ComponentModel;
import org.keycloak.models.GroupModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.ModelException;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RoleModel;
import org.keycloak.models.UserModel;
import org.keycloak.storage.StorageId;

/**
 * {@link TasksWebApiUserStorageProvider} を実 MySQL + mock した Keycloak SPI 型でテスト JVM 内駆動する component
 * テスト。 Lookup / Query / Registration の各経路(ADR-0006 §3.1)を網羅し JaCoCo 計測対象とする。
 */
class TasksWebApiUserStorageProviderComponentTest extends AbstractMySqlContainerTest {

  private KeycloakSession session;
  private RealmModel realm;
  private ComponentModel model;

  @BeforeEach
  void mocks() {
    session = mock(KeycloakSession.class);
    realm = mock(RealmModel.class);
    model = mock(ComponentModel.class);
    when(model.getId()).thenReturn("comp-1");
    when(realm.getDefaultRole()).thenReturn(mock(RoleModel.class));
  }

  private TasksWebApiUserStorageProvider provider(UserRepository repo) {
    return new TasksWebApiUserStorageProvider(session, model, repo);
  }

  private String storageId(long id) {
    return StorageId.keycloakId(model, String.valueOf(id));
  }

  // --- UserLookupProvider ---

  @Test
  void getUserById_resolvesFederatedUser() throws Exception {
    String email = unique("byid") + "@example.com";
    long id = seedUser(unique("sub"), email, "氏名", "カナ", null, "ACTIVE");
    try (UserRepository repo = newRepository()) {
      UserModel u = provider(repo).getUserById(realm, storageId(id));
      assertThat(u).isNotNull();
      assertThat(u.getEmail()).isEqualTo(email);
    }
  }

  @Test
  void getUserById_returnsNull_forNonNumericOrMissing() throws Exception {
    try (UserRepository repo = newRepository()) {
      TasksWebApiUserStorageProvider p = provider(repo);
      assertThat(p.getUserById(realm, "f:comp-1:not-a-number")).isNull();
      assertThat(p.getUserById(realm, storageId(987_654_321L))).isNull();
    }
  }

  @Test
  void getUserByUsernameAndEmail_resolveByEmail() throws Exception {
    String email = unique("byname") + "@example.com";
    seedUser(unique("sub"), email, "氏名", "カナ", null, "ACTIVE");
    try (UserRepository repo = newRepository()) {
      TasksWebApiUserStorageProvider p = provider(repo);
      assertThat(p.getUserByUsername(realm, email)).isNotNull(); // username == email
      assertThat(p.getUserByEmail(realm, email)).isNotNull();
      assertThat(p.getUserByEmail(realm, unique("missing") + "@example.com")).isNull();
    }
  }

  // --- UserQueryProvider ---

  @Test
  void searchForUserStream_usesSearchUsernameOrEmailParam() throws Exception {
    String tag = unique("q");
    seedUser(unique("sub"), tag + "@example.com", "検索 太郎", "カナ", null, "ACTIVE");
    try (UserRepository repo = newRepository()) {
      TasksWebApiUserStorageProvider p = provider(repo);
      assertThat(p.searchForUserStream(realm, Map.of(UserModel.SEARCH, tag), 0, 10)).hasSize(1);
      assertThat(p.searchForUserStream(realm, Map.of(UserModel.USERNAME, tag), 0, 10)).hasSize(1);
      assertThat(p.searchForUserStream(realm, Map.of(UserModel.EMAIL, tag), 0, 10)).hasSize(1);
      // パラメータ無し → 全件(matchAll)。null first/max も許容。
      assertThat(p.searchForUserStream(realm, Map.of(), null, null)).isNotEmpty();
    }
  }

  @Test
  void getUsersCount_reflectsActiveRows() throws Exception {
    seedUser(unique("sub"), unique("cnt") + "@example.com", "氏名", "カナ", null, "ACTIVE");
    try (UserRepository repo = newRepository()) {
      assertThat(provider(repo).getUsersCount(realm)).isGreaterThanOrEqualTo(1);
    }
  }

  @Test
  void groupMembersAndAttributeSearch_areNotFederated() throws Exception {
    try (UserRepository repo = newRepository()) {
      TasksWebApiUserStorageProvider p = provider(repo);
      assertThat(p.getGroupMembersStream(realm, mock(GroupModel.class), 0, 10)).isEmpty();
      assertThat(p.searchForUserByUserAttributeStream(realm, "x", "y")).isEmpty();
    }
  }

  // --- UserRegistrationProvider ---

  @Test
  void addUser_insertsTenantlessRow() throws Exception {
    String email = unique("add") + "@example.com";
    try (UserRepository repo = newRepository()) {
      UserModel u = provider(repo).addUser(realm, email);
      assertThat(u.getEmail()).isEqualTo(email);
    }
    UserRow row = fetchByEmail(email);
    assertThat(row).isNotNull();
    assertThat(row.oidcSub()).isEqualTo("pending:" + email);
  }

  @Test
  void removeUser_anonymizesAndReturnsTrue() throws Exception {
    String email = unique("rm") + "@example.com";
    long id = seedUser(unique("sub"), email, "氏名", "カナ", "部署", "ACTIVE");
    String sid = storageId(id); // mock 呼び出しを when() の外で評価する(UnfinishedStubbing 回避)
    UserModel user = mock(UserModel.class);
    when(user.getId()).thenReturn(sid);
    try (UserRepository repo = newRepository()) {
      assertThat(provider(repo).removeUser(realm, user)).isTrue();
    }
    UserRow row = fetchById(id);
    assertThat(row.deleted()).isTrue();
    assertThat(row.email()).isEqualTo("__deleted__" + id + "@deleted.invalid");
  }

  @Test
  void removeUser_throwsModelException_forNonNumericId() throws Exception {
    UserModel user = mock(UserModel.class);
    when(user.getId()).thenReturn("f:comp-1:not-a-number");
    try (UserRepository repo = newRepository()) {
      TasksWebApiUserStorageProvider p = provider(repo);
      assertThatThrownBy(() -> p.removeUser(realm, user)).isInstanceOf(ModelException.class);
    }
  }

  @Test
  void close_closesRepository() {
    UserRepository repo = newRepository();
    provider(repo).close(); // repository.close() に委譲(例外を投げない)
  }
}
