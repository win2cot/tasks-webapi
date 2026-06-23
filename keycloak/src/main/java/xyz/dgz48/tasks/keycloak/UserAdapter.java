package xyz.dgz48.tasks.keycloak;

import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.keycloak.component.ComponentModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.SubjectCredentialManager;
import org.keycloak.models.UserModel;
import org.keycloak.storage.StorageId;
import org.keycloak.storage.adapter.AbstractInMemoryUserAdapter;

/**
 * {@code users} テーブルの 1 行を Keycloak の {@link org.keycloak.models.UserModel} として適合させるアダプタ。
 *
 * <p>profile 属性(full_name、department_name など)は read-only で、SoT は tasks-webapi。writable な profile
 * 属性は email のみで、Keycloak Update-Email フローにより {@code users.email} へ書き戻す(ADR-0006 §3.1)。{@code
 * full_name_kana} と {@code department_name} は、admin / リカバリの Console 作成における Custom User Profile
 * 経路向けに {@link #setSingleAttribute} 経由で追加的に writable とする(§3.1)。氏名(first/last name)は read-only の
 * no-op のまま。すべての書き戻しは {@code version} 列で楽観排他する(§3.4)。
 */
public final class UserAdapter extends AbstractInMemoryUserAdapter {

  /** {@code users} 列を裏付けとする Custom User Profile attribute キー(ADR-0006 §3.1)。 */
  static final String FULL_NAME = "full_name";

  static final String FULL_NAME_KANA = "full_name_kana";
  static final String DEPARTMENT_NAME = "department_name";

  /** 有効アカウントを表す {@code users.status} 値(ADR-0006 §3.3 有効/無効)。 */
  private static final String STATUS_ACTIVE = "ACTIVE";

  private final UserRepository repository;
  private final long userId;

  /** 永続化済みの {@code users.version} を追跡する。書き戻し成功ごとに進める。 */
  private long version;

  /** コンストラクタが in-memory 状態を hydrate している間だけ true。この間は書き戻しを抑止する。 */
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
    // users.status を Keycloak の有効状態にマッピングし、INACTIVE アカウントが認証できないようにする。
    setEnabled(STATUS_ACTIVE.equals(row.status()));
    setEmailVerified(true);
    super.setEmail(row.email());
    setSingleAttribute(FIRST_NAME, row.fullName());
    setSingleAttribute(FULL_NAME, row.fullName());
    setSingleAttribute(FULL_NAME_KANA, row.fullNameKana());
    if (row.departmentName() != null) {
      setSingleAttribute(DEPARTMENT_NAME, row.departmentName());
    }
    // realm の default role(`default-roles-<realm>` 合成ロール)を付与する。これは Account Console / token の
    // `account` audience など基本機能に必要な技術ロールであり、ADR-0006 §3.7 が SPI から返さないとする「業務ロール
    // (APP_ADMIN / TENANT_ADMIN / MEMBER)」とは別物。これを付与しないと federated user は自身の Account
    // Console すら開けない(account REST が 401)。
    grantRole(realm.getDefaultRole());
    this.hydrating = false;
  }

  /**
   * 資格情報は Keycloak の native(federated)credential store が所有する(ADR-0006 §3.3)。
   *
   * <p>この federated user について Keycloak 自身の credential manager に委譲し、password / MFA を SPI ではなく
   * Keycloak が保存・検証するようにする({@code CredentialInputValidator} は実装しない)。
   */
  @Override
  public SubjectCredentialManager credentialManager() {
    return session.users().getUserCredentialManager(this);
  }

  /**
   * 属性更新の write 戻し。Keycloak の Custom User Profile / Account / Admin Console は属性の適用を {@link
   * #setAttribute(String, java.util.List)} 経由で行うため、こちらを SoT とする({@link #setSingleAttribute} も同じ
   * 経路へ委譲)。writable は email(§3.1 Update-Email)/ {@code full_name_kana} / {@code
   * department_name}(§3.1 Console 作成)のみ。値が現在と変わらない場合は書き込まない(UserProfile は未変更属性も再適用するため、無駄な version
   * increment と楽観排他衝突を避ける)。
   */
  @Override
  public void setAttribute(String name, List<String> values) {
    writeBackIfManaged(name, (values == null || values.isEmpty()) ? null : values.get(0));
    super.setAttribute(name, values);
  }

  @Override
  public void setSingleAttribute(String name, String value) {
    writeBackIfManaged(name, value);
    super.setSingleAttribute(name, value);
  }

  // email / full_name_kana / department_name のみ users 列へ書き戻す。変更があった場合だけ updateXxx を呼び、
  // 返り値で version を進める(同一更新内で複数属性が順に適用されても version を引き継ぐ)。
  private void writeBackIfManaged(String name, @Nullable String value) {
    if (hydrating) {
      return;
    }
    if (UserModel.EMAIL.equals(name)) {
      // email は NOT NULL。null クリアは write 戻ししない(in-memory のみ)。
      if (value != null && !Objects.equals(value, getEmail())) {
        this.version = repository.updateEmail(userId, value, version);
      }
    } else if (FULL_NAME_KANA.equals(name)) {
      String next = value == null ? "" : value; // full_name_kana は NOT NULL(空文字許容)
      if (!Objects.equals(next, getFirstAttribute(FULL_NAME_KANA))) {
        this.version = repository.updateFullNameKana(userId, next, version);
      }
    } else if (DEPARTMENT_NAME.equals(name)) {
      if (!Objects.equals(value, getFirstAttribute(DEPARTMENT_NAME))) {
        this.version = repository.updateDepartmentName(userId, value, version);
      }
    }
  }

  /** 無視する — full_name は read-only。SoT は tasks-webapi の profile 更新 API(§3.1)。 */
  @Override
  public void setFirstName(String firstName) {}

  /** 無視する — users テーブルには保持しない。 */
  @Override
  public void setLastName(String lastName) {}
}
