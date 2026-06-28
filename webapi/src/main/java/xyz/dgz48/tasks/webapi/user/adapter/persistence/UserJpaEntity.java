package xyz.dgz48.tasks.webapi.user.adapter.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import xyz.dgz48.tasks.webapi.user.domain.UserStatus;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SuppressWarnings("NullAway.Init") // JPA requires no-args constructor; fields initialized via JPA
public class UserJpaEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "oidc_sub", nullable = false, unique = true, length = 255)
  private String oidcSub;

  @Column(nullable = false, length = 255)
  private String email;

  @Column(name = "full_name", nullable = false, length = 255)
  private String fullName;

  @Column(name = "full_name_kana", nullable = false, length = 255)
  private String fullNameKana;

  @Nullable
  @Column(name = "department_name", length = 255)
  private String departmentName;

  @Version
  @Column(nullable = false)
  private Long version;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, columnDefinition = "ENUM('ACTIVE','INACTIVE')")
  private UserStatus status;

  @Nullable
  @Column(name = "deleted_at")
  private LocalDateTime deletedAt;

  /** 初回ログイン correlation 待ちの oidc_sub placeholder の接頭辞(SPI insert と共通、ADR-0006 §3.2)。 */
  private static final String PENDING_OIDC_SUB_PREFIX = "pending:";

  public boolean isAnonymized() {
    return this.deletedAt != null;
  }

  public boolean isInactive() {
    return this.status == UserStatus.INACTIVE;
  }

  /**
   * oidc_sub が初回ログイン correlation 待ちの placeholder({@code pending:<email>})かを返す。会員登録(ADR-0040)や SPI の
   * admin/recovery insert で作られた行は、初回ログインで本物の Keycloak {@code sub} に突合されるまでこの状態になる。
   */
  public boolean isPendingCorrelation() {
    return this.oidcSub.startsWith(PENDING_OIDC_SUB_PREFIX);
  }

  /**
   * 初回ログイン時の oidc_sub correlation(ADR-0006 §3.2 / ADR-0040 §3.2):pending placeholder を Keycloak
   * の本物の {@code sub} に書き戻す。pending 状態の行に対してのみ呼ぶこと。
   *
   * @throws IllegalStateException 既に correlation 済み(pending でない)の行に対して呼ばれた場合
   */
  public void correlateOidcSub(String realOidcSub) {
    if (!isPendingCorrelation()) {
      throw new IllegalStateException("oidc_sub correlation は pending 行に対してのみ許可される");
    }
    this.oidcSub = realOidcSub;
  }

  /** 会員登録(ADR-0040 §3.3)時の profile 更新。SPI insert 等で先に作られた pending 行に対し、登録画面で入力された profile を書き込む。 */
  public void updateProfile(String fullName, String fullNameKana, @Nullable String departmentName) {
    this.fullName = fullName;
    this.fullNameKana = fullNameKana;
    this.departmentName = departmentName;
  }

  public UserJpaEntity(
      String oidcSub,
      String email,
      String fullName,
      String fullNameKana,
      @Nullable String departmentName) {
    this.oidcSub = oidcSub;
    this.email = email;
    this.fullName = fullName;
    this.fullNameKana = fullNameKana;
    this.departmentName = departmentName;
    this.status = UserStatus.ACTIVE;
  }
}
