package xyz.dgz48.tasks.keycloak;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.models.ModelException;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.UserRepresentation;

/**
 * Keycloak User Storage SPI を実 Keycloak + 実 MySQL で Admin REST API 経由検証する(ADR-0006 §6)。
 *
 * <p>read(profile lookup / search / count)、email のみ write 戻し、氏名 read-only、admin 作成(tenant
 * 未所属)、admin 削除→匿名化、削除後の認証拒否 + session 失効、楽観排他(UserRepository 直)を網羅する。
 */
class UserStorageSpiIT extends AbstractSpiContainerTest {

  private static long uniq = 0;

  private static synchronized String uniqueEmail(String label) {
    return label + "+" + ++uniq + "@example.com";
  }

  // --- read(UserLookupProvider / UserQueryProvider) ---

  @Test
  void lookup_byEmail_returnsProfileFromUsersTable() throws Exception {
    String email = uniqueEmail("read");
    seedUser("sub-" + email, email, "山田 太郎", "ヤマダ タロウ", "営業部", "ACTIVE");

    try (Keycloak admin = adminClient()) {
      UserRepresentation user = singleByEmail(admin, email);

      // username == email(ADR-0006 §3.1)。profile は users テーブルが SoT。
      assertThat(user.getUsername()).isEqualTo(email);
      assertThat(user.getEmail()).isEqualTo(email);
      assertThat(user.isEnabled()).isTrue(); // status=ACTIVE → 有効
      assertThat(user.getFirstName()).isEqualTo("山田 太郎"); // full_name を firstName へ federate
      assertThat(attr(user, "full_name_kana")).isEqualTo("ヤマダ タロウ");
      // department_name は realm の declarative user profile に宣言が無いため admin 表現には載らない
      // (Custom User Profile の未宣言 attribute は Keycloak がフィルタする)。SPI の read 自体は行われる。
    }
  }

  @Test
  void lookup_inactiveUser_isDisabled() throws Exception {
    String email = uniqueEmail("inactive");
    seedUser("sub-" + email, email, "無効 ユーザー", "ムコウ ユーザー", null, "INACTIVE");

    try (Keycloak admin = adminClient()) {
      UserRepresentation user = singleByEmail(admin, email);
      assertThat(user.isEnabled()).isFalse(); // status=INACTIVE → 認証不可
    }
  }

  @Test
  void query_searchAndCount_reflectUsersTable() throws Exception {
    String tag = "q" + System.nanoTime();
    seedUser("sub1-" + tag, tag + "-a@example.com", "検索 一郎", "ケンサク イチロウ", null, "ACTIVE");
    seedUser("sub2-" + tag, tag + "-b@example.com", "検索 二郎", "ケンサク ジロウ", null, "ACTIVE");

    try (Keycloak admin = adminClient()) {
      UsersResource users = admin.realm(REALM).users();
      List<UserRepresentation> hits = users.search(tag, 0, 50);
      assertThat(hits).hasSize(2);
      assertThat(users.count()).isGreaterThanOrEqualTo(2);
    }
  }

  // --- write: email のみ write 戻し / 氏名 read-only ---

  @Test
  void update_email_writesBackToUsersTable() throws Exception {
    String email = uniqueEmail("emailwb");
    long id = seedUser("sub-" + email, email, "変更 前", "ヘンコウ マエ", null, "ACTIVE");
    String newEmail = uniqueEmail("emailwb-new");

    try (Keycloak admin = adminClient()) {
      UserRepresentation user = singleByEmail(admin, email);
      user.setEmail(newEmail);
      admin.realm(REALM).users().get(user.getId()).update(user);
    }

    UserRow row = fetchById(id);
    assertThat(row.email()).isEqualTo(newEmail); // users.email へ反映
    assertThat(row.version()).isEqualTo(1L); // @Version 相当が increment
  }

  @Test
  void update_firstName_isReadOnly() throws Exception {
    String email = uniqueEmail("nameRo");
    long id = seedUser("sub-" + email, email, "読取 専用", "ヨミトリ センヨウ", null, "ACTIVE");

    try (Keycloak admin = adminClient()) {
      UserRepresentation user = singleByEmail(admin, email);
      user.setFirstName("書込 不可");
      admin.realm(REALM).users().get(user.getId()).update(user);
    }

    UserRow row = fetchById(id);
    assertThat(row.fullName()).isEqualTo("読取 専用"); // full_name は不変(SoT は webapi)
    assertThat(row.version()).isEqualTo(0L); // 書き戻し無し
  }

  // --- create(UserRegistrationProvider, admin / リカバリ経路) ---

  @Test
  void create_viaAdmin_insertsTenantlessRow() throws Exception {
    String email = uniqueEmail("create");

    UserRepresentation rep = new UserRepresentation();
    rep.setUsername(email);
    rep.setEmail(email);
    rep.setEnabled(true);
    // Custom User Profile の必須 attribute(admin ロールでは full_name_kana 必須)。
    rep.setAttributes(Map.of("full_name_kana", List.of("シンキ サクセイ")));

    try (Keycloak admin = adminClient()) {
      try (Response resp = admin.realm(REALM).users().create(rep)) {
        assertThat(resp.getStatus()).isEqualTo(201);
        CreatedResponseUtil.getCreatedId(resp); // Location が SPI の storage id を返すこと
      }
    }

    UserRow row = fetchByEmail(email);
    assertThat(row).isNotNull();
    // tenant 未所属(SPI は user_tenants へ書かない)。oidc_sub は初回ログイン correlation 待ちの placeholder。
    assertThat(row.oidcSub()).isEqualTo("pending:" + email);
    assertThat(row.fullNameKana()).isEqualTo("シンキ サクセイ"); // setSingleAttribute 経由で write 戻し
    assertThat(row.deleted()).isFalse();
  }

  // --- delete(論理削除 + 匿名化, ADR-0006 §3.4) ---

  @Test
  void delete_viaAdmin_anonymizesAndRejectsSubsequentAuth() throws Exception {
    String email = uniqueEmail("del");
    String password = "P@ssw0rd-del";
    long id = seedUser("sub-" + email, email, "削除 対象", "サクジョ タイショウ", "総務部", "ACTIVE");

    try (Keycloak admin = adminClient()) {
      UserRepresentation user = singleByEmail(admin, email);
      setPassword(admin, user.getId(), password);

      // 削除前は認証成功する。
      try (Keycloak uc = userClient(email, password)) {
        assertThat(uc.tokenManager().getAccessToken().getToken()).isNotBlank();
      }

      // 削除 → SPI removeUser → 匿名化 + Keycloak 標準 cleanup。
      admin.realm(REALM).users().get(user.getId()).remove();

      // 削除後は同一資格での認証が拒否される(deleted_at IS NULL で lookup されない)。
      assertThatThrownBy(
              () -> {
                try (Keycloak uc2 = userClient(email, password)) {
                  uc2.tokenManager().getAccessToken();
                }
              })
          .isInstanceOf(Exception.class);
    }

    UserRow row = fetchById(id);
    assertThat(row.deleted()).isTrue(); // deleted_at 設定
    assertThat(row.email()).isEqualTo("__deleted__" + id + "@deleted.invalid");
    assertThat(row.oidcSub()).isEqualTo("__deleted__" + id);
    assertThat(row.fullName()).isEqualTo("__deleted__");
    assertThat(row.fullNameKana()).isEqualTo("__deleted__");
    assertThat(row.departmentName()).isNull();
    assertThat(row.version()).isEqualTo(1L);

    // step 8(ADR-0006 §3.4 / #734): 実 Keycloak removeUser 経由で匿名化と同一トランザクションに
    // audit_logs へ ANONYMIZE が記録される(entity_type='users' / entity_id=users.id、hash_chain 整合)。
    AuditRow audit = fetchAnonymizeAudit(id);
    assertThat(audit).isNotNull();
    assertThat(audit.action()).isEqualTo("ANONYMIZE");
    assertThat(audit.entityType()).isEqualTo("users");
    assertThat(audit.entityId()).isEqualTo(id);
    assertThat(audit.hashChain()).matches("[0-9a-f]{64}");
  }

  @Test
  void delete_existingSession_isInvalidated() throws Exception {
    String email = uniqueEmail("session");
    String password = "P@ssw0rd-sess";
    seedUser("sub-" + email, email, "失効 確認", "シッコウ カクニン", null, "ACTIVE");

    try (Keycloak admin = adminClient()) {
      UserRepresentation user = singleByEmail(admin, email);
      setPassword(admin, user.getId(), password);

      try (Keycloak uc = userClient(email, password)) {
        assertThat(uc.tokenManager().getAccessToken().getToken()).isNotBlank(); // active session

        admin.realm(REALM).users().get(user.getId()).remove();

        // removeUser=true により session/credential が cleanup され、refresh が拒否される。
        assertThatThrownBy(() -> uc.tokenManager().refreshToken()).isInstanceOf(Exception.class);
      }
    }
  }

  @Test
  void delete_concurrentUsers_keepUniqueConstraints() throws Exception {
    String a = uniqueEmail("uniqA");
    String b = uniqueEmail("uniqB");
    long idA = seedUser("sub-" + a, a, "一意 A", "イチイ エー", null, "ACTIVE");
    long idB = seedUser("sub-" + b, b, "一意 B", "イチイ ビー", null, "ACTIVE");

    try (Keycloak admin = adminClient()) {
      admin.realm(REALM).users().get(singleByEmail(admin, a).getId()).remove();
      admin.realm(REALM).users().get(singleByEmail(admin, b).getId()).remove();
    }

    // placeholder に id を埋め込むため UNIQUE(email / oidc_sub)は衝突しない。
    assertThat(fetchById(idA).email()).isEqualTo("__deleted__" + idA + "@deleted.invalid");
    assertThat(fetchById(idB).email()).isEqualTo("__deleted__" + idB + "@deleted.invalid");
    assertThat(fetchById(idA).oidcSub()).isNotEqualTo(fetchById(idB).oidcSub());
  }

  // --- 楽観排他(ADR-0006 §3.4): UserRepository 直接 ---

  @Test
  void optimisticLock_staleVersionUpdate_throwsModelException() throws Exception {
    String email = uniqueEmail("optlock");
    long id = seedUser("sub-" + email, email, "排他 制御", "ハイタ セイギョ", null, "ACTIVE");

    try (UserRepository repo = new UserRepository(MYSQL.getJdbcUrl(), DB_USER, DB_PASS)) {
      // 1 回目: 正しい version 0 → 成功し version 1 を返す。
      long next = repo.updateEmail(id, uniqueEmail("optlock-1"), 0);
      assertThat(next).isEqualTo(1L);

      // 2 回目: 古い version 0 で更新(= 同時更新の負け側)→ 競合検知。
      assertThatThrownBy(() -> repo.updateEmail(id, uniqueEmail("optlock-2"), 0))
          .isInstanceOf(ModelException.class)
          .hasMessageContaining("optimistic lock conflict");
    }
  }

  // --- helpers ---

  private static UserRepresentation singleByEmail(Keycloak admin, String email) {
    List<UserRepresentation> hits = admin.realm(REALM).users().search(email, 0, 2);
    assertThat(hits).as("exactly one user for %s", email).hasSize(1);
    return hits.get(0);
  }

  private static String attr(UserRepresentation user, String name) {
    Map<String, List<String>> attrs = user.getAttributes();
    if (attrs == null || !attrs.containsKey(name)) {
      return null;
    }
    List<String> values = attrs.get(name);
    return values.isEmpty() ? null : values.get(0);
  }

  private static void setPassword(Keycloak admin, String userId, String password) {
    CredentialRepresentation cred = new CredentialRepresentation();
    cred.setType(CredentialRepresentation.PASSWORD);
    cred.setValue(password);
    cred.setTemporary(false);
    admin.realm(REALM).users().get(userId).resetPassword(cred);
  }
}
