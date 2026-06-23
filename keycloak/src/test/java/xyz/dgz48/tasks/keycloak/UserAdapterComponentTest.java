package xyz.dgz48.tasks.keycloak;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.keycloak.component.ComponentModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RoleModel;
import org.keycloak.models.UserCredentialManager;
import org.keycloak.models.UserModel;
import org.keycloak.models.UserProvider;

/**
 * {@link UserAdapter} を実 MySQL + mock した Keycloak SPI 型でテスト JVM 内駆動する component テスト(ADR-0006 §3.1)。
 * hydration(read federate)/ email・full_name_kana・department_name の write 戻し / 氏名 read-only / 変更なし時の
 * 非書き込みを網羅し JaCoCo 計測対象とする。
 */
class UserAdapterComponentTest extends AbstractMySqlContainerTest {

  private UserAdapter adapterFor(long id, UserRepository repo) {
    UserRepository.UserRow row = repo.findById(id).orElseThrow();
    KeycloakSession session = mock(KeycloakSession.class);
    RealmModel realm = mock(RealmModel.class);
    ComponentModel model = mock(ComponentModel.class);
    when(model.getId()).thenReturn("comp-1");
    when(realm.getDefaultRole()).thenReturn(mock(RoleModel.class));
    return new UserAdapter(session, realm, model, repo, row);
  }

  @Test
  void hydration_exposesProfileFromRow() throws Exception {
    String email = unique("hyd") + "@example.com";
    long id = seedUser(unique("sub"), email, "氏名 太郎", "シメイ タロウ", "営業部", "ACTIVE");
    try (UserRepository repo = newRepository()) {
      UserAdapter a = adapterFor(id, repo);
      assertThat(a.getUsername()).isEqualTo(email);
      assertThat(a.getEmail()).isEqualTo(email);
      assertThat(a.isEnabled()).isTrue();
      assertThat(a.isEmailVerified()).isTrue();
      assertThat(a.getFirstAttribute(UserAdapter.FULL_NAME)).isEqualTo("氏名 太郎");
      assertThat(a.getFirstAttribute(UserAdapter.FULL_NAME_KANA)).isEqualTo("シメイ タロウ");
      assertThat(a.getFirstAttribute(UserAdapter.DEPARTMENT_NAME)).isEqualTo("営業部");
    }
  }

  @Test
  void hydration_inactiveUser_isDisabled_andNullDepartment() throws Exception {
    String email = unique("inact") + "@example.com";
    long id = seedUser(unique("sub"), email, "無効", "ムコウ", null, "INACTIVE");
    try (UserRepository repo = newRepository()) {
      UserAdapter a = adapterFor(id, repo);
      assertThat(a.isEnabled()).isFalse();
      assertThat(a.getFirstAttribute(UserAdapter.DEPARTMENT_NAME)).isNull();
    }
  }

  @Test
  void setEmail_writesBackToUsersTable() throws Exception {
    String email = unique("se") + "@example.com";
    long id = seedUser(unique("sub"), email, "氏名", "カナ", null, "ACTIVE");
    String newEmail = unique("se-new") + "@example.com";
    try (UserRepository repo = newRepository()) {
      adapterFor(id, repo).setEmail(newEmail);
    }
    UserRow row = fetchById(id);
    assertThat(row.email()).isEqualTo(newEmail);
    assertThat(row.version()).isEqualTo(1L);
  }

  @Test
  void setAttribute_routesManagedColumns_andSkipsUnchanged() throws Exception {
    String email = unique("sa") + "@example.com";
    long id = seedUser(unique("sub"), email, "氏名", "旧カナ", "旧部署", "ACTIVE");
    String newEmail = unique("sa-new") + "@example.com";
    try (UserRepository repo = newRepository()) {
      UserAdapter a = adapterFor(id, repo);
      // Keycloak の基底は email/username を lowercase 化するため values.set(0,..) を呼ぶ。可変リストを渡す。
      a.setAttribute(UserModel.EMAIL, new java.util.ArrayList<>(List.of(newEmail)));
      a.setAttribute(UserAdapter.FULL_NAME_KANA, List.of("新カナ"));
      a.setAttribute(UserAdapter.DEPARTMENT_NAME, List.of("新部署"));
      // 変更なしの再適用は write しない(version を無駄に進めない)。
      a.setAttribute(UserAdapter.FULL_NAME_KANA, List.of("新カナ"));
      // 管理対象外の属性は in-memory のみ。
      a.setAttribute("firstName", List.of("無視される"));
    }
    UserRow row = fetchById(id);
    assertThat(row.email()).isEqualTo(newEmail);
    assertThat(row.fullNameKana()).isEqualTo("新カナ");
    assertThat(row.departmentName()).isEqualTo("新部署");
    assertThat(row.fullName()).isEqualTo("氏名"); // read-only
    assertThat(row.version()).isEqualTo(3L); // email + kana + department = 3 回(重複 kana は skip)
  }

  @Test
  void setSingleAttribute_routesFullNameKana() throws Exception {
    String email = unique("ssa") + "@example.com";
    long id = seedUser(unique("sub"), email, "氏名", "カナ", null, "ACTIVE");
    try (UserRepository repo = newRepository()) {
      adapterFor(id, repo).setSingleAttribute(UserAdapter.FULL_NAME_KANA, "別カナ");
    }
    assertThat(fetchById(id).fullNameKana()).isEqualTo("別カナ");
  }

  @Test
  void setAttribute_emptyValues_doesNotWriteEmail() throws Exception {
    String email = unique("empty") + "@example.com";
    long id = seedUser(unique("sub"), email, "氏名", "カナ", null, "ACTIVE");
    try (UserRepository repo = newRepository()) {
      UserAdapter a = adapterFor(id, repo);
      a.setAttribute(UserModel.EMAIL, List.of()); // null 値扱い → email(NOT NULL)は write しない
    }
    UserRow row = fetchById(id);
    assertThat(row.email()).isEqualTo(email); // 不変
    assertThat(row.version()).isZero();
  }

  @Test
  void nameSetters_areReadOnlyNoops() throws Exception {
    String email = unique("ro") + "@example.com";
    long id = seedUser(unique("sub"), email, "氏名 元", "カナ", null, "ACTIVE");
    try (UserRepository repo = newRepository()) {
      UserAdapter a = adapterFor(id, repo);
      a.setFirstName("変更");
      a.setLastName("変更");
    }
    UserRow row = fetchById(id);
    assertThat(row.fullName()).isEqualTo("氏名 元");
    assertThat(row.version()).isZero();
  }

  @Test
  void credentialManager_delegatesToKeycloakNativeStore() throws Exception {
    String email = unique("cred") + "@example.com";
    long id = seedUser(unique("sub"), email, "氏名", "カナ", null, "ACTIVE");
    try (UserRepository repo = newRepository()) {
      UserRepository.UserRow row = repo.findById(id).orElseThrow();
      KeycloakSession session = mock(KeycloakSession.class);
      RealmModel realm = mock(RealmModel.class);
      ComponentModel model = mock(ComponentModel.class);
      when(model.getId()).thenReturn("comp-1");
      when(realm.getDefaultRole()).thenReturn(mock(RoleModel.class));
      UserProvider users = mock(UserProvider.class);
      UserCredentialManager cm = mock(UserCredentialManager.class);
      when(session.users()).thenReturn(users);
      UserAdapter a = new UserAdapter(session, realm, model, repo, row);
      when(users.getUserCredentialManager(a)).thenReturn(cm);
      // 資格は Keycloak native store が所有(SPI は CredentialInputValidator 非実装、§3.3)。
      assertThat(a.credentialManager()).isSameAs(cm);
    }
  }
}
